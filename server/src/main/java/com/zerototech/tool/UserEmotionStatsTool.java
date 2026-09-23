package com.zerototech.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.zerototech.entity.AnalysisRecord;
import com.zerototech.mapper.AnalysisRecordMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 用户历史情绪画像与文学足迹分析工具 (Tool Calling)
 * 供 Spring AI 大模型在多轮追问中自主调用
 */
@Component
public class UserEmotionStatsTool {

    private final AnalysisRecordMapper recordMapper;

    public UserEmotionStatsTool(AnalysisRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    /**
     * 大模型调用该工具的入参规范
     */
    public record Request(
        @JsonPropertyDescription("查询并分析的历史记录样本数量，通常为 5-20，默认为 10")
        Integer limit
    ) {}

    /**
     * 工具返回给大模型的严谨统计出参
     */
    public record Response(
        int totalSamples,                        // 统计的历史样本条数
        double averageScore,                     // 平均情绪分值 (0.0~1.0)
        String dominantSentiment,                // 占主导地位的情绪标签
        Map<String, Integer> sentimentCounts,    // 各情绪标签出现频次统计
        List<SentenceSnapshot> recentSnapshots,  // 最近代表性诗句快照
        String statusNote                        // 样本状态说明
    ) {
        //内部类
        public record SentenceSnapshot(String text, double score, String sentiment) {}
    }

    /**
     * 业务执行逻辑：安全结合 ToolContext 获取当前 userId 并从 MySQL 统计聚合
     */
    public Response execute(Request request, ToolContext toolContext) {
        String userId = null;
        if (toolContext != null && toolContext.getContext() != null) {
            Object idObj = toolContext.getContext().get("userId");
            if (idObj != null) {
                userId = idObj.toString();
            }
        }

        if (userId == null || userId.isBlank()) {
            return new Response(0, 0.5, "未登录用户", Map.of(), List.of(), "未能识别当前用户凭证");
        }
        //限制从数据库中查询的数量
        int queryLimit = (request != null && request.limit() != null && request.limit() > 0)
            ? Math.min(request.limit(), 20)
            : 10;

        List<AnalysisRecord> records = recordMapper.selectList(
            new LambdaQueryWrapper<AnalysisRecord>()
                .eq(AnalysisRecord::getUserId, userId)
                .orderByDesc(AnalysisRecord::getCreatedAt)
                .last("LIMIT " + queryLimit)
        );

        if (records.isEmpty()) {
            return new Response(0, 0.5, "无历史记录", Map.of(), List.of(), "该用户目前尚未保存过任何历史分析文本");
        }
        // 情绪分组，判断每个情绪的数量
        double sum = 0.0;
        Map<String, Integer> counts = new HashMap<>();
        List<Response.SentenceSnapshot> snapshots = new ArrayList<>();

        for (AnalysisRecord r : records) {
            //情绪总分
            double s = (r.getScore() != null) ? r.getScore() : 0.5;
            sum += s;
            // 情绪分组，判断每个情绪的数量
            String sent = (r.getSentiment() != null && !r.getSentiment().isBlank()) ? r.getSentiment() : "中性平和";
            counts.put(sent, counts.getOrDefault(sent, 0) + 1);

            if (snapshots.size() < 4) {
                snapshots.add(new Response.SentenceSnapshot(r.getText(), s, sent));
            }
        }
        //这两行代码分别完成了计算保留两位小数的平均分以及找出出现频次最多的主导情绪标签。
        double avg = Math.round((sum / records.size()) * 100.0) / 100.0;
        String dominant = counts.entrySet().stream()
                //根据value比较找到最大value的entry
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("中性平和");

        return new Response(
            records.size(),
            avg,
            dominant,
            counts,
            snapshots,
            "成功调取并汇总了最近 " + records.size() + " 条真实分析记录"
        );
    }

    /**
     * 封装为 Spring AI 标准 FunctionCallback
     */
    public FunctionCallback getFunctionCallback() {
        return FunctionCallbackWrapper.builder(this::execute)
            .withName("analyzeUserHistoryEmotion")
            .withDescription("""
                查询并精确统计当前用户在数据库中保存的历史分析文本、过往情绪得分走势与偏好标签。
                当用户在追问中询问其本人的历史记录、过往心境变化、输入风格总结或请求情绪走势分析时，必须调用此工具获取真实数据。
                """)
            .withInputType(Request.class)
            .build();
    }
}

package com.zerototech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zerototech.entity.AnalysisRecord;
import com.zerototech.mapper.AnalysisRecordMapper;
import com.zerototech.model.AnalyzeRequest;
import com.zerototech.model.AnalyzeResult;
import com.zerototech.util.UserSessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(
    origins = {"http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:8080", "http://127.0.0.1:8080"},
    allowCredentials = "true"
)
public class TextLabController {

    private final ChatClient chatClient;
    private final AnalysisRecordMapper recordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TextLabController(ChatClient.Builder chatClientBuilder, AnalysisRecordMapper recordMapper) {
        this.chatClient = chatClientBuilder.build();
        this.recordMapper = recordMapper;
    }

    /**
     * 流式文本与情感分析接口 (SSE: text/event-stream)
     * 1. 结构化元数据即时下发 (meta)
     * 2. 大模型深度语境与心理剖析逐字推流 (chunk)
     * 3. 流结束自动写入现有 MySQL 表并返回完成帧 (done)
     */
    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyzeStream(
        @RequestBody AnalyzeRequest requestBody,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String inputText = requestBody.getText();
        if (inputText == null || inputText.isBlank()) {
            // 流式发送：just
            return Flux.just("{\"type\":\"error\",\"message\":\"输入内容不能为空\"}");
        }

        // 1. 获取或下发当前用户的专属 UUID 标识 (写进响应 Cookie)
        String userId = UserSessionUtils.getOrCreateUserId(request, response);

        try {
            // 2. 快速结构化分析（原文、拼音、分数、情绪标签）
            AnalyzeResult result = chatClient.prompt()
                .user(u -> u.text("""
                    你是一个专业的中文语言与情感分析专家。
                    请深入分析用户输入的这句中文："{input}"

                    请按照以下规范返回：
                    1. text: 完整的输入原文。
                    2. pinyin: 准确的带声调拼音（例如：jīn tiān de fēng hěn qīng）。
                    3. score: 情绪得分，介于 0.0 到 1.0 之间的两位浮点数（0 代表极端消极，1 代表非常积极，0.5 代表中性平和）。
                    4. sentiment: 简明的情绪总结标签（例如：偏积极、偏消极、中性平和）。
                    """)
                    .param("input", inputText))
                .call()
                .entity(AnalyzeResult.class);

            if (result == null) {
                result = new AnalyzeResult(inputText, "", 0.5, "中性平和");
            }

            final AnalyzeResult finalResult = result;

            // 首帧：元数据下发
            String metaJson = objectMapper.writeValueAsString(Map.of(
                "type", "meta",
                "text", finalResult.text() != null ? finalResult.text() : inputText,
                "pinyin", finalResult.pinyin() != null ? finalResult.pinyin() : "",
                "score", finalResult.score() != null ? finalResult.score() : 0.5,
                "sentiment", finalResult.sentiment() != null ? finalResult.sentiment() : "中性平和"
            ));
            //先流式输出结果：
            Flux<String> metaFlux = Flux.just(metaJson);

            // 连续流式帧：AI 对语境、文采与情绪意境的深度赏析逐字流出
            Flux<String> chunkFlux = chatClient.prompt()
                .user(u -> u.text("""
                    你是一个富有文学底蕴与心理洞察力的中文语言分析家。
                    请对以下这句中文展开生动、精辟的语言与心理情感深度解读（100-150字左右），语言优美细腻、富有共鸣感：
                    "{input}"
                    请直接输出解读正文，不要输出任何多余的开场白或客套话。
                    """)
                    .param("input", inputText))
                .stream()
                .content()
                .filter(s -> s != null && !s.isEmpty())
                .map(chunk -> {
                    try {
                        return objectMapper.writeValueAsString(Map.of(
                            "type", "chunk",
                            "content", chunk
                        ));
                    } catch (Exception e) {
                        return "{\"type\":\"chunk\",\"content\":\"\"}";
                    }
                });

            // 尾帧：数据持久化（不改动数据库结构，只持久化原有的 4 个指标）并通知完成
            Mono<String> doneMono = Mono.fromCallable(() -> {
                Long recordId = null;
                try {
                    AnalysisRecord record = new AnalysisRecord(
                        userId,
                        finalResult.text(),
                        finalResult.pinyin(),
                        finalResult.score(),
                        finalResult.sentiment()
                    );
                    recordMapper.insert(record);
                    recordId = record.getId();
                } catch (Exception e) {
                    System.err.println("保存分析历史失败: " + e.getMessage());
                }
                return objectMapper.writeValueAsString(Map.of(
                    "type", "done",
                    "id", recordId != null ? recordId : 0
                ));
            });

            return Flux.concat(metaFlux, chunkFlux, doneMono)
                .onErrorResume(err -> {
                    try {
                        return Flux.just(objectMapper.writeValueAsString(Map.of(
                            "type", "error",
                            "message", err.getMessage() != null ? err.getMessage() : "流式处理出现异常"
                        )));
                    } catch (Exception e) {
                        return Flux.just("{\"type\":\"error\",\"message\":\"未知流式异常\"}");
                    }
                });

        } catch (Exception e) {
            try {
                return Flux.just(objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "message", "AI 分析初始化失败: " + e.getMessage()
                )));
            } catch (Exception ex) {
                return Flux.just("{\"type\":\"error\",\"message\":\"分析初始化失败\"}");
            }
        }
    }

    @PostMapping("/analyze")
    public AnalyzeResult analyze(
        @RequestBody AnalyzeRequest requestBody,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String inputText = requestBody.getText();
        if (inputText == null || inputText.isBlank()) {
            return new AnalyzeResult("", "", 0.5, "未输入文本");
        }

        // 1. 获取或下发当前用户的专属 UUID 标识
        String userId = UserSessionUtils.getOrCreateUserId(request, response);

        // 2. Spring AI 调用大模型完成结构化分析
        AnalyzeResult result = chatClient.prompt()
            .user(u -> u.text("""
                你是一个专业的中文语言与情感分析专家。
                请深入分析用户输入的这句中文："{input}"

                请按照以下规范返回：
                1. text: 完整的输入原文。
                2. pinyin: 准确的带声调拼音（例如：jīn tiān de fēng hěn qīng）。
                3. score: 情绪得分，介于 0.0 到 1.0 之间的两位浮点数（0 代表极端消极，1 代表非常积极，0.5 代表中性平和）。
                4. sentiment: 简明的情绪总结标签（例如：偏积极、偏消极、中性平和）。
                """)
                .param("input", inputText))
            .call()
            .entity(AnalyzeResult.class);

        // 3. 将分析结果绑定当前 userId 持久化到 MySQL 数据库中
        if (result != null) {
            try {
                recordMapper.insert(new AnalysisRecord(
                    userId,
                    result.text(),
                    result.pinyin(),
                    result.score(),
                    result.sentiment()
                ));
            } catch (Exception e) {
                System.err.println("MyBatis-Plus 保存分析历史失败: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * 仅查询属于当前用户的最近 10 条历史记录 (用户隔离)
     */
    @GetMapping("/history")
    public List<AnalysisRecord> getHistory(HttpServletRequest request, HttpServletResponse response) {
        String userId = UserSessionUtils.getOrCreateUserId(request, response);
        return recordMapper.selectList(
            new LambdaQueryWrapper<AnalysisRecord>()
                .eq(AnalysisRecord::getUserId, userId)
                .orderByDesc(AnalysisRecord::getCreatedAt)
                .last("LIMIT 10")
        );
    }

    /**
     * 仅删除属于当前用户的指定记录 (防越权安全控制)
     */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Map<String, Object>> deleteHistory(
        @PathVariable Long id,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String userId = UserSessionUtils.getOrCreateUserId(request, response);
        int rows = recordMapper.delete(
            new LambdaQueryWrapper<AnalysisRecord>()
                .eq(AnalysisRecord::getId, id)
                .eq(AnalysisRecord::getUserId, userId)
        );
        if (rows > 0) {
            return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "记录不存在或无权删除"));
        }
    }
}

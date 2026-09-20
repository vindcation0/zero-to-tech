package com.zerototech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zerototech.entity.AnalysisRecord;
import com.zerototech.mapper.AnalysisRecordMapper;
import com.zerototech.model.AnalyzeRequest;
import com.zerototech.model.AnalyzeResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class TextLabController {

    private final ChatClient chatClient;
    private final AnalysisRecordMapper recordMapper;

    public TextLabController(ChatClient.Builder chatClientBuilder, AnalysisRecordMapper recordMapper) {
        this.chatClient = chatClientBuilder.build();
        this.recordMapper = recordMapper;
    }

    @PostMapping("/analyze")
    public AnalyzeResult analyze(@RequestBody AnalyzeRequest request) {
        String inputText = request.getText();
        if (inputText == null || inputText.isBlank()) {
            return new AnalyzeResult("", "", 0.5, "未输入文本");
        }

        // 1. Spring AI 调用大模型完成结构化分析
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

        // 2. 使用 MyBatis-Plus 将分析结果持久化到 MySQL 数据库中
        if (result != null) {
            try {
                recordMapper.insert(new AnalysisRecord(
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
     * 使用 MyBatis-Plus 的 LambdaQueryWrapper 查询最近 10 条历史记录
     */
    @GetMapping("/history")
    public List<AnalysisRecord> getHistory() {
        return recordMapper.selectList(
            new LambdaQueryWrapper<AnalysisRecord>()
                .orderByDesc(AnalysisRecord::getCreatedAt)
                .last("LIMIT 10")
        );
    }

    /**
     * 使用 MyBatis-Plus 根据 ID 删除记录
     */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Map<String, Object>> deleteHistory(@PathVariable Long id) {
        int rows = recordMapper.deleteById(id);
        if (rows > 0) {
            return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "记录不存在"));
        }
    }
}

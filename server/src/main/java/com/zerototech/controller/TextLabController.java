package com.zerototech.controller;

import com.zerototech.model.AnalyzeRequest;
import com.zerototech.model.AnalyzeResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class TextLabController {

    private final ChatClient chatClient;

    // 推荐做法：通过 Spring AI 自动注入的 ChatClient.Builder 来构建 ChatClient
    public TextLabController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/analyze")
    public AnalyzeResult analyze(@RequestBody AnalyzeRequest request) {
        String inputText = request.getText();
        if (inputText == null || inputText.isBlank()) {
            return new AnalyzeResult("", "", 0.5, "未输入文本");
        }

        // Spring AI 核心魅力：链式提示词 + 大模型调用 + 自动映射为 Java POJO
        return chatClient.prompt()
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
    }
}

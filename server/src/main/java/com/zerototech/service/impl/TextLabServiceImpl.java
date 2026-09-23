package com.zerototech.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerototech.entity.AnalysisRecord;
import com.zerototech.mapper.AnalysisRecordMapper;
import com.zerototech.model.AnalyzeResult;
import com.zerototech.service.TextLabService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zerototech.tool.UserEmotionStatsTool;

/**
 * 语言与文本分析业务实现类 (Service 业务逻辑层)
 */
@Service
public class TextLabServiceImpl implements TextLabService {

    private final ChatClient chatClient;
    private final AnalysisRecordMapper recordMapper;
    private final ChatMemory chatMemory;
    private final UserEmotionStatsTool userEmotionStatsTool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TextLabServiceImpl(
        ChatClient.Builder chatClientBuilder,
        AnalysisRecordMapper recordMapper,
        ChatMemory chatMemory,
        UserEmotionStatsTool userEmotionStatsTool
    ) {
        this.chatMemory = chatMemory;
        this.userEmotionStatsTool = userEmotionStatsTool;
        // 装配多轮会话记忆拦截器 (MessageChatMemoryAdvisor)
        this.chatClient = chatClientBuilder
            .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
            .build();
        this.recordMapper = recordMapper;
    }

    @Override
    public Flux<String> analyzeStream(String inputText, String userId) {
        if (inputText == null || inputText.isBlank()) {
            return Flux.just("{\"type\":\"error\",\"message\":\"输入内容不能为空\"}");
        }

        String promptText = """
            你是一个专业的中文语言与情感分析专家。
            请深入分析用户输入的这句中文："{input}"

            请严格按照以下规范格式输出，每项各占一行，保留前缀标记，不要输出任何额外的开场白、问候语或markdown标记：
            [PINYIN] 完整准确的带声调拼音
            [SCORE] 情绪分值（0.0到1.0之间的浮点数）
            [SENTIMENT] 简短情绪标签（例如：偏积极、偏消极、中性平和）
            [COMMENTARY] 此处开始直接输出深度文学语境、词藻特色与心理意境的精辟赏析（100-150字左右），语言优美细腻富有洞察力。
            """.replace("{input}", inputText.trim());

        // 构建流式管道
        Flux<String> rawStream = chatClient.prompt()
            .user(promptText)
            .stream()
            .content()
            .filter(s -> s != null && !s.isEmpty());

        return Flux.create(sink -> {
            StringBuilder headerBuffer = new StringBuilder();
            StringBuilder commentaryBuffer = new StringBuilder();
            AtomicBoolean headerParsed = new AtomicBoolean(false);
            AtomicReference<String> pinyinRef = new AtomicReference<>("");
            AtomicReference<Double> scoreRef = new AtomicReference<>(0.5);
            AtomicReference<String> sentimentRef = new AtomicReference<>("中性平和");

            rawStream.subscribe(
                chunk -> {
                    if (!headerParsed.get()) {
                        headerBuffer.append(chunk);
                        String current = headerBuffer.toString();
                        int idx = current.indexOf("[COMMENTARY]");

                        if (idx != -1) {
                            headerParsed.set(true);
                            String headerPart = current.substring(0, idx);
                            parseHeaders(headerPart, pinyinRef, scoreRef, sentimentRef);

                            // 1. 立即下发 meta 帧
                            try {
                                String metaJson = objectMapper.writeValueAsString(Map.of(
                                    "type", "meta",
                                    "text", inputText,
                                    "pinyin", pinyinRef.get(),
                                    "score", scoreRef.get(),
                                    "sentiment", sentimentRef.get()
                                ));
                                sink.next(metaJson);
                            } catch (Exception e) {
                                sink.next("{\"type\":\"meta\",\"text\":\"" + inputText + "\",\"pinyin\":\"\",\"score\":0.5,\"sentiment\":\"中性平和\"}");
                            }

                            // 2. 将 [COMMENTARY] 后的多余文字作为第一帧 chunk 下发
                            String remainder = current.substring(idx + "[COMMENTARY]".length()).trim();
                            if (!remainder.isEmpty()) {
                                commentaryBuffer.append(remainder);
                                try {
                                    sink.next(objectMapper.writeValueAsString(Map.of("type", "chunk", "content", remainder)));
                                } catch (Exception ignored) {}
                            }

                        } else if (current.length() > 250) {
                            // 防御性超时：若 250 字符内未见标记，强行解析放行
                            headerParsed.set(true);
                            parseHeaders(current, pinyinRef, scoreRef, sentimentRef);
                            try {
                                sink.next(objectMapper.writeValueAsString(Map.of(
                                    "type", "meta",
                                    "text", inputText,
                                    "pinyin", pinyinRef.get(),
                                    "score", scoreRef.get(),
                                    "sentiment", sentimentRef.get()
                                )));
                            } catch (Exception ignored) {}
                        }
                    } else {
                        // header 已解析，所有后续 Token 实时直推给前端打字机
                        commentaryBuffer.append(chunk);
                        try {
                            sink.next(objectMapper.writeValueAsString(Map.of(
                                "type", "chunk",
                                "content", chunk
                            )));
                        } catch (Exception e) {
                            sink.next("{\"type\":\"chunk\",\"content\":\"\"}");
                        }
                    }
                },
                error -> {
                    try {
                        sink.next(objectMapper.writeValueAsString(Map.of(
                            "type", "error",
                            "message", error.getMessage() != null ? error.getMessage() : "流式处理异常"
                        )));
                    } catch (Exception ignored) {}
                    sink.complete();
                },
                () -> {
                    // 流正常结束：兜底检查 meta 是否已下发
                    if (!headerParsed.get()) {
                        parseHeaders(headerBuffer.toString(), pinyinRef, scoreRef, sentimentRef);
                        try {
                            sink.next(objectMapper.writeValueAsString(Map.of(
                                "type", "meta",
                                "text", inputText,
                                "pinyin", pinyinRef.get(),
                                "score", scoreRef.get(),
                                "sentiment", sentimentRef.get()
                            )));
                        } catch (Exception ignored) {}
                    }

                    // 持久化到 MySQL（零字段新增，仅存原有 4 项核心数据）
                    Long recordId = null;
                    try {
                        AnalysisRecord record = new AnalysisRecord(
                            userId,
                            inputText,
                            pinyinRef.get(),
                            scoreRef.get(),
                            sentimentRef.get()
                        );
                        recordMapper.insert(record);
                        recordId = record.getId();
                    } catch (Exception e) {
                        System.err.println("保存分析记录失败: " + e.getMessage());
                    }

                    // 沉淀本轮分析结果到 ChatMemory 作为后续多轮追问的上下文锚点
                    try {
                        chatMemory.clear(userId);
                        chatMemory.add(userId, List.of(
                            new UserMessage("请深入分析这句中文：“" + inputText + "”"),
                            new AssistantMessage(
                                "我已经对文本《" + inputText + "》完成了深度分析。\n" +
                                "【拼音】" + pinyinRef.get() + "\n" +
                                "【情感】" + sentimentRef.get() + "（得分 " + scoreRef.get() + "）\n" +
                                "【赏析解读】" + commentaryBuffer.toString() + "\n" +
                                "你可以就这句文字的背景故事、意境手法、现实启示或现代改写随时向我追问！"
                            )
                        ));
                    } catch (Exception e) {
                        System.err.println("沉淀初始会话记忆失败: " + e.getMessage());
                    }

                    // 下发完成帧 done
                    try {
                        sink.next(objectMapper.writeValueAsString(Map.of(
                            "type", "done",
                            "id", recordId != null ? recordId : 0
                        )));
                    } catch (Exception ignored) {}
                    sink.complete();
                }
            );
        });
    }

    @Override
    public Flux<String> chatStream(String message, String userId) {
        if (message == null || message.isBlank()) {
            return Flux.just("{\"type\":\"error\",\"message\":\"追问内容不能为空\"}");
        }

        return chatClient.prompt()
            .system("""
                你是一个博学、敏锐且善于启发的中文文学与情感导师。
                请结合当前对话上下文深入回答用户的追问。
                当用户询问其历史记录、情绪走势、心境变化或往期输入风格时，请自动调用 analyzeUserHistoryEmotion 工具获取其在数据库中的真实统计数据，并结合数据给出充满人文关怀与洞察力的深度解读。
                回答时请充分运用优雅的 Markdown 格式：
                - 适度加粗核心论点或关键词（如 **诗眼**、**意境**）
                - 诗句或原文引用使用引用块（> 引用内容）
                - 分条剖析使用列表项（- 或 1. 2.）
                - 段落清晰，言辞富有文采与启发性。
                """)
            .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, userId))
            .toolContext(Map.of("userId", userId))
            .functions(userEmotionStatsTool.getFunctionCallback())
            .user(message.trim())
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
            })
                //标记最终完成
            .concatWith(Mono.just("{\"type\":\"done\"}"))
            .onErrorResume(err -> {
                try {
                    return Flux.just(objectMapper.writeValueAsString(Map.of(
                        "type", "error",
                        "message", err.getMessage() != null ? err.getMessage() : "追问处理异常"
                    )));
                } catch (Exception e) {
                    return Flux.just("{\"type\":\"error\",\"message\":\"未知异常\"}");
                }
            });
    }

    @Override
    public void clearChatMemory(String userId) {
        if (userId != null && !userId.isBlank()) {
            chatMemory.clear(userId);
        }
    }

    @Override
    public List<AnalysisRecord> getHistory(String userId) {
        return recordMapper.selectList(
            new LambdaQueryWrapper<AnalysisRecord>()
                .eq(AnalysisRecord::getUserId, userId)
                .orderByDesc(AnalysisRecord::getCreatedAt)
                .last("LIMIT 10")
        );
    }

    @Override
    public boolean deleteHistory(Long id, String userId) {
        int rows = recordMapper.delete(
            new LambdaQueryWrapper<AnalysisRecord>()
                .eq(AnalysisRecord::getId, id)
                .eq(AnalysisRecord::getUserId, userId)
        );
        return rows > 0;
    }

    @Override
    public AnalyzeResult analyze(String inputText, String userId) {
        if (inputText == null || inputText.isBlank()) {
            return new AnalyzeResult("", "", 0.5, "未输入文本");
        }

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
     * 正则解析前置元数据行 [PINYIN]、[SCORE]、[SENTIMENT]
     */
    private void parseHeaders(
        String headerPart,
        AtomicReference<String> pinyinRef,
        AtomicReference<Double> scoreRef,
        AtomicReference<String> sentimentRef
    ) {
        if (headerPart == null || headerPart.isBlank()) return;

        // 1. PINYIN
        Matcher pinyinMatcher = Pattern
            .compile("\\[?PINYIN\\]?:?\\s*([^\\r\\n\\[]+)", Pattern.CASE_INSENSITIVE)
            .matcher(headerPart);
        if (pinyinMatcher.find()) {
            String pinyin = pinyinMatcher.group(1).trim();
            if (!pinyin.isBlank()) pinyinRef.set(pinyin);
        }

        // 2. SCORE
        Matcher scoreMatcher = Pattern
            .compile("\\[?SCORE\\]?:?\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE)
            .matcher(headerPart);
        if (scoreMatcher.find()) {
            try {
                double score = Double.parseDouble(scoreMatcher.group(1).trim());
                scoreRef.set(score);
            } catch (Exception ignored) {}
        }

        // 3. SENTIMENT
        Matcher sentimentMatcher = Pattern
            .compile("\\[?SENTIMENT\\]?:?\\s*([^\\r\\n\\[]+)", Pattern.CASE_INSENSITIVE)
            .matcher(headerPart);
        if (sentimentMatcher.find()) {
            String sentiment = sentimentMatcher.group(1).trim();
            if (!sentiment.isBlank()) sentimentRef.set(sentiment);
        }
    }
}

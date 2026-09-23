package com.zerototech.service;

import com.zerototech.entity.AnalysisRecord;
import com.zerototech.model.AnalyzeResult;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 语言与文本分析服务接口 (Service 层)
 */
public interface TextLabService {

    /**
     * 单流零阻塞式文本与情绪分析 (SSE)
     *
     * @param inputText 输入中文文本
     * @param userId    当前用户 UUID
     * @return 包含 meta、chunk、done 或 error 的流式 JSON 序列
     */
    Flux<String> analyzeStream(String inputText, String userId);

    /**
     * 多轮语境深度追问流式交互 (SSE)
     *
     * @param message 追问内容
     * @param userId  当前用户 UUID
     * @return 包含 chunk、done 或 error 的流式 JSON 序列
     */
    Flux<String> chatStream(String message, String userId);

    /**
     * 清空当前用户的多轮会话记忆
     *
     * @param userId 当前用户 UUID
     */
    void clearChatMemory(String userId);

    /**
     * 查询属于当前用户的最近历史分析记录 (分页前 10 条)
     *
     * @param userId 当前用户 UUID
     * @return 记录列表
     */
    List<AnalysisRecord> getHistory(String userId);

    /**
     * 删除属于当前用户的指定分析历史记录 (防越权安全控制)
     *
     * @param id     记录主键 ID
     * @param userId 当前用户 UUID
     * @return 是否成功删除
     */
    boolean deleteHistory(Long id, String userId);

    /**
     * 同步结构化分析接口 (历史兼容)
     *
     * @param inputText 输入文本
     * @param userId    当前用户 UUID
     * @return 结构化分析结果实体
     */
    AnalyzeResult analyze(String inputText, String userId);
}

package com.zerototech.model;

/**
 * 分析结果实体：供 Spring AI 自动结构化映射，并作为 JSON 返回给前端
 *
 * @param text      原文
 * @param pinyin    中文拼音（带声调）
 * @param score     情感分数（0.0 代表极度消极，1.0 代表极度积极，0.5 为中性）
 * @param sentiment 情绪判断标签（如：偏积极、偏消极、中性平和等）
 */
public record AnalyzeResult(
    String text,
    String pinyin,
    Double score,
    String sentiment
) {}

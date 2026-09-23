package com.zerototech.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 全局配置类：注入会话记忆组件
 */
@Configuration
public class AiConfig {

    /**
     * 内存型会话记忆存储器（基于 Map 维护不同 conversationId 的历史消息，零数据库改动）
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
}

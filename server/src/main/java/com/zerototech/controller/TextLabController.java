package com.zerototech.controller;

import com.zerototech.entity.AnalysisRecord;
import com.zerototech.model.AnalyzeRequest;
import com.zerototech.model.AnalyzeResult;
import com.zerototech.service.TextLabService;
import com.zerototech.util.UserSessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 语言与文本分析控制器 (Controller 控制接入层)
 * 职责：负责接收 HTTP/SSE 请求、Cookie/Session 身份解析、参数合法性处理、并委托 Service 层执行业务
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(
    origins = {"http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:8080", "http://127.0.0.1:8080"},
    allowCredentials = "true"
)
public class TextLabController {

    private final TextLabService textLabService;

    public TextLabController(TextLabService textLabService) {
        this.textLabService = textLabService;
    }

    /**
     * 流式文本与情感分析接口 (SSE: text/event-stream)
     */
    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyzeStream(
        @RequestBody AnalyzeRequest requestBody,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String inputText = requestBody.getText();
        if (inputText == null || inputText.isBlank()) {
            return Flux.just("{\"type\":\"error\",\"message\":\"输入内容不能为空\"}");
        }

        String userId = UserSessionUtils.getOrCreateUserId(request, response);
        return textLabService.analyzeStream(inputText, userId);
    }

    /**
     * 多轮追问流式接口 (SSE: text/event-stream)
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
        @RequestBody Map<String, String> body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Flux.just("{\"type\":\"error\",\"message\":\"追问内容不能为空\"}");
        }

        String userId = UserSessionUtils.getOrCreateUserId(request, response);
        return textLabService.chatStream(message, userId);
    }

    /**
     * 清空当前用户的会话记忆
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<Map<String, Object>> clearChat(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String userId = UserSessionUtils.getOrCreateUserId(request, response);
        textLabService.clearChatMemory(userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "会话记忆已清空"));
    }

    /**
     * 查询当前用户的最近 10 条历史记录 (用户会话隔离)
     */
    @GetMapping("/history")
    public List<AnalysisRecord> getHistory(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String userId = UserSessionUtils.getOrCreateUserId(request, response);
        return textLabService.getHistory(userId);
    }

    /**
     * 删除当前用户的指定历史记录 (防越权安全控制)
     */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Map<String, Object>> deleteHistory(
        @PathVariable Long id,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String userId = UserSessionUtils.getOrCreateUserId(request, response);
        boolean success = textLabService.deleteHistory(id, userId);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "记录不存在或无权删除"));
        }
    }

    /**
     * 同步结构化分析接口 (历史兼容)
     */
    @Deprecated
    @PostMapping("/analyze")
    public AnalyzeResult analyze(
        @RequestBody AnalyzeRequest requestBody,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String inputText = requestBody.getText();
        String userId = UserSessionUtils.getOrCreateUserId(request, response);
        return textLabService.analyze(inputText, userId);
    }
}

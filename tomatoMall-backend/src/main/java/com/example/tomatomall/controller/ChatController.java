package com.example.tomatomall.controller;

import com.example.tomatomall.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI聊天控制器
 * 提供基于Spring AI的对话和推荐接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 简单的AI对话接口
     * 用于测试DeepSeek连接和基础对话功能
     *
     * @param request 请求体，包含message字段
     * @return AI的回复
     */
    @PostMapping("/simple")
    public ResponseEntity<Map<String, String>> simpleChat(@RequestBody Map<String, String> request) {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           📨 收到HTTP请求: /api/chat/simple                ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        try {
            String userMessage = request.get("message");
            log.info("收到聊天请求");

            if (userMessage == null || userMessage.trim().isEmpty()) {
                log.warn("⚠️  警告：message参数为空");
                return ResponseEntity.status(400).body(Map.of(
                    "code", "400",
                    "message", "请求参数错误：message不能为空"
                ));
            }

            log.info("⏳ 调用ChatService.chat()...");
            String aiResponse = chatService.chat(userMessage);

            log.info("✅ 服务调用成功，准备返回响应");
            return ResponseEntity.ok(Map.of(
                "code", "200",
                "message", "对话成功",
                "data", aiResponse
            ));

        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════╗");
            log.error("║           ❌ 简单对话接口异常                                ║");
            log.error("╚════════════════════════════════════════════════════════════╝");
            log.error("❌ 错误类型: {}", e.getClass().getName());
            log.error("❌ 错误消息: {}", e.getMessage());
            log.error("❌ 堆栈跟踪:", e);

            return ResponseEntity.status(500).body(Map.of(
                "code", "500",
                "message", "对话失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 图书推荐接口 - RAG应用
     * 这个接口会结合商品数据库为用户提供智能推荐
     *
     * @param request 请求体，包含query字段
     * @return 推荐结果
     */
    @PostMapping("/recommend")
    public ResponseEntity<Map<String, String>> recommendBooks(@RequestBody Map<String, String> request) {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           📨 收到HTTP请求: /api/chat/recommend               ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        try {
            String userQuery = request.get("query");
            log.info("收到推荐请求");

            if (userQuery == null || userQuery.trim().isEmpty()) {
                log.warn("⚠️  警告：query参数为空");
                return ResponseEntity.status(400).body(Map.of(
                    "code", "400",
                    "message", "请求参数错误：query不能为空"
                ));
            }

            log.info("⏳ 调用ChatService.recommendBooks()...");
            String recommendation = chatService.recommendBooks(userQuery);

            log.info("✅ 服务调用成功，准备返回响应");
            return ResponseEntity.ok(Map.of(
                "code", "200",
                "message", "推荐成功",
                "data", recommendation
            ));

        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════╗");
            log.error("║           ❌ 图书推荐接口异常                                ║");
            log.error("╚════════════════════════════════════════════════════════════╝");
            log.error("❌ 错误类型: {}", e.getClass().getName());
            log.error("❌ 错误消息: {}", e.getMessage());
            log.error("❌ 堆栈跟踪:", e);

            return ResponseEntity.status(500).body(Map.of(
                "code", "500",
                "message", "推荐失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 健康检查接口
     * 用于验证AI服务是否正常
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "message", "Spring AI Chat服务运行正常",
            "provider", "DeepSeek API"
        ));
    }
}

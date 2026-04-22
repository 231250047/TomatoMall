package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.DeepSeekService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek API直接调用实现
 * 不依赖Spring AI的OpenAI适配器
 */
@Slf4j
@Service
public class DeepSeekServiceImpl implements DeepSeekService {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String MODEL = "deepseek-chat";

    private final RestTemplate restTemplate = new RestTemplate();

    // 配置ObjectMapper忽略未知字段
    private final ObjectMapper objectMapper = new ObjectMapper() {{
        configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }};

    @Override
    public String chat(String message) {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           💬 DeepSeek直接调用（简单对话）                    ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("📝 用户消息: {}", message);
        log.info("🔗 API URL: {}", API_URL);
        log.info("🤖 模型: {}", MODEL);

        try {
            // 构建请求体
            ChatRequest request = new ChatRequest();
            request.model = MODEL;
            request.messages = new ArrayList<>();
            request.messages.add(new Message("user", message));
            request.max_tokens = 2000;
            request.temperature = 0.7;

            // 发送请求
            String responseJson = sendRequest(request);

            // 解析响应
            ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);

            if (response.choices == null || response.choices.isEmpty()) {
                throw new RuntimeException("DeepSeek返回空响应");
            }

            String content = response.choices.get(0).message.content;
            log.info("✅ DeepSeek调用成功");
            log.info("🤖 AI回复: {}", content);
            log.info("╚════════════════════════════════════════════════════════════╝");

            return content;

        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════╗");
            log.error("║           ❌ DeepSeek调用失败                               ║");
            log.error("╚════════════════════════════════════════════════════════════╝");
            log.error("❌ 错误类型: {}", e.getClass().getName());
            log.error("❌ 错误消息: {}", e.getMessage());
            log.error("❌ 堆栈跟踪:", e);
            throw new RuntimeException("DeepSeek调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String chatWithPrompt(String prompt) {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           💬 DeepSeek直接调用（带Prompt）                   ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("📝 Prompt长度: {} 字符", prompt.length());
        log.info("🔗 API URL: {}", API_URL);
        log.info("🤖 模型: {}", MODEL);

        try {
            // 构建请求体
            ChatRequest request = new ChatRequest();
            request.model = MODEL;
            request.messages = new ArrayList<>();
            request.messages.add(new Message("user", prompt));
            request.max_tokens = 2000;
            request.temperature = 0.7;

            // 发送请求
            String responseJson = sendRequest(request);

            // 解析响应
            ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);

            if (response.choices == null || response.choices.isEmpty()) {
                throw new RuntimeException("DeepSeek返回空响应");
            }

            String content = response.choices.get(0).message.content;
            log.info("✅ DeepSeek调用成功");
            log.info("🤖 AI回复: {}", content);
            log.info("╚════════════════════════════════════════════════════════════╝");

            return content;

        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════╗");
            log.error("║           ❌ DeepSeek调用失败                               ║");
            log.error("╚════════════════════════════════════════════════════════════╝");
            log.error("❌ 错误类型: {}", e.getClass().getName());
            log.error("❌ 错误消息: {}", e.getMessage());
            log.error("❌ 堆栈跟踪:", e);
            throw new RuntimeException("DeepSeek调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送HTTP请求到DeepSeek API
     */
    private String sendRequest(ChatRequest request) throws Exception {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📤 发送HTTP请求到DeepSeek...");

        // 序列化请求
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String requestJson = objectMapper.writeValueAsString(request);
        log.debug("📄 请求体: {}", requestJson);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        log.info("🔗 目标URL: {}", API_URL);
        log.info("🔑 API Key: {}...{}",
            apiKey.substring(0, Math.min(8, apiKey.length())),
            apiKey.length() > 4 ? apiKey.substring(apiKey.length() - 4) : "");

        // 发送请求
        ResponseEntity<String> response = restTemplate.exchange(
            API_URL,
            HttpMethod.POST,
            entity,
            String.class
        );

        log.info("📥 HTTP状态码: {}", response.getStatusCode());

        if (response.getStatusCode() == HttpStatus.OK) {
            log.debug("📄 响应体: {}", response.getBody());
            return response.getBody();
        } else {
            throw new RuntimeException("DeepSeek API返回错误: " + response.getStatusCode());
        }
    }

    // ==================== 数据模型 ====================

    @Data
    private static class ChatRequest {
        String model;
        List<Message> messages;
        @JsonProperty("max_tokens")
        Integer max_tokens;
        Double temperature;
    }

    @Data
    private static class Message {
        String role;
        String content;

        // 默认构造函数（用于JSON反序列化）
        Message() {}

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    private static class ChatResponse {
        String id;
        String object;
        Long created;
        String model;
        List<Choice> choices;
        Usage usage;
    }

    @Data
    private static class Choice {
        Integer index;
        Message message;
        @JsonProperty("finish_reason")
        String finish_reason;
        Object logprobs;  // DeepSeek返回的logprobs字段（可能是null或对象）
    }

    @Data
    private static class Usage {
        @JsonProperty("prompt_tokens")
        Integer prompt_tokens;
        @JsonProperty("completion_tokens")
        Integer completion_tokens;
        @JsonProperty("total_tokens")
        Integer total_tokens;
    }
}

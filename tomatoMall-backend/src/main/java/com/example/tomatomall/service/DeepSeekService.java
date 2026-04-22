package com.example.tomatomall.service;

/**
 * DeepSeek直接调用服务
 * 绕过Spring AI的OpenAI适配器，直接使用RestTemplate调用DeepSeek API
 */
public interface DeepSeekService {
    /**
     * 简单对话
     * @param message 用户消息
     * @return AI回复
     */
    String chat(String message);

    /**
     * 带上下文的对话
     * @param prompt 完整的prompt
     * @return AI回复
     */
    String chatWithPrompt(String prompt);
}

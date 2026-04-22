package com.example.tomatomall.service;

/**
 * AI聊天服务接口
 * 定义基础的AI对话功能
 */
public interface ChatService {

    /**
     * 简单的AI对话
     * @param userMessage 用户消息
     * @return AI回复
     */
    String chat(String userMessage);

    /**
     * 图书推荐对话
     * @param userQuery 用户查询
     * @return 推荐结果
     */
    String recommendBooks(String userQuery);
}

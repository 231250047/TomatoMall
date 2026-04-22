package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.service.ChatService;
import com.example.tomatomall.service.DeepSeekService;
import com.example.tomatomall.service.VectorRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI聊天服务实现 - 真正的RAG版本
 * 使用DeepSeek + 向量检索实现智能推荐
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final DeepSeekService deepSeekService;  // DeepSeek服务
    private final VectorRetrievalService vectorRetrievalService;  // 向量检索服务

    /**
     * 简单的AI对话（智能判断是否需要RAG）
     */
    @Override
    @Transactional(readOnly = true)
    public String chat(String userMessage) {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           💬 AI对话（LLM智能判断模式）                       ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("📝 用户消息: {}", userMessage);

        try {
            // ============ 方案：让LLM自己判断是否需要RAG ============
            log.info("🤖 让LLM判断用户意图...");

            // 先进行向量检索（获取知识库）
            List<Product> knowledgeBase = vectorRetrievalService.similaritySearch(userMessage, 3);
            log.info("📚 已加载知识库（{}本书）", knowledgeBase.size());

            // 构建智能Prompt，让LLM自己决定
            String smartPrompt = buildSmartPrompt(userMessage, knowledgeBase);

            // 调用DeepSeek
            String response = deepSeekService.chatWithPrompt(smartPrompt);

            log.info("✅ LLM智能判断完成");
            log.info("╚════════════════════════════════════════════════════════════╝");
            return response;

        } catch (Exception e) {
            log.error("❌ 对话失败", e);
            return "抱歉，AI服务暂时不可用，请稍后再试";
        }
    }

    /**
     * 构建智能Prompt - 让LLM自己判断是否使用知识库
     */
    private String buildSmartPrompt(String userMessage, List<Product> knowledgeBase) {
        StringBuilder prompt = new StringBuilder();

        // 1. 设置角色和任务
        prompt.append("你是一个智能图书推荐助手，具备以下能力：\n");
        prompt.append("- 可以进行友好的对话交流\n");
        prompt.append("- 可以根据知识库推荐相关书籍\n");
        prompt.append("- 能够智能判断用户意图\n\n");

        // 2. 提供知识库（如果需要）
        if (!knowledgeBase.isEmpty()) {
            prompt.append("【知识库】以下是系统检索到的相关书籍（供推荐时使用）：\n\n");
            for (int i = 0; i < knowledgeBase.size(); i++) {
                Product product = knowledgeBase.get(i);
                prompt.append(String.format(
                    "%d. 《%s》\n   - 价格: ￥%.2f\n   - 评分: %.1f分\n   - 分类: %s\n   - 简介: %s\n\n",
                    i + 1,
                    product.getTitle(),
                    product.getPrice(),
                    product.getRate(),
                    product.getTag(),
                    product.getDescription() != null ? truncate(product.getDescription(), 80) : "暂无简介"
                ));
            }
        }

        // 3. 核心指令：让LLM智能判断
        prompt.append("【回复要求】请根据用户消息智能判断并回复：\n\n");
        prompt.append("1️⃣ 如果用户**询问图书推荐**（如\"推荐Java书\"、\"有什么Python入门书\"）：\n");
        prompt.append("   - 请从上述知识库中选择最相关的书籍进行推荐\n");
        prompt.append("   - 重点推荐评分高、与用户需求匹配的书籍\n");
        prompt.append("   - 说明推荐理由\n");
        prompt.append("   - 不要提及不相关的书籍\n\n");

        prompt.append("2️⃣ 如果用户**只是打招呼或闲聊**（如\"你好\"、\"在吗\"、\"今天天气怎么样\"）：\n");
        prompt.append("   - 请友好地回复，不要提及知识库中的书籍\n");
        prompt.append("   - 进行自然的对话交流\n\n");

        prompt.append("3️⃣ 如果用户**询问其他问题**：\n");
        prompt.append("   - 尽力回答\n");
        prompt.append("   - 如果不知道，礼貌地说明\n\n");

        // 4. 用户消息
        prompt.append("────────────────────────────\n");
        prompt.append("【用户消息】\n");
        prompt.append(userMessage);
        prompt.append("\n────────────────────────────\n\n");

        prompt.append("请开始回复：");

        return prompt.toString();
    }

    /**
     * 图书推荐对话（已重构为使用智能判断）
     * 保留此方法以兼容接口，内部统一使用chat方法
     */
    @Override
    @Transactional(readOnly = true)
    public String recommendBooks(String userQuery) {
        // 直接使用chat方法，让LLM智能判断
        return chat(userQuery);
    }

    /**
     * 辅助方法:截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}

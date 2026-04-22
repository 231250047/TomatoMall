package com.example.tomatomall.config;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 阿里云DashScope Embedding模型配置
 * 用于替代OpenAI的Embedding服务
 */
@Slf4j
@Component
public class DashScopeEmbeddingModelConfig {

    @Value("${aliyun.dashscope.api-key}")
    private String apiKey;

    @Value("${aliyun.dashscope.embedding.model:text-embedding-v4}")
    private String model;

    @Value("${aliyun.dashscope.embedding.dimensions:1024}")
    private int dimensions;

    /**
     * 创建阿里云DashScope的EmbeddingModel Bean
     * 使用@Primary让它优先于OpenAI的默认实现
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           🔧 初始化阿里云DashScope Embedding模型               ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("📌 API Key: {}", maskApiKey(apiKey));
        log.info("📌 模型: {}", model);
        log.info("📌 向量维度: {}", dimensions);
        log.info("📌 提供商: 阿里云DashScope");
        log.info("📌 用途: 文本向量化（用于RAG检索）");
        log.info("╚════════════════════════════════════════════════════════════╝");

        return new DashScopeEmbeddingModel(model, dimensions, apiKey);
    }

    /**
     * 遮蔽API Key的中间部分
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return "***";
        }
        return apiKey.substring(0, 8) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}

/**
 * 阿里云DashScope Embedding模型实现
 * 实现Spring AI的EmbeddingModel接口
 */
@Slf4j
class DashScopeEmbeddingModel implements EmbeddingModel {

    private final String model;
    private final int dimensions;
    private final String apiKey;

    public DashScopeEmbeddingModel(String model, int dimensions, String apiKey) {
        this.model = model;
        this.dimensions = dimensions;
        this.apiKey = apiKey;

        // 设置DashScope API Key到系统环境变量
        if (System.getenv("DASHSCOPE_API_KEY") == null) {
            System.setProperty("dashscope.api.key", apiKey);
            log.info("✅ 已设置DashScope API Key到系统属性");
        }
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        try {
            // 获取输入文本列表
            List<String> inputs = request.getInstructions();
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("🚀 开始调用DashScope Embedding API");
            log.info("📊 输入文本数量: {}", inputs.size());
            log.info("🔧 使用模型: {}", model);
            log.info("📐 期望向量维度: {}", dimensions);

            // 打印输入文本预览（前50字符）
            for (int i = 0; i < inputs.size(); i++) {
                String preview = inputs.get(i);
                if (preview.length() > 50) {
                    preview = preview.substring(0, 50) + "...";
                }
                log.info("📝 输入[{}]: {}", i, preview);
            }

            // 调用DashScope API
            log.info("⏳ 正在请求DashScope API...");
            List<Embedding> embeddings = new java.util.ArrayList<>();

            for (int i = 0; i < inputs.size(); i++) {
                String text = inputs.get(i);

                // 创建TextEmbedding实例
                TextEmbedding textEmbedding = new TextEmbedding();

                // 构建单个文本的embedding请求
                TextEmbeddingParam param = TextEmbeddingParam.builder()
                        .model(model)
                        .text(text)  // 单个文本
                        .apiKey(apiKey)  // 设置API Key
                        .build();

                // 调用API返回单个结果
                TextEmbeddingResult result = textEmbedding.call(param);

                // 提取向量
                float[] vector = extractEmbedding(result);

                log.info("📦 向量[{}] - 维度: {}, 前5个值: [{}, {}, {}, {}, {}]",
                    i,
                    vector.length,
                    String.format("%.4f", vector[0]),
                    String.format("%.4f", vector[1]),
                    String.format("%.4f", vector[2]),
                    String.format("%.4f", vector[3]),
                    String.format("%.4f", vector[4])
                );

                // 验证向量维度
                if (vector.length != dimensions) {
                    log.warn("⚠️ 向量维度不匹配！期望: {}，实际: {}", dimensions, vector.length);
                } else {
                    log.info("✓ 向量维度验证通过");
                }

                // 创建Spring AI的Embedding对象
                embeddings.add(new Embedding(vector, i));
            }

            log.info("✅ DashScope API调用成功！返回结果数: {}", embeddings.size());
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return new EmbeddingResponse(embeddings);

        } catch (NoApiKeyException e) {
            log.error("❌ 阿里云DashScope API Key未配置");
            log.error("错误详情: {}", e.getMessage());
            log.error("请检查application.yml中的 aliyun.dashscope.api-key 配置");
            throw new RuntimeException("阿里云DashScope API Key未配置，请在application.yml中配置 aliyun.dashscope.api-key", e);
        } catch (ApiException e) {
            log.error("❌ DashScope API调用失败 - ApiException");
            log.error("错误消息: {}", e.getMessage());
            log.error("错误详情: {}", e.toString());
            throw new RuntimeException("调用DashScope Embedding API失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // 捕获所有其他运行时异常（包括InputRequiredException）
            log.error("❌ DashScope API调用失败 - {}", e.getClass().getSimpleName());
            log.error("错误消息: {}", e.getMessage());
            throw new RuntimeException("调用DashScope Embedding API失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从TextEmbeddingResult中提取向量
     * 兼容不同版本的DashScope SDK
     */
    private float[] extractEmbedding(TextEmbeddingResult result) {
        try {
            // 尝试不同的方法名
            try {
                // 方法1: getOutput().getEmbeddings()
                var output = result.getOutput();
                if (output != null) {
                    var embeddings = output.getEmbeddings();
                    if (embeddings != null && !embeddings.isEmpty()) {
                        // getEmbedding() 返回 List<Double>，需要转换为 float[]
                        List<Double> embeddingList = embeddings.get(0).getEmbedding();
                        float[] embedding = new float[embeddingList.size()];
                        for (int i = 0; i < embeddingList.size(); i++) {
                            embedding[i] = embeddingList.get(i).floatValue();
                        }
                        return embedding;
                    }
                }
            } catch (Exception e1) {
                log.debug("方法1失败: {}", e1.getMessage());
            }

            // 方法2: 直接尝试获取embedding
            try {
                // 尝试反射调用
                java.lang.reflect.Field embeddingField = result.getClass().getDeclaredField("embedding");
                embeddingField.setAccessible(true);
                Object embeddingObj = embeddingField.get(result);

                if (embeddingObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Number> embeddingList = (List<Number>) embeddingObj;
                    float[] embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        Number num = embeddingList.get(i);
                        embedding[i] = num.floatValue();
                    }
                    return embedding;
                } else if (embeddingObj instanceof float[]) {
                    return (float[]) embeddingObj;
                } else if (embeddingObj instanceof double[]) {
                    double[] dblArray = (double[]) embeddingObj;
                    float[] embedding = new float[dblArray.length];
                    for (int i = 0; i < dblArray.length; i++) {
                        embedding[i] = (float) dblArray[i];
                    }
                    return embedding;
                }
            } catch (Exception e2) {
                log.debug("方法2失败: {}", e2.getMessage());
            }

            // 方法3: 从结果对象中提取
            Map<String, Object> resultMap = parseResult(result);
            if (resultMap.containsKey("embedding")) {
                return convertToFloatArray(resultMap.get("embedding"));
            }

            throw new RuntimeException("无法从TextEmbeddingResult中提取向量数据");

        } catch (Exception e) {
            log.error("❌ 提取向量失败", e);
            throw new RuntimeException("提取向量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析TextEmbeddingResult为Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResult(TextEmbeddingResult result) {
        try {
            // 尝试通过toString解析
            String resultStr = result.toString();
            if (resultStr.contains("embedding")) {
                // 简单的解析逻辑
                Map<String, Object> map = new HashMap<>();
                // 这里需要根据实际的返回格式进行解析
                return map;
            }
        } catch (Exception e) {
            log.debug("解析result失败: {}", e.getMessage());
        }
        return new HashMap<>();
    }

    /**
     * 转换为float数组
     */
    @SuppressWarnings("unchecked")
    private float[] convertToFloatArray(Object obj) {
        if (obj instanceof float[]) {
            return (float[]) obj;
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            float[] array = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number) {
                    array[i] = ((Number) item).floatValue();
                } else {
                    throw new RuntimeException("列表元素不是数字类型: " + item.getClass());
                }
            }
            return array;
        } else if (obj instanceof double[]) {
            double[] dblArray = (double[]) obj;
            float[] array = new float[dblArray.length];
            for (int i = 0; i < dblArray.length; i++) {
                array[i] = (float) dblArray[i];
            }
            return array;
        }
        throw new RuntimeException("无法转换为float数组: " + obj.getClass());
    }

    @Override
    public float[] embed(Document document) {
        try {
            log.info("📄 单个文档向量化: {}",
                document.getContent().length() > 50 ?
                document.getContent().substring(0, 50) + "..." :
                document.getContent());

            TextEmbedding textEmbedding = new TextEmbedding();
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(model)
                    .text(document.getContent())
                    .apiKey(apiKey)  // 设置API Key
                    .build();

            TextEmbeddingResult result = textEmbedding.call(param);
            float[] embedding = extractEmbedding(result);

            log.info("✅ 文档向量化成功，维度: {}", embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("❌ 文档向量化失败", e);
            throw new RuntimeException("文档向量化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            log.info("📝 单个文本向量化: {}",
                text.length() > 50 ? text.substring(0, 50) + "..." : text);

            TextEmbedding textEmbedding = new TextEmbedding();
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(model)
                    .text(text)
                    .apiKey(apiKey)  // 设置API Key
                    .build();

            TextEmbeddingResult result = textEmbedding.call(param);
            float[] embedding = extractEmbedding(result);

            log.info("✅ 文本向量化成功，维度: {}", embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("❌ 文本向量化失败", e);
            throw new RuntimeException("文本向量化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        try {
            log.info("📚 批量文本向量化，数量: {}", texts.size());

            List<float[]> embeddings = new java.util.ArrayList<>();

            for (int i = 0; i < texts.size(); i++) {
                String text = texts.get(i);
                float[] embedding = embed(text);
                embeddings.add(embedding);

                log.info("✅ [{}/{}] 向量化完成", i + 1, texts.size());
            }

            log.info("✅ 批量向量化完成，总计: {}", embeddings.size());
            return embeddings;

        } catch (Exception e) {
            log.error("❌ 批量文本向量化失败", e);
            throw new RuntimeException("批量文本向量化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimensions() {
        return this.dimensions;
    }
}

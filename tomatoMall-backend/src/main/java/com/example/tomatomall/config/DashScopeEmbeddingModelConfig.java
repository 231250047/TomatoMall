package com.example.tomatomall.config;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.protocol.ConnectionConfigurations;
import com.alibaba.dashscope.utils.Constants;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** DashScope SDK 2.16.7 adapter for Spring AI 1.0.0-M4. */
@Component
public class DashScopeEmbeddingModelConfig {
    @Value("${aliyun.dashscope.api-key:}")
    private String apiKey;
    @Value("${aliyun.dashscope.embedding.model:text-embedding-v4}")
    private String model;
    @Value("${aliyun.dashscope.embedding.dimensions:1024}")
    private int dimensions;
    @Value("${aliyun.dashscope.embedding.connect-timeout-seconds:5}")
    private int connectTimeoutSeconds;
    @Value("${aliyun.dashscope.embedding.read-timeout-seconds:20}")
    private int readTimeoutSeconds;
    @Value("${aliyun.dashscope.embedding.response-timeout-seconds:30}")
    private int responseTimeoutSeconds;

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0 || responseTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Embedding timeouts must be positive");
        }
        // SDK 2.16.7 uses process-wide connection settings. Configure before creating its client;
        // these timeouts also apply to other DashScope SDK clients in this JVM.
        Constants.connectionConfigurations = ConnectionConfigurations.builder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
                .build();
        return new DashScopeEmbeddingModel(model, dimensions, apiKey);
    }
}

class DashScopeEmbeddingModel implements EmbeddingModel {
    private final String model;
    private final int dimensions;
    private final String apiKey;

    public DashScopeEmbeddingModel(String model, int dimensions, String apiKey) {
        if (model == null || model.isBlank() || dimensions <= 0) {
            throw new IllegalArgumentException("Embedding model and positive dimensions are required");
        }
        this.model = model;
        this.dimensions = dimensions;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Embedding request is required");
        }
        List<float[]> vectors = embed(request.getInstructions());
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            embeddings.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Embedding document is required");
        }
        // Metadata contains identifiers and live business attributes, not semantic content.
        return embed(document.getContent());
    }

    @Override
    public float[] embed(String text) {
        validateCredentials();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding text must not be blank");
        }
        TextEmbeddingParam param = TextEmbeddingParam.builder()
                .model(model)
                .text(text)
                .apiKey(apiKey)
                .parameter("dimension", dimensions)
                .build();
        TextEmbeddingResult result;
        try {
            result = new TextEmbedding().call(param);
        } catch (Exception failure) {
            // SDK exception bodies may contain credentials or input. Do not propagate/log them.
            throw new IllegalStateException("DashScope embedding request failed");
        }
        return extractEmbedding(result);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        validateCredentials();
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(t -> t == null || t.isBlank())) {
            throw new IllegalArgumentException("Embedding texts must not be empty or blank");
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    private void validateCredentials() {
        String key = apiKey.toLowerCase(Locale.ROOT);
        if (key.isBlank() || key.contains("placeholder") || key.startsWith("your-")
                || key.startsWith("your_") || key.equals("changeme") || key.startsWith("${")) {
            // Keep the bean available for keyword fallback; fail before constructing an SDK client.
            throw new IllegalStateException("DashScope embedding credentials are not configured");
        }
    }

    private float[] extractEmbedding(TextEmbeddingResult result) {
        if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null
                || result.getOutput().getEmbeddings().size() != 1
                || result.getOutput().getEmbeddings().get(0) == null) {
            throw new IllegalStateException("DashScope returned missing or unexpected embedding output");
        }
        List<Double> values = result.getOutput().getEmbeddings().get(0).getEmbedding();
        if (values == null || values.size() != dimensions) {
            throw new IllegalStateException("DashScope embedding dimension does not match configuration");
        }
        float[] vector = new float[dimensions];
        boolean nonzero = false;
        for (int i = 0; i < dimensions; i++) {
            Double value = values.get(i);
            if (value == null || !Double.isFinite(value) || !Float.isFinite(value.floatValue())) {
                throw new IllegalStateException("DashScope returned a non-finite embedding value");
            }
            vector[i] = value.floatValue();
            nonzero |= vector[i] != 0;
        }
        if (!nonzero) {
            throw new IllegalStateException("DashScope returned a zero embedding vector");
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}

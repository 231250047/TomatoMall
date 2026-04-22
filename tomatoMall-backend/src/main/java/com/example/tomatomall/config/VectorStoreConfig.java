package com.example.tomatomall.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.embedding.EmbeddingModel;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 向量数据库配置(使用SimpleVectorStore�?
 * SimpleVectorStore是Spring AI内置的内存向量存储，无需额外服务
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.simple.store-path:src/main/resources/data/vectorstore}")
    private String storePath;

    private final EmbeddingModel embeddingModel;

    public VectorStoreConfig(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 创建SimpleVectorStore
     * 这是RAG系统的核心组�?
     */
    @Bean
    @Primary
    public VectorStore vectorStore() {
        log.info("初始化SimpleVectorStore...");
        log.info("向量存储路径: {}", storePath);

        try {
            // 创建SimpleVectorStore(使用默认构造函数)
            SimpleVectorStore vectorStore = new SimpleVectorStore(embeddingModel);

            log.info("SimpleVectorStore初始化成功!");
            return vectorStore;

        } catch (Exception e) {
            log.error("SimpleVectorStore初始化失败");
            throw new RuntimeException("SimpleVectorStore初始化失败");
        }
    }
}

package com.example.tomatomall.config;
import com.example.tomatomall.retrieval.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.util.*;
@Configuration
public class VectorStoreConfig {
    @Bean(destroyMethod="")
    public ProductVectorIndex productVectorIndex(EmbeddingModel model,ProductCatalog catalog,
            @Value("${rag.vector.enabled:false}") boolean enabled,
            @Value("${rag.vector.url:jdbc:postgresql://127.0.0.1:15432/tomatomall_vectors}") String url,
            @Value("${rag.vector.username:tomatomall}") String username,
            @Value("${rag.vector.password:}") String password,
            @Value("${aliyun.dashscope.embedding.dimensions:1024}") int dimensions,
            @Value("${aliyun.dashscope.embedding.model:text-embedding-v4}") String modelName,
            @Value("${rag.vector.namespace:tomatomall}") String namespace) {
        if(enabled) return new PgProductVectorIndex(url,username,password,model,dimensions,modelName,namespace,catalog);
        return new ProductVectorIndex() {
            public List<ProductVectorIndex.Match> search(String q,int k) { throw new IllegalStateException("Vector index disabled"); }
            public List<ProductVectorIndex.Match> search(String q,int k,Set<Integer> eligibleIds) {
                if(eligibleIds.isEmpty()) return List.of();
                throw new IllegalStateException("Vector index disabled");
            }
            public void sync(int id) { throw new IllegalStateException("Vector index disabled"); }
            public Set<Integer> indexedIds() { throw new IllegalStateException("Vector index disabled"); }
            public Map<String,Object> status() { return Map.of("status","DISABLED","store","pgvector"); }
        };
    }
}

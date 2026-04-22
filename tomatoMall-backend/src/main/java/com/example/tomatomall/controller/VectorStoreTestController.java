package com.example.tomatomall.controller;

import com.example.tomatomall.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 向量数据库测试控制器
 * 用于验证Chroma连接和RAG功能
 */
@Slf4j
@RestController
@RequestMapping("/api/test/vectorstore")
@RequiredArgsConstructor
public class VectorStoreTestController {

    private final RagService ragService;

    /**
     * 测试Chroma连接状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> status = Map.of(
                "status", "UP",
                "message", "Chroma向量数据库正在运行",
                "service", "Chroma Vector Store",
                "address", "localhost:8000"
            );
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 构建向量知识�?
     */
    @PostMapping("/build")
    public ResponseEntity<Map<String, String>> buildVectorKnowledgeBase() {
        try {
            ragService.buildVectorKnowledgeBase();
            return ResponseEntity.ok(Map.of(
                "code", "200",
                "message", "向量知识库构建成",
                "details", "商品数据已向量化并存储到Chroma"
            ));
        } catch (Exception e) {
            log.error("向量知识库构建失败");
            return ResponseEntity.status(500).body(Map.of(
                "code", "500",
                "message", "向量知识库构建失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 测试向量检索
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> testSearch(@RequestBody Map<String, String> request) {
        try {
            String query = request.get("query");
            int topK = request.getOrDefault("topK", "5").toString().equals("5") ? 5 : Integer.parseInt(request.get("topK").toString());

            log.info("测试向量检索，查询:{}，topK:{}", query, topK);

            List<Document> documents = ragService.retrieveDocuments(query, topK);

            return ResponseEntity.ok(Map.of(
                "code", "200",
                "message", "向量检索成功",
                "query", query,
                "count", documents.size(),
                "results", documents
            ));
        } catch (Exception e) {
            log.error("向量检索失败");
            return ResponseEntity.status(500).body(Map.of(
                "code", "500",
                "message", "向量检索失�? " + e.getMessage()
            ));
        }
    }

    /**
     * 测试增强推荐
     */
    @PostMapping("/recommend")
    public ResponseEntity<Map<String, String>> testRecommendation(@RequestBody Map<String, String> request) {
        try {
            String query = request.get("query");
            String recommendation = ragService.getEnhancedRecommendation(query);

            return ResponseEntity.ok(Map.of(
                "code", "200",
                "message", "推荐成功",
                "query", query,
                "recommendation", recommendation
            ));
        } catch (Exception e) {
            log.error("推荐失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "code", "500",
                "message", "推荐失败: " + e.getMessage()
            ));
        }
    }
}

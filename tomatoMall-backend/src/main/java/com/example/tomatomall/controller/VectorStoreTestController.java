package com.example.tomatomall.controller;

import com.example.tomatomall.Util.SecurityUtil;
import com.example.tomatomall.retrieval.*;
import com.example.tomatomall.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

/** Legacy URL retained; all retrieval now uses the same production service. */
@RestController @RequestMapping("/api/test/vectorstore") @RequiredArgsConstructor
public class VectorStoreTestController {
    private final ProductRetrievalService retrieval;
    private final ProductIndexMaintenance maintenance;
    private final ChatService chat;
    private final SecurityUtil security;
    @GetMapping("/status") public Map<String,Object> status() { requireAdmin();return maintenance.status(); }
    @PostMapping("/build") public Map<String,Object> build() { requireAdmin();return maintenance.reconcile(); }
    @PostMapping("/search") public ProductRetrievalService.Result search(@RequestBody Map<String,Object> body) {
        try {
            Set<String> fields=Set.of("query","tag","minPrice","maxPrice","inStockOnly","topK","mode","level","excludedTopics");
            for(String key:body.keySet()) if(!fields.contains(key)) throw new IllegalArgumentException("不支持的检索参数："+key);
            if(!(body.get("query") instanceof String)
                || (body.get("tag")!=null && !(body.get("tag") instanceof String))
                || (body.get("level")!=null && !(body.get("level") instanceof String))
                || (body.get("mode")!=null && !(body.get("mode") instanceof String))
                || (body.get("inStockOnly")!=null && !(body.get("inStockOnly") instanceof Boolean)))
                throw new IllegalArgumentException("检索参数类型不合法");
            return retrieval.search(new ProductSearchQuery((String)body.get("query"),(String)body.get("tag"),
                decimal(body.get("minPrice")),decimal(body.get("maxPrice")),
                !Boolean.FALSE.equals(body.get("inStockOnly")),Integer.parseInt(body.get("topK")==null?"5":body.get("topK").toString()),
                body.get("mode")==null?"hybrid":body.get("mode").toString(),(String)body.get("level"),topics(body.get("excludedTopics"))));
        } catch(IllegalArgumentException e) { throw e; }
    }
    @PostMapping("/recommend") public Map<String,String> recommend(@RequestBody Map<String,String> body) {
        return Map.of("data",chat.recommendBooks(body.get("query")));
    }
    private List<String> topics(Object value) {
        if(value==null) return List.of();
        if(!(value instanceof List<?> list) || list.stream().anyMatch(v->!(v instanceof String)))
            throw new IllegalArgumentException("excludedTopics必须为字符串数组");
        return ((List<?>)value).stream().map(String.class::cast).toList();
    }
    private BigDecimal decimal(Object value) { return value==null?null:new BigDecimal(value.toString()); }
    private void requireAdmin() {
        var user=security.getCurrentAccount();
        if(user==null || !"admin".equalsIgnoreCase(user.getRole())) throw new org.springframework.security.access.AccessDeniedException("需要管理员权限");
    }
}

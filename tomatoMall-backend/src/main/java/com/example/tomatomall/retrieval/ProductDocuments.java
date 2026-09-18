package com.example.tomatomall.retrieval;
import org.springframework.ai.document.Document;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
public final class ProductDocuments {
    public static final String TEXT_VERSION="product-v1";
    private ProductDocuments() {}
    public static String text(ProductSnapshot p) {
        return "书名："+p.title()+"\n分类："+p.tag()+"\n简介："+Objects.toString(p.description(),"")
            +"\n详情："+Objects.toString(p.detail(),"")+"\n规格："+String.join("；",p.specifications().stream().sorted().toList());
    }
    public static String id(int productId) { return UUID.nameUUIDFromBytes(("product:"+productId).getBytes(StandardCharsets.UTF_8)).toString(); }
    public static String hash(ProductSnapshot p) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text(p).getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static Document document(ProductSnapshot p) {
        return new Document(id(p.id()),text(p),Map.of("productId",p.id(),"contentHash",hash(p),"textVersion",TEXT_VERSION));
    }
}

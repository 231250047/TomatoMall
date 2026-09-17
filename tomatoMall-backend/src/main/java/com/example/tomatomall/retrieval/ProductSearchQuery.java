package com.example.tomatomall.retrieval;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
public record ProductSearchQuery(String query, String tag, BigDecimal minPrice, BigDecimal maxPrice,
        boolean inStockOnly, int topK, String mode, String level, List<String> excludedTopics) {
    public ProductSearchQuery(String query,String tag,BigDecimal minPrice,BigDecimal maxPrice,boolean inStockOnly,int topK,String mode) {
        this(query,tag,minPrice,maxPrice,inStockOnly,topK,mode,null,List.of());
    }
    private static final Pattern MAX = Pattern.compile("(?:不超过|不高于|(?<!不)低于|预算(?:为)?|最多)\\s*(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块)?|([0-9]+(?:\\.[0-9]{1,2})?)\\s*元(?:以内|以下)");
    public ProductSearchQuery {
        if(query==null || query.isBlank() || query.length()>1000) throw new IllegalArgumentException("查询长度应为1到1000字符");
        query=query.trim();
        if(tag!=null && !Set.of("science","education","literature","art","management","history","philosophy","health").contains(tag))
            throw new IllegalArgumentException("不支持的商品分类");
        if(level!=null && !Set.of("入门","进阶","高级").contains(level)) throw new IllegalArgumentException("难度应为入门、进阶或高级");
        if(excludedTopics==null) excludedTopics=List.of();
        if(excludedTopics.size()>10 || excludedTopics.stream().anyMatch(t->t==null || t.isBlank() || t.length()>64))
            throw new IllegalArgumentException("排除主题最多10项，每项1到64字符");
        excludedTopics=excludedTopics.stream().map(String::trim).distinct().toList();
        if(topK<1 || topK>20) throw new IllegalArgumentException("topK应为1到20");
        if(mode==null) mode="hybrid";
        if(!Set.of("hybrid","keyword","vector").contains(mode)) throw new IllegalArgumentException("不支持的检索模式");
        if(query.contains("总预算") || query.contains("合计") || query.contains("总价")) throw new IllegalArgumentException("总预算组合购书请先明确每本书的预算");
        if(maxPrice==null) {
            var m=MAX.matcher(query); if(m.find()) {
                maxPrice=new BigDecimal(m.group(1)!=null?m.group(1):m.group(2));
                if(m.group().startsWith("低于")) maxPrice=maxPrice.subtract(new BigDecimal("0.01"));
            }
        }
        if(minPrice==null) {
            var minimum=Pattern.compile("(?:不低于|至少|最低)\\s*(\\d+(?:\\.\\d{1,2})?)").matcher(query);
            if(minimum.find()) minPrice=new BigDecimal(minimum.group(1));
        }
        if((minPrice!=null && minPrice.stripTrailingZeros().scale()>2) || (maxPrice!=null && maxPrice.stripTrailingZeros().scale()>2))
            throw new IllegalArgumentException("价格最多两位小数");
        if((minPrice!=null && minPrice.signum()<0) || (maxPrice!=null && maxPrice.signum()<0)
            || (minPrice!=null && maxPrice!=null && minPrice.compareTo(maxPrice)>0)) throw new IllegalArgumentException("价格范围不合法");
    }
    public static ProductSearchQuery of(String text,int topK) { return new ProductSearchQuery(text,null,null,null,true,topK,"hybrid"); }
    public boolean accepts(ProductSnapshot p) {
        return "available".equals(p.status()) && (!inStockOnly || p.availableStock()>0)
            && (tag==null || tag.isBlank() || tag.equals(p.tag()))
            && (minPrice==null || p.price().compareTo(minPrice)>=0) && (maxPrice==null || p.price().compareTo(maxPrice)<=0)
            && (level==null || level.equals(p.level()))
            && excludedTopics.stream().noneMatch(t->ProductText.contains(ProductDocuments.text(p),t));
    }
}

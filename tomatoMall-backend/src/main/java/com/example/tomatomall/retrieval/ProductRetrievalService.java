package com.example.tomatomall.retrieval;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor
public class ProductRetrievalService {
    private final ProductCatalog catalog;
    private final ProductVectorIndex vectors;
    @Value("${rag.retrieval.min-similarity:0.54}") private double minSimilarity=0.54;
    @jakarta.annotation.PostConstruct void validateConfiguration() {
        if(!Double.isFinite(minSimilarity) || minSimilarity<0.3 || minSimilarity>1)
            throw new IllegalArgumentException("rag.retrieval.min-similarity must be between 0.3 and 1");
    }
    public record Item(ProductSnapshot product,double score,String evidence,Double vectorSimilarity) {
        public Item(ProductSnapshot product,double score,String evidence) { this(product,score,evidence,null); }
    }
    public record Result(List<Item> items,String strategy,boolean degraded,String reason,long elapsedMs) {
        @JsonProperty("outcome") public String outcome() { return degraded?"DEGRADED":items.isEmpty()?"NO_MATCH":"REQUIRES_RELEVANCE_REVIEW".equals(reason)?"CANDIDATES":"MATCHES"; }
        @JsonProperty("scoreType") public String scoreType() { return "RRF"; }
    }
    public Result candidates(ProductSearchQuery q) { return search(q,true); }
    public Result search(ProductSearchQuery q) { return search(q,false); }
    private Result search(ProductSearchQuery q,boolean forRecommendation) {
        long start=System.nanoTime();
        // A known complete title is a lookup, not an invitation to substitute another book.
        if(!q.mode().equals("vector")) {
            var exact=catalog.all().stream().filter(p->ProductText.title(p.title()).equals(ProductText.title(q.query()))).toList();
            if(!exact.isEmpty()) {
                var items=exact.stream().filter(q::accepts).sorted(Comparator.comparingInt(ProductSnapshot::id))
                    .limit(q.topK()).map(p->new Item(p,1.0/61,ProductDocuments.text(p),null)).toList();
                return new Result(items,"exact",false,items.isEmpty()?"EXACT_TITLE_FILTERED":"",elapsed(start));
            }
        }
        Map<Integer,Double> scores=new HashMap<>(), similarities=new HashMap<>();
        // Restrict the vector search itself so ineligible books cannot consume its topK.
        var products=catalog.all().stream().filter(q::accepts).toList();
        Set<Integer> eligibleIds=new HashSet<>();
        products.forEach(p->eligibleIds.add(p.id()));
        boolean degraded=false;String strategy=q.mode();
        if(!q.mode().equals("keyword") && !eligibleIds.isEmpty()) {
            try {
                for(var match:vectors.search(q.query(),200,eligibleIds)) {
                    if(!Double.isFinite(match.similarity()) || match.similarity() < -1.00001 || match.similarity()>1.00001)
                        throw new IllegalStateException("Invalid vector similarity");
                    similarities.merge(match.productId(),Math.max(-1,Math.min(1,match.similarity())),Math::max);
                }
            } catch(RuntimeException e) { similarities.clear();degraded=true;strategy="keyword"; }
        }
        var vectorRank=products.stream().filter(p->similarities.getOrDefault(p.id(),-1.0)>=(forRecommendation?0.3:minSimilarity))
            .sorted(Comparator.<ProductSnapshot>comparingDouble(p->similarities.get(p.id())).reversed().thenComparingInt(ProductSnapshot::id))
            .map(ProductSnapshot::id).toList();
        fuse(scores,vectorRank);
        List<Integer> keywordRank=List.of();
        if(!q.mode().equals("vector") || degraded) {
            keywordRank=products.stream().map(p->Map.entry(p,keywordScore(q.query(),p)))
                .filter(e->e.getValue()>0).sorted(Comparator.<Map.Entry<ProductSnapshot,Integer>>comparingInt(Map.Entry::getValue)
                    .reversed().thenComparingInt(e->e.getKey().id())).limit(200).map(e->e.getKey().id()).toList();
            fuse(scores,keywordRank);
        }
        Set<Integer> reserved=new LinkedHashSet<>();
        if(forRecommendation) {
            vectorRank.stream().limit(q.topK()/2).forEach(reserved::add);
            keywordRank.stream().limit(q.topK()-q.topK()/2).forEach(reserved::add);
            scores.entrySet().stream().sorted(Map.Entry.<Integer,Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).filter(id->!reserved.contains(id)).limit(Math.max(0,q.topK()-reserved.size())).forEach(reserved::add);
        }
        var result=products.stream().filter(p->scores.containsKey(p.id()))
            .filter(p->!forRecommendation || reserved.contains(p.id()))
            .sorted(Comparator.<ProductSnapshot>comparingDouble(p->scores.get(p.id())).reversed().thenComparingInt(ProductSnapshot::id))
            .limit(q.topK()).map(p->new Item(p,scores.get(p.id()),ProductDocuments.text(p),similarities.get(p.id()))).toList();
        return new Result(result,strategy,degraded,degraded?"VECTOR_UNAVAILABLE":result.isEmpty()?"NO_RELEVANT_ELIGIBLE_MATCH":forRecommendation?"REQUIRES_RELEVANCE_REVIEW":"",elapsed(start));
    }
    private static long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
    private static void fuse(Map<Integer,Double> scores,List<Integer> ids) {
        int rank=0;for(int id:new LinkedHashSet<>(ids)) scores.merge(id,1.0/(60+(++rank)),Double::sum);
    }
    // Small-catalog baseline: Latin words + Chinese bigrams, not BM25 or an intent parser.
    static int keywordScore(String query,ProductSnapshot p) {
        String text=ProductDocuments.text(p).toLowerCase(Locale.ROOT),title=p.title().toLowerCase(Locale.ROOT);
        String cleaned=query.toLowerCase(Locale.ROOT).replaceAll("推荐|有没有|有什么|一本|书籍|图书|适合|预算|不超过|价格|想要|请问|商品|推荐理由|学习目标|合成|测试|数据", " ");
        Set<String> tokens=new LinkedHashSet<>();
        var matcher=Pattern.compile("[a-z][a-z0-9+#.-]*|[\\p{IsHan}]+").matcher(cleaned);
        while(matcher.find()) {
            String word=matcher.group();
            if(word.charAt(0)<128) tokens.add(word);
            else for(int i=0;i<word.length()-1;i++) tokens.add(word.substring(i,i+2));
        }
        int score=0;
        for(String token:tokens) if(ProductText.contains(text,token)) score+=ProductText.contains(title,token)?3:1;
        return score;
    }
}

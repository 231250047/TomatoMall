package com.example.tomatomall.shopping;

import com.example.tomatomall.retrieval.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded, single-turn read-only tool routing. Ranking and purchase constraints stay on the server. */
@Service @RequiredArgsConstructor @Slf4j
public class BookShoppingAgent {
    private final BookToolPlanner planner;
    private final ProductCatalog catalog;
    private final GroundedBookRecommendation recommendations;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String UNAVAILABLE="购书需求解析暂不可用，请稍后重试，也可以使用商城搜索。";
    public String chat(String original) {
        if(original==null || original.isBlank() || original.length()>1000) return "问题长度应为1到1000字符。";
        String text=original.trim();
        if(Set.of("你好","您好","在吗","hello","hi","谢谢","谢谢你").contains(text.toLowerCase(Locale.ROOT))) {log.info("book_shopping route=greeting");return "你好！可以告诉我想看的主题、具体书名和每本预算。";}
        final BookPurchaseFilter filter;
        try { filter=BookPurchaseFilter.fromText(text,true); }
        catch(IllegalArgumentException e) { log.info("book_shopping route=clarify");return e.getMessage(); }
        try {
            BookToolPlanner.Call call=directLookup(text);
            if(call==null) call=planner.choose(text);
            var args=call.arguments();
            String answer=switch(call.name()) {
                case "search_books" -> search(text,args,filter);
                case "lookup_books" -> lookup(text,args,filter);
                case "clarify" -> { fields(args,Set.of("question"));String question=string(args,"question",true);yield "需要确认你的购书需求："+question; }
                default -> throw new IllegalArgumentException("Unsupported tool");
            };
            log.info("book_shopping route={} hasMinPrice={} hasMaxPrice={}",call.name(),filter.minPrice()!=null,filter.maxPrice()!=null);
            return answer;
        } catch(Exception e) { log.warn("book_shopping outcome=UNAVAILABLE type={}",e.getClass().getSimpleName());return UNAVAILABLE; }
    }
    private BookToolPlanner.Call directLookup(String text) {
        // References used as style examples must remain semantic queries.
        if(text.matches("(?s).*(类似|像.{0,20}那样|风格|相似|不要|不想|除了|排除|别推荐|读过).*")) return null;
        var isbn=Pattern.compile("(?i)ISBN[：:\\s]*([0-9Xx-]{10,17})").matcher(text);
        if(isbn.find()) return new BookToolPlanner.Call("lookup_books",JSON.createObjectNode().put("isbn",isbn.group(1)));
        var title=Pattern.compile("《([^《》]{1,200})》").matcher(text);
        if(title.find()) {
            String value=title.group(1);
            if(!title.find()) return new BookToolPlanner.Call("lookup_books",JSON.createObjectNode().put("title",value));
            return new BookToolPlanner.Call("clarify",JSON.createObjectNode().put("question","请先指定一本书；多书比较将后续支持。"));
        }
        return null;
    }
    private String search(String original,JsonNode args,BookPurchaseFilter filter) {
        fields(args,Set.of("semanticQuery"));if(!args.has("semanticQuery")) throw new IllegalArgumentException("Missing semantic query");String semantic=string(args,"semanticQuery",false);
        if(semantic.isBlank()) {
            if(filter.minPrice()==null && filter.maxPrice()==null) return "请提供想看的主题、具体书名或单本预算。";
            return facts(catalog.all().stream().filter(filter::accepts).sorted(Comparator.comparing(ProductSnapshot::price).thenComparingInt(ProductSnapshot::id)).limit(5).toList(),filter,"符合价格条件的商品（按价格升序）：\n");
        }
        // These ranking choices never come from tool arguments.
        var query=new ProductSearchQuery(semantic,null,filter.minPrice(),filter.maxPrice(),true,10,"hybrid");
        if(!Objects.equals(query.minPrice(),filter.minPrice()) || !Objects.equals(query.maxPrice(),filter.maxPrice())) throw new IllegalArgumentException("Rewritten query changed budget");
        return recommendations.recommend(original,query);
    }
    private String lookup(String original,JsonNode args,BookPurchaseFilter priceFilter) {
        fields(args,Set.of("title","author","isbn"));
        String title=string(args,"title",false),author=string(args,"author",false),isbn=string(args,"isbn",false);
        if(title.isEmpty() && author.isEmpty() && isbn.isEmpty()) throw new IllegalArgumentException("Missing identifier");
        for(String supplied:List.of(title,author)) if(!supplied.isEmpty() && !original.toLowerCase(Locale.ROOT).contains(supplied.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Ungrounded identifier");
        if(!isbn.isEmpty() && (!isbn.replace("-","").matches("[0-9]{9}[0-9Xx]|[0-9]{13}") || !original.replace("-","").toLowerCase(Locale.ROOT).contains(isbn.replace("-","").toLowerCase(Locale.ROOT)))) throw new IllegalArgumentException("Invalid ISBN");
        // A stock/price lookup is allowed to report unavailable books. Explicit stock requirements still apply.
        boolean stockOnly=original.matches("(?s).*(只要有货|仅看有货|只看有货|必须有货|有货的|现货).*" );
        var filter=new BookPurchaseFilter(priceFilter.minPrice(),priceFilter.maxPrice(),stockOnly);
        var matches=catalog.all().stream().filter(p->title.isEmpty() || normalizeTitle(p.title()).equals(normalizeTitle(title)))
            .filter(p->author.isEmpty() || values(p,"作者").stream().anyMatch(v->v.equalsIgnoreCase(author)))
            .filter(p->isbn.isEmpty() || values(p,"ISBN").stream().anyMatch(v->v.replace("-","").equalsIgnoreCase(isbn.replace("-",""))))
            .filter(filter::accepts).sorted(Comparator.comparingInt(ProductSnapshot::id)).limit(5).toList();
        return facts(matches,filter,"查到以下商品（不同版本分别列出）：\n");
    }
    private String facts(List<ProductSnapshot> candidates,BookPurchaseFilter filter,String heading) {
        var answer=new StringBuilder(heading);int count=0;
        for(var candidate:candidates) {
            var fresh=catalog.find(candidate.id());if(fresh.isEmpty() || !filter.accepts(fresh.get())) continue;
            var p=fresh.get();
            // If identity changed during lookup, do not present it as the requested book.
            if(!p.title().equals(candidate.title()) || !p.specifications().equals(candidate.specifications())) continue;
            answer.append(++count).append(". 《").append(p.title()).append("》 ￥").append(p.price().toPlainString())
                .append("，商品编号 ").append(p.id()).append("，")
                .append(!"available".equals(p.status())?"已下架":p.availableStock()>0?"有货，可售 "+p.availableStock()+" 本":"暂时缺货").append("\n");
        }
        return count==0?"没有找到满足条件的商品；可能尚未收录、书目信息缺失或价格/库存不符合要求。":answer.toString();
    }
    private static List<String> values(ProductSnapshot p,String key) {
        return p.specifications().stream().map(v->v.replace(':','：')).filter(v->v.regionMatches(true,0,key+"：",0,key.length()+1))
            .map(v->v.substring(key.length()+1).trim()).flatMap(v->Arrays.stream(v.split("[、;；]"))).map(String::trim).filter(v->!v.isEmpty()).toList();
    }
    private static String normalizeTitle(String title) {return title.trim().replace("（合成测试）","").toLowerCase(Locale.ROOT);}
    private static String string(JsonNode args,String name,boolean required) {
        var value=args.get(name);
        if(value==null) {if(required) throw new IllegalArgumentException("Missing argument");return "";}
        if(!value.isTextual() || value.asText().length()>500 || (required && value.asText().isBlank())) throw new IllegalArgumentException("Invalid argument");
        return value.asText().trim();
    }
    private static void fields(JsonNode args,Set<String> allowed) {
        if(args==null || !args.isObject()) throw new IllegalArgumentException("Invalid arguments");
        args.fieldNames().forEachRemaining(k->{if(!allowed.contains(k)) throw new IllegalArgumentException("Unsupported argument");});
    }
}

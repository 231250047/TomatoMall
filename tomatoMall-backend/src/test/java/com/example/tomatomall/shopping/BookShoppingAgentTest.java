package com.example.tomatomall.shopping;

import com.example.tomatomall.retrieval.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookShoppingAgentTest {
    BookToolPlanner planner=mock(BookToolPlanner.class);
    ProductCatalog catalog=mock(ProductCatalog.class);
    GroundedBookRecommendation recommendations=mock(GroundedBookRecommendation.class);
    BookShoppingAgent agent=new BookShoppingAgent(planner,catalog,recommendations);
    ProductSnapshot book(int id,String title,String price,String description,List<String> specs) { return new ProductSnapshot(id,title,new BigDecimal(price),"literature",description,"","available",2,specs); }
    BookToolPlanner.Call call(String name,String arguments) throws Exception {return new BookToolPlanner.Call(name,new ObjectMapper().readTree(arguments));}
    @Test void semanticIntentKeepsBeginnerInQueryAndPriceComesFromOriginal() throws Exception {
        when(planner.choose(anyString())).thenReturn(call("search_books","{\"semanticQuery\":\"Java入门书\"}"));
        when(recommendations.recommend(anyString(),any())).thenAnswer(i->{
            ProductSearchQuery q=i.getArgument(1);assertThat(q.query()).isEqualTo("Java入门书");assertThat(q.maxPrice()).isEqualByComparingTo("50");assertThat(q.level()).isNull();assertThat(q.mode()).isEqualTo("hybrid");return "推荐结果";});
        assertThat(agent.chat("Java入门书，50块以下")).isEqualTo("推荐结果");
    }
    @Test void exactBookLookupDoesNotCallModelAndUsesCurrentFacts() {
        when(catalog.all()).thenReturn(List.of(book(1,"活着","49","余华小说",List.of("作者：余华"))));
        when(catalog.find(1)).thenReturn(Optional.of(book(1,"活着","45","",List.of("作者：余华"))));
        assertThat(agent.chat("《活着》多少钱？")).contains("45","活着");verifyNoInteractions(planner,recommendations);
    }
    @Test void authorLookupDoesNotConfuseMentionsWithAuthorship() throws Exception {
        var genuine=book(1,"活着","49","",List.of("作者：余华"));
        when(catalog.all()).thenReturn(List.of(genuine,book(2,"余华研究","30","余华",List.of("作者：别人"))));
        when(catalog.find(1)).thenReturn(Optional.of(genuine));
        when(planner.choose(anyString())).thenReturn(call("lookup_books","{\"author\":\"余华\"}"));
        assertThat(agent.chat("想看余华写的书，50元以下")).contains("活着").doesNotContain("余华研究");verifyNoInteractions(recommendations);
    }
    @Test void priceOnlyDoesNotEmbedAnEmptyQuery() throws Exception {
        when(planner.choose(anyString())).thenReturn(call("search_books","{\"semanticQuery\":\"\"}"));
        var p=book(1,"诗集","39","",List.of());when(catalog.all()).thenReturn(List.of(p));when(catalog.find(1)).thenReturn(Optional.of(p));
        assertThat(agent.chat("50块以下的书有哪些")).contains("诗集");verifyNoInteractions(recommendations);
    }
    @Test void injectedWeightIsRejectedAndCannotChangeRetrieval() throws Exception {
        when(planner.choose(anyString())).thenReturn(call("search_books","{\"semanticQuery\":\"Java\",\"vectorWeight\":1,\"maxPrice\":999}"));
        assertThat(agent.chat("Java书50元以下")).contains("解析");verifyNoInteractions(recommendations);
    }
    @Test void ambiguousBudgetIsClarifiedBeforeAnyModelCall() {
        assertThat(agent.chat("Java书，50元左右")).contains("预算");verifyNoInteractions(planner,recommendations);
    }
    @Test void exactTitleNotFoundDoesNotSubstituteSimilarBook() {
        when(catalog.all()).thenReturn(List.of(book(1,"活着续作","40","",List.of())));
        assertThat(agent.chat("《活着》多少钱")).contains("没有找到").doesNotContain("活着续作");verifyNoInteractions(planner,recommendations);
    }
    @Test void greetingIsFreeAndHasNoTools() {assertThat(agent.chat("你好")).contains("你好");verifyNoInteractions(planner,catalog,recommendations);}
    @Test void excludedTitleDoesNotUseExactLookupShortcut() throws Exception {
        when(planner.choose(anyString())).thenReturn(call("search_books","{\"semanticQuery\":\"余华其他作品，不要活着\"}"));
        when(recommendations.recommend(anyString(),any())).thenReturn("其他作品");
        assertThat(agent.chat("不要《活着》，推荐余华其他作品")).isEqualTo("其他作品");
        verify(planner).choose(anyString());verifyNoInteractions(catalog);
    }
    @Test void lookupBudgetIsRecheckedAfterPriceChange() {
        when(catalog.all()).thenReturn(List.of(book(1,"活着","49","",List.of())));
        when(catalog.find(1)).thenReturn(Optional.of(book(1,"活着","55","",List.of())));
        assertThat(agent.chat("《活着》，50元以下")).contains("没有找到").doesNotContain("55");
    }
    @Test void rewrittenQueryCannotInventBudget() throws Exception {
        when(planner.choose(anyString())).thenReturn(call("search_books","{\"semanticQuery\":\"Java书预算999元\"}"));
        assertThat(agent.chat("推荐Java书")).contains("解析");verifyNoInteractions(recommendations);
    }
}

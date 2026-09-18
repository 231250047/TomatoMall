package com.example.tomatomall.retrieval;
import com.example.tomatomall.service.DeepSeekService;
import com.example.tomatomall.shopping.GroundedBookRecommendation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class GroundedChatTest {
    ProductRetrievalService retrieval=mock(ProductRetrievalService.class);
    DeepSeekService model=mock(DeepSeekService.class);
    ProductCatalog catalog=mock(ProductCatalog.class);
    GroundedBookRecommendation chat=new GroundedBookRecommendation(model,retrieval,catalog);
    String ask(String message) { return chat.recommend(message,ProductSearchQuery.of(message,10)); }
    void prepare() {
        var p=ProductRetrievalTest.book(1,"Java实践","59",10,0,"available");
        when(retrieval.candidates(any())).thenReturn(new ProductRetrievalService.Result(List.of(new ProductRetrievalService.Item(p,.01,ProductDocuments.text(p))),"hybrid",false,"",1));
        when(catalog.find(1)).thenReturn(Optional.of(p));
    }
    @Test void rejectsInventedProductWithoutPromotingUnverifiedCandidates() {
        prepare();when(model.chatWithPrompt(anyString())).thenReturn("{\"items\":[{\"productId\":999,\"evidence\":\"虚构畅销书\"}]}");
        assertThat(ask("Java书")).contains("推荐暂不可用").doesNotContain("Java实践","999","虚构畅销书");
    }
    @Test void rechecksAfterModelCallSoDelistedBookIsNotRecommended() {
        prepare();when(model.chatWithPrompt(anyString())).thenReturn("{\"items\":[{\"productId\":1,\"evidence\":\"面向初学者\"}]}");
        when(catalog.find(1)).thenReturn(Optional.of(ProductRetrievalTest.book(1,"Java实践","59",10,0,"unavailable")));
        assertThat(ask("Java书")).doesNotContain("Java实践").contains("没有");
    }
    @Test void noResultsDoesNotInviteModelToInventBooks() {
        when(retrieval.candidates(any())).thenReturn(new ProductRetrievalService.Result(List.of(),"keyword",true,"VECTOR_UNAVAILABLE",1));
        assertThat(ask("Java书")).contains("没有");verifyNoInteractions(model);
    }
    @Test void modelNoMatchDoesNotFallBackToRetrievedBooks() {
        prepare();when(model.chatWithPrompt(anyString())).thenReturn("{\"items\":[]}");
        assertThat(ask("商品中有量子芯片制造工艺专著吗？")).contains("没有找到足够匹配").doesNotContain("Java实践","推荐暂不可用");
        verifyNoInteractions(catalog);
    }
    @Test void validSelectionStillUsesLiveFacts() {
        prepare();when(model.chatWithPrompt(anyString())).thenReturn("{\"items\":[{\"productId\":1,\"evidence\":\"Java实践\"}]}");
        assertThat(ask("Java书")).contains("Java实践","59","商品介绍依据").doesNotContain("推荐暂不可用");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"", " ", "not-json", "{}", "{\"items\":null}", "{\"items\":[{\"productId\":1,\"evidence\":\"编造依据\"}]}"})
    void emptyOrMalformedOutputIsUnavailableNotNoMatch(String output) {
        prepare();when(model.chatWithPrompt(anyString())).thenReturn(output);
        assertThat(ask("Java书")).contains("推荐暂不可用").doesNotContain("Java实践","没有找到足够匹配");
    }
    @Test void callFailureDoesNotExposeExceptionOrRecommendCandidates() {
        prepare();when(model.chatWithPrompt(anyString())).thenThrow(new RuntimeException("secret-provider-body"));
        assertThat(ask("Java书")).contains("推荐暂不可用").doesNotContain("Java实践","secret-provider-body");
    }

    @Test void selectionReceivesTrustedPriceForBudgetJudgement() {
        prepare();when(model.chatWithPrompt(anyString())).thenAnswer(invocation->{
            String prompt=invocation.getArgument(0);
            assertThat(prompt).contains("\"price\":59", "已按价格和库存过滤");
            return "{\"items\":[{\"productId\":1,\"evidence\":\"Java实践\"}]}";
        });
        assertThat(ask("Java书低于80元")).contains("Java实践");
    }
    @Test void changedSourceCannotRemainARecommendationWithoutEvidence() {
        prepare();when(model.chatWithPrompt(anyString())).thenReturn("{\"items\":[{\"productId\":1,\"evidence\":\"Java实践\"}]}");
        var changed=new ProductSnapshot(1,"Java实践",new java.math.BigDecimal("59"),"science","改为高阶源码材料","不是入门教材","available",10,List.of());
        when(catalog.find(1)).thenReturn(Optional.of(changed));
        assertThat(ask("Java入门书")).doesNotContain("Java实践").contains("没有");
    }
}

package com.example.tomatomall.retrieval;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRetrievalTest {
    ProductCatalog catalog = mock(ProductCatalog.class);
    ProductVectorIndex index = mock(ProductVectorIndex.class);
    ProductRetrievalService service = new ProductRetrievalService(catalog, index);
    static ProductSnapshot book(int id, String title, String price, int amount, int frozen, String status) {
        return new ProductSnapshot(id, title, new BigDecimal(price), "science", "Java项目实践", "面向初学者", status, amount-frozen, List.of());
    }
    @Test void filtersLiveBudgetStockAndStatusAndDeduplicates() {
        var good=book(1,"Java项目实践","80",10,0,"available");
        when(catalog.all()).thenReturn(List.of(good,book(2,"Java进阶","80.01",10,0,"available"),book(3,"Java入门","60",10,10,"available"),book(4,"Java源码","70",10,0,"unavailable")));
        when(index.search(anyString(),anyInt(),anySet())).thenReturn(List.of(4,3,2,1,1).stream().map(id->new ProductVectorIndex.Match(id,.8)).toList());
        var result=service.search(new ProductSearchQuery("Java",null,null,new BigDecimal("80"),true,5,"hybrid"));
        assertThat(result.items()).extracting(i->i.product().id()).containsExactly(1);
        assertThat(result.degraded()).isFalse();
        verify(index).search("Java",200,Set.of(1));
    }
    @Test void noEligibleProductsReturnsNoMatchWithoutCallingVectorIndex() {
        when(catalog.all()).thenReturn(List.of(book(1,"Java实践","90",10,0,"available")));
        var result=service.candidates(new ProductSearchQuery("后端开发",null,null,new BigDecimal("80"),true,5,"hybrid"));
        assertThat(result.items()).isEmpty();
        assertThat(result.outcome()).isEqualTo("NO_MATCH");
        verifyNoInteractions(index);
    }
    @Test void failedVectorSearchHasExplicitKeywordFallback() {
        when(catalog.all()).thenReturn(List.of(book(1,"Java实践","59",10,0,"available")));
        when(index.search(anyString(),anyInt(),anySet())).thenThrow(new IllegalStateException("offline"));
        var result=service.search(ProductSearchQuery.of("Java",3));
        assertThat(result.items()).hasSize(1);
        assertThat(result.degraded()).isTrue();
        assertThat(result.strategy()).isEqualTo("keyword");
    }
    @Test void keywordBaselineNeverCallsEmbeddingAndUnrelatedQueryReturnsEmpty() {
        when(catalog.all()).thenReturn(List.of(book(1,"Java实践","59",10,0,"available")));
        var result=service.search(new ProductSearchQuery("古典音乐",null,null,null,true,5,"keyword"));
        assertThat(result.items()).isEmpty(); verifyNoInteractions(index);
    }
    @Test void genericCatalogBoilerplateCannotMatchUnrelatedSubject() {
        var p=new ProductSnapshot(1,"Java实践",new BigDecimal("59"),"science","Java项目实战","本商品为合成测试数据","available",20,List.of());
        when(catalog.all()).thenReturn(List.of(p));
        assertThat(service.search(new ProductSearchQuery("商品中有量子芯片制造工艺专著吗？","science",null,null,true,5,"keyword")).items()).isEmpty();
    }
    @Test void validatesBoundsAndExtractsExplicitSingleBookBudget() {
        assertThatThrownBy(()->ProductSearchQuery.of("Java",201)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ProductSearchQuery.of("推荐Java书，价格不超过80元",5).maxPrice()).isEqualByComparingTo("80");
        assertThatThrownBy(()->ProductSearchQuery.of("买两本书总预算100元",5)).hasMessageContaining("总预算");
    }
    @Test void strictlyBelowBudgetExcludesBoundary() {
        assertThat(ProductSearchQuery.of("Java价格低于80元",5).maxPrice()).isEqualByComparingTo("79.99");
        assertThat(ProductSearchQuery.of("不低于80元的Java书",5).maxPrice()).isNull();
        assertThat(ProductSearchQuery.of("不低于80元的Java书",5).minPrice()).isEqualByComparingTo("80");
    }
    @Test void documentIdentityStableAndTextOmitsDynamicFacts() {
        var a=book(12,"Java实践","59",10,0,"available");
        var b=book(12,"Java实践","89",2,1,"available");
        var d=ProductDocuments.document(a);
        assertThat(d.getId()).isEqualTo(ProductDocuments.document(b).getId());
        assertThatCode(()->UUID.fromString(d.getId())).doesNotThrowAnyException();
        assertThat(d.getContent()).doesNotContain("59","89","库存");
        assertThat(ProductDocuments.hash(a)).isEqualTo(ProductDocuments.hash(b));
    }

    @Test void javaTokenDoesNotMatchJavaScriptOnlyBook() {
        var js=new ProductSnapshot(1,"JavaScript基础",new BigDecimal("59"),"science","前端语法","数组与函数","available",10,List.of());
        when(catalog.all()).thenReturn(List.of(js));
        assertThat(service.search(new ProductSearchQuery("Java",null,null,null,true,5,"keyword")).items()).isEmpty();
    }

    @Test void belowThresholdVectorMustNotFillTopK() {
        when(catalog.all()).thenReturn(List.of(book(1,"Java实践","59",10,0,"available")));
        when(index.search(anyString(),anyInt(),anySet())).thenReturn(List.of(new ProductVectorIndex.Match(1,.4)));
        assertThat(service.search(new ProductSearchQuery("量子芯片",null,null,null,true,5,"vector")).items()).isEmpty();
    }
    @Test void exactTitleDoesNotRecommendOtherTitlesWhenItsConstraintsFail() {
        when(catalog.all()).thenReturn(List.of(book(1,"Java实践","90",10,0,"available"),book(2,"Java其他书","50",10,0,"available")));
        when(index.search(anyString(),anyInt(),anySet())).thenReturn(List.of(new ProductVectorIndex.Match(2,.9)));
        assertThat(service.search(new ProductSearchQuery("Java实践",null,null,new BigDecimal("80"),true,5,"hybrid")).items()).isEmpty();
    }
    @Test void exactTitleRanksFirstOverOtherVectorMatches() {
        when(catalog.all()).thenReturn(List.of(book(1,"Java实践","59",10,0,"available"),book(2,"Java其他书","59",10,0,"available")));
        when(index.search(anyString(),anyInt(),anySet())).thenReturn(List.of(new ProductVectorIndex.Match(2,.9),new ProductVectorIndex.Match(1,.7)));
        assertThat(service.search(ProductSearchQuery.of("Java实践",5)).items()).extracting(i->i.product().id()).containsExactly(1);
    }

    @Test void explicitLevelRejectsMissingOrConflictingSpecification() {
        var q=new ProductSearchQuery("Java",null,null,null,true,5,"keyword","入门",List.of());
        assertThat(q.accepts(book(1,"Java","59",10,0,"available"))).isFalse();
        var conflicting=new ProductSnapshot(1,"Java",new BigDecimal("59"),"science","基础","变量","available",10,List.of("适用难度：入门","适用难度：高级"));
        assertThat(q.accepts(conflicting)).isFalse();
    }
    @Test void emptyAndUnavailableVectorHaveDifferentOutcomes() {
        when(catalog.all()).thenReturn(List.of(book(1,"Java实践","59",10,0,"available")));
        when(index.search(anyString(),anyInt(),anySet())).thenReturn(List.of());
        var q=new ProductSearchQuery("完全陌生主题",null,null,null,true,5,"vector");
        assertThat(service.search(q).outcome()).isEqualTo("NO_MATCH");
        when(index.search(anyString(),anyInt(),anySet())).thenThrow(new IllegalStateException("offline"));
        assertThat(service.search(q).outcome()).isEqualTo("DEGRADED");
    }
    @Test void vectorSimilarityIsExposedSeparatelyFromRankScore() {
        when(catalog.all()).thenReturn(List.of(book(1,"Java实践","59",10,0,"available")));
        when(index.search(anyString(),anyInt(),anySet())).thenReturn(List.of(new ProductVectorIndex.Match(1,.8)));
        var item=service.search(new ProductSearchQuery("后端学习",null,null,null,true,5,"vector")).items().get(0);
        assertThat(item.vectorSimilarity()).isEqualTo(.8);
        assertThat(item.score()).isCloseTo(1.0/61,org.assertj.core.data.Offset.offset(.00001));
    }

    @Test void recommendationPoolPreservesWeakVectorEvidenceForModelReview() {
        when(catalog.all()).thenReturn(List.of(book(1,"事件补偿","59",10,0,"available")));
        when(index.search(anyString(),anyInt(),anySet())).thenReturn(List.of(new ProductVectorIndex.Match(1,.5)));
        var q=new ProductSearchQuery("保存后崩溃如何补发",null,null,null,true,10,"hybrid");
        assertThat(service.search(q).items()).isEmpty();
        assertThat(service.candidates(q).items()).extracting(i->i.product().id()).containsExactly(1);
    }
}

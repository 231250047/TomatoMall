package com.example.tomatomall.retrieval;
import com.example.tomatomall.controller.VectorStoreTestController;
import com.example.tomatomall.Util.SecurityUtil;
import com.example.tomatomall.service.ChatService;
import com.example.tomatomall.po.Account;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RetrievalControllerTest {
    ProductRetrievalService retrieval=mock(ProductRetrievalService.class);
    ProductIndexMaintenance maintenance=mock(ProductIndexMaintenance.class);
    SecurityUtil security=mock(SecurityUtil.class);
    VectorStoreTestController controller=new VectorStoreTestController(retrieval,maintenance,mock(ChatService.class),security);
    @Test void ordinaryUserCannotStartIndexBuild() {
        Account user=new Account();user.setRole("user");when(security.getCurrentAccount()).thenReturn(user);
        assertThatThrownBy(()->controller.build()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(maintenance);
    }
    @Test void invalidParameterTypeFailsValidation() {
        assertThatThrownBy(()->controller.search(Map.of("query",123))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(retrieval);
    }
    @Test void explicitNullOptionalValuesUseDefaults() {
        Map<String,Object> body=new HashMap<>();body.put("query","Java");body.put("topK",null);body.put("mode",null);
        controller.search(body);
        verify(retrieval).search(ProductSearchQuery.of("Java",5));
    }

    @Test void rejectsUnknownFieldsRatherThanSilentlyIgnoringAgentConstraints() {
        assertThatThrownBy(()->controller.search(Map.of("query","Java","author","某作者")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("author");
    }
    @Test void levelAndExclusionsReachExecutableFilters() {
        controller.search(Map.of("query","Java","level","入门","excludedTopics",List.of("源码")));
        var captured=org.mockito.ArgumentCaptor.forClass(ProductSearchQuery.class);
        verify(retrieval).search(captured.capture());
        var advanced=new ProductSnapshot(1,"Java源码",new java.math.BigDecimal("59"),"science","Java","源码","available",10,List.of("适用难度：高级"));
        var beginner=new ProductSnapshot(2,"Java基础",new java.math.BigDecimal("59"),"science","Java","变量","available",10,List.of("适用难度：入门"));
        assertThat(captured.getValue().accepts(advanced)).isFalse();
        assertThat(captured.getValue().accepts(beginner)).isTrue();
    }
    @Test void rejectsBadStructuredConstraints() {
        for(var body:List.of(Map.of("query","Java","level","随便"),Map.of("query","Java","excludedTopics","源码"),
                Map.of("query","Java","tag","unknown"),Map.of("query","Java","maxPrice","80.001")))
            assertThatThrownBy(()->controller.search(new HashMap<String,Object>(body))).isInstanceOf(IllegalArgumentException.class);
    }
}

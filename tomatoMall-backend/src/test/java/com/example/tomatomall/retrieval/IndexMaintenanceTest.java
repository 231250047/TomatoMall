package com.example.tomatomall.retrieval;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class IndexMaintenanceTest {
    @Test void backgroundReconciliationDoesNotBlockSchedulerOrQueueDuplicateJobs() throws Exception {
        ProductVectorIndex index=mock(ProductVectorIndex.class);ProductCatalog catalog=mock(ProductCatalog.class);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(index.indexedIds()).thenReturn(Set.of(1));when(catalog.all()).thenReturn(List.of());
        doAnswer(a->{entered.countDown();release.await(3,TimeUnit.SECONDS);return null;}).when(index).sync(1);
        var maintenance=new ProductIndexMaintenance(index,catalog);
        ReflectionTestUtils.setField(maintenance,"enabled",true);
        try {
            assertThat(maintenance.reconcile().get("status")).isEqualTo("ACCEPTED");
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(maintenance.reconcile().get("status")).isEqualTo("ALREADY_RUNNING");
            maintenance.scheduledReconcile();
        } finally { release.countDown();maintenance.close(); }
        verify(index,times(1)).sync(1);
    }
}

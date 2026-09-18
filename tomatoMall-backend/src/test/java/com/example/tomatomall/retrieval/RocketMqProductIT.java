package com.example.tomatomall.retrieval;

import com.example.tomatomall.po.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in real broker test:
 * mvn -Dtest=RocketMqProductIT -Drag.test.mq-endpoints=localhost:18081 test
 * Provision NORMAL topic product-changed-rag-test and group product-index-rag-test first.
 * Run one instance at a time because the consumer group is deliberately fixed and isolated.
 *
 * This exercises the real transport, broker delivery and retry after ConsumeResult.FAILURE.
 * The index is a controlled failure double: this does NOT claim MySQL + PostgreSQL end-to-end
 * consistency, embedding quality, or exactly-once delivery. PostgreSQL has separate integration tests.
 */
@EnabledIfSystemProperty(named="rag.test.mq-endpoints",matches=".+")
class RocketMqProductIT {
    @Test void failedIndexSyncIsRedeliveredAndThenSucceeds() throws Exception {
        int productId=ThreadLocalRandom.current().nextInt(1,Integer.MAX_VALUE);
        AtomicInteger attempts=new AtomicInteger();
        CountDownLatch firstFailure=new CountDownLatch(1);
        CountDownLatch recovered=new CountDownLatch(1);
        ProductVectorIndex index=new ProductVectorIndex() {
            @Override public void sync(int id) {
                // A previous run may leave messages in this dedicated test group. They cannot
                // satisfy this run's assertions; productId is fresh for each invocation.
                if(id!=productId) return;
                if(attempts.incrementAndGet()==1) {
                    firstFailure.countDown();
                    throw new IllegalStateException("Injected first-attempt index failure");
                }
                recovered.countDown();
            }
            @Override public List<ProductVectorIndex.Match> search(String query,int limit) { throw new UnsupportedOperationException(); }
            @Override public List<ProductVectorIndex.Match> search(String query,int limit,Set<Integer> eligibleIds) { throw new UnsupportedOperationException(); }
            @Override public Set<Integer> indexedIds() { return Set.of(); }
            @Override public Map<String,Object> status() { return Map.of("status","TEST_DOUBLE"); }
        };
        DefaultListableBeanFactory beans=new DefaultListableBeanFactory();
        beans.registerSingleton("testProductVectorIndex",index);
        RocketMqProductTransport transport=new RocketMqProductTransport(beans.getBeanProvider(ProductVectorIndex.class));
        ReflectionTestUtils.setField(transport,"endpoints",System.getProperty("rag.test.mq-endpoints"));
        ReflectionTestUtils.setField(transport,"topic","product-changed-rag-test");
        ReflectionTestUtils.setField(transport,"group","product-index-rag-test");
        try {
            transport.connect();
            assertThat(ReflectionTestUtils.getField(transport,"producer"))
                .as("Producer must connect to the real broker").isNotNull();
            assertThat(ReflectionTestUtils.getField(transport,"consumer"))
                .as("Consumer must subscribe to the dedicated NORMAL topic").isNotNull();
            OutboxEvent event=OutboxEvent.create(OutboxEvent.Kind.PRODUCT_CHANGED,productId,new Date());
            transport.publish(event); // Exactly one send; the second sync must come from delivery retry.
            assertThat(firstFailure.await(30,TimeUnit.SECONDS))
                .as("Real broker must deliver the event and trigger the injected failure").isTrue();
            assertThat(recovered.await(60,TimeUnit.SECONDS))
                .as("Failed consumption must be retried without a second publish").isTrue();
            assertThat(attempts.get()).as("At least one failed sync followed by successful sync").isGreaterThanOrEqualTo(2);
        } finally {
            transport.close();
        }
    }
}

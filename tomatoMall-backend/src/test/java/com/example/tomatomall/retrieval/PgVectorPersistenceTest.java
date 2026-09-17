package com.example.tomatomall.retrieval;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL/pgvector with deterministic test vectors: NOT a semantic quality evaluation. */
@EnabledIfSystemProperty(named="rag.test.jdbc-url",matches=".+_rag_test(?:\\?.*)?")
class PgVectorPersistenceTest {
    ProductCatalog catalog;
    AtomicReference<ProductSnapshot> product;
    TestEmbedding model;
    JdbcTemplate jdbc;
    PgProductVectorIndex index;
    String url;
    @BeforeEach void setup() throws Exception {
        url=System.getProperty("rag.test.jdbc-url");
        jdbc=new JdbcTemplate(new DriverManagerDataSource(url,System.getProperty("rag.test.user","tomatomall"),System.getProperty("rag.test.password","")));
        jdbc.execute("DROP TABLE IF EXISTS vector_store"); jdbc.execute("DROP TABLE IF EXISTS rag_index_config");
        String sql=Files.readString(Path.of("../infra/rag/init.sql")).replace("vector(1024)","vector(3)");
        jdbc.execute(sql);
        catalog=mock(ProductCatalog.class);product=new AtomicReference<>(ProductRetrievalTest.book(1,"Java实践","59",10,0,"available"));
        when(catalog.find(1)).thenAnswer(a->Optional.ofNullable(product.get()));
        model=new TestEmbedding();index=create(model,"test-model");
    }
    PgProductVectorIndex create(TestEmbedding m,String name) {
        return new PgProductVectorIndex(url,System.getProperty("rag.test.user","tomatomall"),System.getProperty("rag.test.password",""),m,3,name,"test-catalog",catalog);
    }
    @AfterEach void close() { if(index!=null) index.close(); }
    @Test void eligibleBookSurvivesMoreThanTwoHundredHigherSimilarityOverBudgetBooks() {
        index.sync(1);
        // Eligible book has a lower similarity than every expensive book.
        jdbc.update("UPDATE vector_store SET embedding='[1,0.6,0.1]'::vector WHERE id=?::uuid",ProductDocuments.id(1));
        List<ProductSnapshot> books=new ArrayList<>();books.add(product.get());
        for(int id=2;id<=206;id++) {
            books.add(ProductRetrievalTest.book(id,"昂贵专业书"+id,"200",10,0,"available"));
            jdbc.update("INSERT INTO vector_store(id,content,metadata,embedding) VALUES (?::uuid,?,?::jsonb,'[1,0.2,0.1]'::vector)",
                ProductDocuments.id(id),"昂贵专业书", "{\"productId\":"+id+"}");
        }
        when(catalog.all()).thenReturn(books);
        assertThat(index.search("事件补偿",200)).extracting(ProductVectorIndex.Match::productId).doesNotContain(1);
        assertThat(index.search("事件补偿",1,Set.of(1))).extracting(ProductVectorIndex.Match::productId).containsExactly(1);
        assertThat(index.search("事件补偿",200,Set.of(1))).extracting(ProductVectorIndex.Match::productId).containsExactly(1);
        assertThat(index.search("事件补偿",200,Set.of(1,2))).extracting(ProductVectorIndex.Match::productId).containsExactly(2,1);
        var query=new ProductSearchQuery("事件补偿",null,null,new java.math.BigDecimal("80"),true,5,"vector");
        var result=new ProductRetrievalService(catalog,index).candidates(query);
        assertThat(result.degraded()).isFalse();
        assertThat(result.items()).extracting(item->item.product().id()).containsExactly(1);
    }
    @Test void emptyEligibleSetDoesNotCallEmbeddingOrRequireBoundIndex() {
        model.fail=true;
        assertThat(index.search("Java",5,Set.of())).isEmpty();
        assertThat(model.calls.get()).isZero();
    }
    @Test void committedVectorsSurviveNewStoreInstanceAndDuplicateSkipsEmbedding() {
        index.sync(1); assertThat(model.calls.get()).isEqualTo(1);
        index.sync(1); assertThat(model.calls.get()).isEqualTo(1);
        index.close();index=create(model,"test-model");
        assertThat(index.search("Java",5)).extracting(ProductVectorIndex.Match::productId).containsExactly(1);
        assertThat(index.search("Java",5).get(0).similarity()).isCloseTo(1.0,org.assertj.core.data.Offset.offset(0.00001));
        assertThat(index.indexedIds()).containsExactly(1);
        assertThat(index.status().get("status")).isEqualTo("UP");
    }
    @Test void delayedEventReadsLatestStateAndUnlistingDeletes() {
        index.sync(1);
        product.set(ProductRetrievalTest.book(1,"数据库进阶","79",10,0,"available"));index.sync(1);index.sync(1);
        assertThat(jdbc.queryForObject("SELECT content FROM vector_store",String.class)).contains("数据库进阶").doesNotContain("书名：Java实践");
        assertThat(model.calls.get()).isEqualTo(2);
        product.set(ProductRetrievalTest.book(1,"数据库进阶","79",10,0,"unavailable"));index.sync(1);
        assertThat(index.indexedIds()).isEmpty();
    }
    @Test void changedDuringEmbeddingRollsBackAndRetryPublishesLatest() {
        index.sync(1);
        product.set(ProductRetrievalTest.book(1,"Java新版本","59",10,0,"available"));
        model.during=()->product.set(ProductRetrievalTest.book(1,"Java最新版本","59",10,0,"available"));
        assertThatThrownBy(()->index.sync(1)).hasMessageContaining("changed");
        assertThat(jdbc.queryForObject("SELECT content FROM vector_store",String.class)).contains("书名：Java实践");
        model.during=()->{};index.sync(1);
        assertThat(jdbc.queryForObject("SELECT content FROM vector_store",String.class)).contains("Java最新版本");
    }
    @Test void providerFailureKeepsPreviousDurableVector() {
        index.sync(1);product.set(ProductRetrievalTest.book(1,"修改后的书","59",10,0,"available"));model.fail=true;
        assertThatThrownBy(()->index.sync(1)).hasMessageContaining("provider unavailable");
        assertThat(jdbc.queryForObject("SELECT content FROM vector_store",String.class)).contains("Java实践");
    }
    @Test void missingModelMarkerCannotAdoptAnExistingIndex() {
        index.sync(1);jdbc.update("DELETE FROM rag_index_config");
        assertThatThrownBy(()->index.sync(1)).hasMessageContaining("unbound");
        assertThat(index.indexedIds()).containsExactly(1);
    }
    @Test void incompatibleModelIsRejectedWithoutOverwriting() {
        index.sync(1);
        try(var incompatible=create(model,"different-model")) {
            assertThatThrownBy(()->incompatible.sync(1)).hasMessageContaining("mismatch");
            assertThatThrownBy(()->incompatible.search("Java",5)).hasMessageContaining("mismatch");
        }
        assertThat(index.indexedIds()).containsExactly(1);
    }
    @Test void independentWorkersSerializeBeforeReadingLatestProduct() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        model.during=()->{entered.countDown();try { if(!release.await(3,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); } catch(InterruptedException e) {throw new RuntimeException(e);} };
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try(var second=create(new TestEmbedding(),"test-model")) {
            var first=executor.submit(()->index.sync(1));assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            var next=executor.submit(()->second.sync(1));
            product.set(ProductRetrievalTest.book(1,"并发更新后的商品","60",10,0,"available"));release.countDown();
            assertThatThrownBy(()->first.get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            next.get(5,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT content FROM vector_store",String.class)).contains("并发更新后的商品");
            assertThat(index.indexedIds()).containsExactly(1);
        } finally { release.countDown();executor.shutdownNow(); }
    }
    static class TestEmbedding implements EmbeddingModel {
        AtomicInteger calls=new AtomicInteger();volatile boolean fail;volatile Runnable during=()->{};
        public EmbeddingResponse call(EmbeddingRequest request) {
            if(fail) throw new IllegalStateException("provider unavailable");
            calls.incrementAndGet();during.run();
            List<Embedding> values=new ArrayList<>();
            for(int i=0;i<request.getInstructions().size();i++) values.add(new Embedding(new float[]{1,.2f,.1f},i));
            return new EmbeddingResponse(values);
        }
        public float[] embed(Document doc) { return embed(doc.getContent()); }
        public int dimensions() { return 3; }
    }
}

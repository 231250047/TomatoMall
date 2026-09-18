package com.example.tomatomall.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Read-only keyword baseline against the actual fixture MySQL catalog. Never invokes paid APIs. */
@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=none","spring.jpa.open-in-view=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=RagCatalogIT.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named="rag.test.mysql-url",matches=".+_rag_eval(?:\\?.*)?")
class RagCatalogIT {
    @Configuration @EntityScan("com.example.tomatomall.po") @EnableJpaRepositories("com.example.tomatomall.repository")
    @Import(ProductCatalog.class) static class Config {}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getProperty("rag.test.mysql-url"));
        r.add("spring.datasource.username",()->System.getProperty("rag.test.mysql-user","root"));
        r.add("spring.datasource.password",()->System.getProperty("rag.test.mysql-password",""));
        r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
    @Autowired ProductCatalog catalog;
    @Test @EnabledIfSystemProperty(named="rag.test.catalog-pg-url",matches=".+_rag_test(?:\\?.*)?")
    void mysqlCatalogAndPgVectorUseIndependentTransactions() throws Exception {
        String pgUrl=System.getProperty("rag.test.catalog-pg-url");
        var pg=new JdbcTemplate(new DriverManagerDataSource(pgUrl,"tomatomall",""));
        pg.execute("DROP TABLE IF EXISTS vector_store");pg.execute("DROP TABLE IF EXISTS rag_index_config");
        pg.execute(Files.readString(Path.of("../infra/rag/init.sql")).replace("vector(1024)","vector(3)"));
        var embedding=new PgVectorPersistenceTest.TestEmbedding();
        try(var index=new PgProductVectorIndex(pgUrl,"tomatomall","",embedding,3,"test-model","fixture-eval",catalog)) {
            for(var p:catalog.all()) index.sync(p.id());
            long active=catalog.all().stream().filter(p->"available".equals(p.status())).count();
            assertThat(index.indexedIds()).hasSize((int)active);
            int calls=embedding.calls.get();
            for(var p:catalog.all()) index.sync(p.id());
            assertThat(embedding.calls.get()).isEqualTo(calls);
            var result=new ProductRetrievalService(catalog,index).search(ProductSearchQuery.of("Java不超过80元",5));
            assertThat(result.degraded()).isFalse();assertThat(result.items()).isNotEmpty();
            assertThat(result.items()).allMatch(i->i.product().price().compareTo(new BigDecimal("80"))<=0 && i.product().availableStock()>0);
        }
    }
    @Test void collectKeywordBaselineFromSharedProductionRetrievalService() throws Exception {
        var all=catalog.all();assertThat(all).hasSize(100);
        assertThat(all).allMatch(p->p.specifications().size()==4);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(System.getProperty("rag.test.mysql-url"),System.getProperty("rag.test.mysql-user","root"),System.getProperty("rag.test.mysql-password","")));
        Map<Integer,String> keys=new HashMap<>();
        jdbc.query("SELECT product_id,fixture_key FROM rag_fixture_products",rs->{keys.put(rs.getInt(1),rs.getString(2));});
        ProductVectorIndex unused=org.mockito.Mockito.mock(ProductVectorIndex.class);
        var retrieval=new ProductRetrievalService(catalog,unused);var json=new ObjectMapper();
        var queries=json.readTree(Files.readString(Path.of("../infra/rag/fixtures/queries.json")));
        List<Map<String,Object>> predictions=new ArrayList<>();
        for(var q:queries) {
            var c=q.path("constraints");
            var result=retrieval.search(new ProductSearchQuery(q.path("query").asText(),c.has("category")?c.get("category").asText():null,null,
                c.has("max_price")?new BigDecimal(c.get("max_price").asText()):null,c.path("in_stock").asBoolean(true),5,"keyword"));
            predictions.add(Map.of("query_id",q.path("query_id").asText(),"fixture_keys",result.items().stream().map(i->keys.get(i.product().id())).toList(),"elapsed_ms",result.elapsedMs(),"strategy",result.strategy()));
            assertThat(result.degraded()).isFalse();
            assertThat(result.items()).allMatch(i->"available".equals(i.product().status()) && i.product().availableStock()>0);
        }
        org.mockito.Mockito.verifyNoInteractions(unused);
        Path output=Path.of(System.getProperty("rag.test.predictions","/private/tmp/tomatomall-rag-keyword-predictions.json"));
        Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(predictions));
    }
}

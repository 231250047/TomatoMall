package com.example.tomatomall.retrieval;

import com.zaxxer.hikari.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.*;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Owns a separate PostgreSQL pool; it is deliberately NOT a Spring DataSource bean (MySQL stays primary). */
public class PgProductVectorIndex implements ProductVectorIndex, AutoCloseable {
    private final HikariDataSource pool;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PgVectorStore store;
    private final ProductCatalog catalog;
    private final String fingerprint;
    private final int dimensions;
    public PgProductVectorIndex(String url,String user,String password,EmbeddingModel model,int dimensions,
                                String modelName,String namespace,ProductCatalog catalog) {
        if(dimensions<1 || dimensions>16000) throw new IllegalArgumentException("Invalid embedding dimension");
        HikariConfig c=new HikariConfig();c.setJdbcUrl(url);c.setUsername(user);c.setPassword(password);
        c.setPoolName("product-pgvector");c.setMaximumPoolSize(4);c.setMinimumIdle(0);c.setConnectionTimeout(3000);
        c.setInitializationFailTimeout(-1);c.addDataSourceProperty("connectTimeout",3);c.addDataSourceProperty("socketTimeout",30);
        pool=new HikariDataSource(c);jdbc=new JdbcTemplate(pool);jdbc.setQueryTimeout(10);
        tx=new TransactionTemplate(new DataSourceTransactionManager(pool));
        this.catalog=catalog;this.dimensions=dimensions;
        fingerprint=namespace+"|"+modelName+"|"+dimensions+"|"+ProductDocuments.TEXT_VERSION;
        store=new PgVectorStore(jdbc,model,dimensions,PgVectorStore.PgDistanceType.COSINE_DISTANCE,false,PgVectorStore.PgIndexType.NONE,false);
    }
    private void compatible() {
        String actual=jdbc.queryForObject("SELECT fingerprint FROM rag_index_config WHERE id=1",String.class);
        if(!fingerprint.equals(actual)) throw new IllegalStateException("Index model/dimension/namespace mismatch; use a new index database");
    }
    /** SQL schema is provisioned separately; first writer binds an empty database to its model and source catalog. */
    private void bind() {
        Integer configs=jdbc.queryForObject("SELECT count(*) FROM rag_index_config",Integer.class);
        if(configs==0 && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM vector_store)",Boolean.class)))
            throw new IllegalStateException("Existing unbound vectors require an explicit new index database");
        jdbc.update("INSERT INTO rag_index_config(id,fingerprint) VALUES (1,?) ON CONFLICT(id) DO NOTHING",fingerprint);
        compatible();
        Integer actual=jdbc.queryForObject("SELECT atttypmod FROM pg_attribute WHERE attrelid='vector_store'::regclass AND attname='embedding'",Integer.class);
        if(actual==null || actual!=dimensions) throw new IllegalStateException("SQL vector dimension differs from model configuration");
    }
    @Override public List<Match> search(String query,int limit) {
        return search(SearchRequest.query(query).withTopK(limit).withSimilarityThreshold(0.3));
    }
    @Override public List<Match> search(String query,int limit,Set<Integer> eligibleIds) {
        if(eligibleIds.isEmpty()) return List.of();
        var filter=new Filter.Expression(Filter.ExpressionType.IN,new Filter.Key("productId"),
            new Filter.Value(eligibleIds.stream().sorted().toList()));
        return search(SearchRequest.query(query).withTopK(limit).withSimilarityThreshold(0.3).withFilterExpression(filter));
    }
    private List<Match> search(SearchRequest request) {
        compatible();
        return store.similaritySearch(request).stream()
            .map(d->new Match(Integer.parseInt(d.getMetadata().get("productId").toString()),
                1.0-((Number)d.getMetadata().get("distance")).doubleValue())).distinct().toList();
    }
    @Override public void sync(int productId) {
        tx.executeWithoutResult(s->{
            // Cross-instance transaction lock. Every writer/reconciler reads MySQL only AFTER acquiring it.
            // Small-catalog tradeoff: serial embedding work; no MySQL locks are held during model I/O.
            jdbc.execute("SET LOCAL lock_timeout = '5s'");
            jdbc.execute("SELECT pg_advisory_xact_lock(724061901)");
            bind();
            var current=catalog.find(productId);
            if(current.isEmpty() || !"available".equals(current.get().status())) {
                store.delete(List.of(ProductDocuments.id(productId))); return;
            }
            var product=current.get();String hash=ProductDocuments.hash(product);
            var old=jdbc.queryForList("SELECT metadata->>'contentHash' FROM vector_store WHERE id=?::uuid",String.class,ProductDocuments.id(productId));
            if(!old.isEmpty() && hash.equals(old.get(0))) return;
            store.add(List.of(ProductDocuments.document(product)));
            // If product changes while embedding, rollback PostgreSQL and let MQ/reconciliation retry.
            var latest=catalog.find(productId);
            if(latest.isEmpty() || !"available".equals(latest.get().status()) || !hash.equals(ProductDocuments.hash(latest.get())))
                throw new IllegalStateException("Product changed while embedding; retry latest state");
        });
    }
    @Override public Set<Integer> indexedIds() {
        return new HashSet<>(jdbc.queryForList("SELECT (metadata->>'productId')::integer FROM vector_store",Integer.class));
    }
    @Override public Map<String,Object> status() {
        try { compatible();return Map.of("status","UP","store","pgvector","documents",indexedIds().size(),"dimensions",dimensions); }
        catch(RuntimeException e) { return Map.of("status","UNAVAILABLE","store","pgvector","reason",e.getClass().getSimpleName()); }
    }
    @Override public void close() { pool.close(); }
}

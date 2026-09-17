package com.example.tomatomall.retrieval;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
@Service @RequiredArgsConstructor
public class ProductIndexMaintenance {
    private static final Logger log=LoggerFactory.getLogger(ProductIndexMaintenance.class);
    private final ProductVectorIndex index;
    private final ProductCatalog catalog;
    @Value("${rag.vector.enabled:false}") private boolean enabled;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"product-index-reconcile");t.setDaemon(true);return t;});
    private volatile String lastResult="NOT_RUN";
    private volatile int processed;
    /** Incremental reconciliation never clears the serving index. Every item uses the same locked sync as MQ. */
    public Map<String,Object> reconcile() {
        if(!enabled) throw new IllegalArgumentException("向量索引未启用，请先配置 PostgreSQL 和 Embedding");
        if(!running.compareAndSet(false,true)) return Map.of("status","ALREADY_RUNNING");
        executor.execute(()->{
            processed=0;
            try {
                Set<Integer> ids=new TreeSet<>(index.indexedIds());
                catalog.all().forEach(p->ids.add(p.id()));
                for(int id:ids) { if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Shutting down");index.sync(id);processed++; }
                lastResult="SUCCESS";
            } catch(RuntimeException e) { lastResult="FAILED";log.warn("Product index reconciliation failed: {}",e.getClass().getSimpleName()); }
            finally { running.set(false); }
        });
        return Map.of("status","ACCEPTED");
    }
    @Scheduled(initialDelayString="${rag.sync.initial-delay-ms:30000}",fixedDelayString="${rag.sync.interval-ms:300000}")
    public void scheduledReconcile() {
        if(enabled) reconcile(); // Never block the shared scheduler used by order timeouts/outbox.
    }
    public Map<String,Object> status() {
        return Map.of("index",index.status(),"reconciling",running.get(),"lastReconcile",lastResult,"processed",processed);
    }
    @jakarta.annotation.PreDestroy public void close() throws Exception {
        executor.shutdownNow();executor.awaitTermination(20,TimeUnit.SECONDS);
        if(index instanceof AutoCloseable c) c.close();
    }
}

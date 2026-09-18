package com.example.tomatomall.order;
import com.example.tomatomall.po.OutboxEvent;
import com.example.tomatomall.retrieval.ProductChangedPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.tomatomall.repository.OutboxEventRepository;
import com.example.tomatomall.service.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.*;
import java.util.*;

@Component
public class OutboxWorker {
    private static final Logger log=LoggerFactory.getLogger(OutboxWorker.class);
    private final OutboxEventRepository events;
    private final ObjectProvider<TimeoutPublisher> publisher;
    private final ObjectProvider<ProductChangedPublisher> productPublisher;
    private final StockCacheService stock;
    private final CartCacheService cart;
    private final TransactionTemplate tx;
    public OutboxWorker(OutboxEventRepository events,ObjectProvider<TimeoutPublisher> publisher,
            StockCacheService stock,CartCacheService cart,PlatformTransactionManager manager) {
        this(events,publisher,stock,cart,manager,null);
    }
    @Autowired
    public OutboxWorker(OutboxEventRepository events,ObjectProvider<TimeoutPublisher> publisher,
            StockCacheService stock,CartCacheService cart,PlatformTransactionManager manager,
            ObjectProvider<ProductChangedPublisher> productPublisher) {
        this.events=events;this.publisher=publisher;this.stock=stock;this.cart=cart;
        this.tx=new TransactionTemplate(manager);this.productPublisher=productPublisher;
    }
    @Scheduled(fixedDelayString="${orders.outbox.poll-ms:1000}")
    public void dispatch() {
        for(String id:events.findDue(new Date(),PageRequest.of(0,50))) deliver(id);
    }
    public void deliver(String id) {
        OutboxEvent event=tx.execute(status->{
            OutboxEvent e=events.lockById(id).orElse(null);Date now=new Date();
            if(e==null || e.isSent() || e.getNextAttemptAt().after(now) || (e.getLeaseUntil()!=null && e.getLeaseUntil().after(now))) return null;
            e.setLeaseToken(UUID.randomUUID().toString());e.setLeaseUntil(new Date(now.getTime()+60_000));e.setAttempts(e.getAttempts()+1);
            return e;
        });
        if(event==null) return;
        try {
            switch(event.getKind()) {
                case ORDER_TIMEOUT -> {
                    TimeoutPublisher p=publisher.getIfAvailable();
                    if(p==null) throw new IllegalStateException("MQ disabled; timeout recovery remains in database");
                    p.publish(event);
                }
                case PRODUCT_CHANGED -> {
                    ProductChangedPublisher p=productPublisher==null?null:productPublisher.getIfAvailable();
                    if(p==null) throw new IllegalStateException("Product MQ disabled; product change remains in outbox");
                    p.publish(event);
                }
                case STOCK_INVALIDATE -> stock.deleteStockCache(event.getAggregateId());
                case CART_INVALIDATE -> cart.clearCart(event.getAggregateId());
            }
            finish(event,null);
        } catch(Exception e) {
            log.warn("Outbox delivery failed id={} kind={} attempt={}",id,event.getKind(),event.getAttempts(),e);
            finish(event,e.getClass().getSimpleName());
        }
    }
    private void finish(OutboxEvent claimed,String error) {
        tx.executeWithoutResult(status->{
            OutboxEvent current=events.lockById(claimed.getId()).orElseThrow();
            // A timed-out publisher must not acknowledge a newer worker's lease.
            if(!Objects.equals(current.getLeaseToken(),claimed.getLeaseToken())) return;
            current.setLeaseUntil(null);current.setLeaseToken(null);current.setLastError(error);
            if(error==null) current.setSent(true);
            else current.setNextAttemptAt(new Date(System.currentTimeMillis()+Math.min(300_000,1000L*(1L<<Math.min(current.getAttempts(),8)))));
        });
    }
}

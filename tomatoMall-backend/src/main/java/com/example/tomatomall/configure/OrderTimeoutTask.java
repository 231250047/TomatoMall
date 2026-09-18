package com.example.tomatomall.configure;
import com.example.tomatomall.order.OrderTimeoutHandler;
import com.example.tomatomall.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.slf4j.*;
import java.util.Date;
@Component @EnableScheduling
public class OrderTimeoutTask {
    private static final Logger log=LoggerFactory.getLogger(OrderTimeoutTask.class);
    private final OrderRepository orders;
    private final OrderTimeoutHandler handler;
    public OrderTimeoutTask(OrderRepository orders,OrderTimeoutHandler handler) { this.orders=orders;this.handler=handler; }
    @Scheduled(fixedDelayString="${orders.timeout.scan-ms:60000}")
    public void processTimeoutOrders() {
        for(Integer id:orders.findDue(new Date(),PageRequest.of(0,100))) {
            try { handler.handle(id); }
            catch(Exception e) { log.error("Timeout recovery failed for order {}",id,e); }
        }
    }
}

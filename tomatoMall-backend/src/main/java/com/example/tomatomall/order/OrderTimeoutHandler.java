package com.example.tomatomall.order;
import com.example.tomatomall.po.Order;
import com.example.tomatomall.service.serviceImpl.OrderServiceImpl;
import org.springframework.stereotype.Service;
@Service
public class OrderTimeoutHandler {
    private final OrderServiceImpl orders;
    private final PaymentGateway gateway;
    public OrderTimeoutHandler(OrderServiceImpl orders,PaymentGateway gateway) { this.orders=orders;this.gateway=gateway; }
    // Intentionally not transactional: network calls must not hold database row locks.
    public void handle(Integer id) {
        Order o=orders.prepareTimeout(id);
        if(o==null) return;
        if(!o.isPaymentAttempted()) { orders.finishTimeout(id);return; }
        PaymentGateway.Resolution result=gateway.resolveAndClose(o);
        if(result.state()==PaymentGateway.State.PAID) orders.updateOrderStatus(id.toString(),result.tradeNo(),result.amount());
        else if(result.state()==PaymentGateway.State.CLOSED) orders.finishTimeout(id);
        // UNKNOWN leaves CLOSING + nextCheckAt persisted; the database scanner will retry.
    }
}

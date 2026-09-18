package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.order.*;
import com.example.tomatomall.po.*;
import com.example.tomatomall.repository.*;
import com.example.tomatomall.service.*;
import com.example.tomatomall.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/** Opt-in: mvn -Dtest=RocketMqTimeoutIT -Dorders.test.mq-endpoints=localhost:8081 test */
@DataJpaTest(showSql=false,properties={"orders.mq.enabled=true", "orders.mq.consumer-group=order-timeout-it",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes={OrderTransactionTest.Config.class,RocketMqTimeoutTransport.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RocketMqTimeoutIT {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("orders.mq.endpoints",()->System.getProperty("orders.test.mq-endpoints","localhost:8081"));
        r.add("spring.datasource.url",()->"jdbc:h2:mem:mq_live;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        r.add("spring.datasource.username",()->"sa");r.add("spring.datasource.driver-class-name",()->"org.h2.Driver");
    }
    @Autowired OrderServiceImpl service;
    @Autowired RocketMqTimeoutTransport transport;
    @Autowired OutboxWorker worker;
    @org.springframework.boot.test.mock.mockito.SpyBean OrderTimeoutHandler handler;
    @Autowired AccountRepository accounts;
    @Autowired ProductRepository products;
    @Autowired CartItemRepository carts;
    @Autowired StockpileRepository stocks;
    @Autowired OrderRepository orders;
    @Autowired OutboxEventRepository outbox;
    @MockBean StockCacheService stockCache;
    @MockBean CartCacheService cartCache;
    @MockBean PaymentGateway gateway;
    @Test void brokerDelayAndDuplicateDeliveryCloseOrderOnce() throws Exception {
        transport.connect();
        Account a=new Account();a.setUsername("mq-test");a=accounts.save(a);
        Product p=new Product();p.setTitle("MQ test book");p.setPrice(new BigDecimal("10"));p.setTag("science");p.setRate(5.0);p=products.save(p);
        Stockpile stock=new Stockpile();stock.setProduct(p);stock.setAmount(5);stocks.save(stock);
        CartItem cart=new CartItem();cart.setUserId(a.getId());cart.setProductId(p.getId());cart.setQuantity(2);cart=carts.save(cart);
        CheckoutVO request=new CheckoutVO();request.setRequestId(UUID.randomUUID().toString());request.setCartItemIds(List.of(cart.getCartItemId().toString()));request.setPaymentMethod("ALIPAY");
        ShopAddress address=new ShopAddress();address.setName("Test");address.setPhone("13800000000");address.setAddress("Test");request.setShoppingAddress(address);
        int id=service.createOrder(a.getUsername(),request).getOrderId();
        String restartMarker=System.getProperty("orders.test.restart-marker");
        Order o=orders.findById(id).orElseThrow();o.setExpiresAt(new Date(System.currentTimeMillis()+(restartMarker==null?8000:45000)));o.setNextCheckAt(o.getExpiresAt());orders.save(o);
        OutboxEvent event=outbox.findAll().stream().filter(e->e.getKind()==OutboxEvent.Kind.ORDER_TIMEOUT).findFirst().orElseThrow();event.setDeliverAt(o.getExpiresAt());outbox.save(event);
        worker.deliver(event.getId());
        assertThat(outbox.findById(event.getId()).orElseThrow().isSent()).as("broker must acknowledge the send").isTrue();
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.PENDING);
        if(restartMarker!=null) java.nio.file.Files.writeString(java.nio.file.Path.of(restartMarker),"sent order="+id+" deadline="+o.getExpiresAt().getTime());
        long deadline=System.currentTimeMillis()+(restartMarker==null?40_000:120_000);
        while(orders.findById(id).orElseThrow().getStatus()!=Order.OrderStatus.TIMEOUT && System.currentTimeMillis()<deadline) Thread.sleep(100);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.TIMEOUT);
        transport.publish(event); // A stale original delivery timestamp must be accepted and checked idempotently.
        org.mockito.Mockito.verify(handler,org.mockito.Mockito.timeout(15000).atLeast(2)).handle(id);
        assertThat(stocks.findByProductId(p.getId()).getAmount()).isEqualTo(5);
        assertThat(stocks.findByProductId(p.getId()).getFrozen()).isZero();
    }
}

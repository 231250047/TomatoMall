package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.*;
import com.example.tomatomall.repository.*;
import com.example.tomatomall.service.*;
import com.example.tomatomall.vo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false, properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.globally_quoted_identifiers=true", "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=OrderTransactionTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OrderTransactionTest {
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getProperty("orders.test.jdbc-url","jdbc:h2:mem:orders;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"));
        registry.add("spring.datasource.username",()->System.getProperty("orders.test.db-user","sa"));
        registry.add("spring.datasource.password",()->System.getProperty("orders.test.db-password",""));
        registry.add("spring.datasource.driver-class-name",()->System.getProperty("orders.test.jdbc-url","").startsWith("jdbc:mysql:")?"com.mysql.cj.jdbc.Driver":"org.h2.Driver");
    }

    @Configuration @EntityScan("com.example.tomatomall.po")
    @EnableJpaRepositories("com.example.tomatomall.repository")
    @Import({OrderServiceImpl.class,com.example.tomatomall.order.OrderTimeoutHandler.class,com.example.tomatomall.order.OutboxWorker.class})
    static class Config {}
    @Autowired OrderServiceImpl service;
    @Autowired AccountRepository accounts;
    @Autowired ProductRepository products;
    @Autowired StockpileRepository stocks;
    @Autowired CartItemRepository carts;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @MockBean StockCacheService stockCache;
    @MockBean CartCacheService cartCache;
    @MockBean ProductService productService;
    @MockBean CartService cartService;
    @Autowired com.example.tomatomall.order.OrderTimeoutHandler timeout;
    @Autowired com.example.tomatomall.order.OutboxWorker worker;
    @Autowired OutboxEventRepository outbox;
    @MockBean com.example.tomatomall.order.PaymentGateway gateway;
    @MockBean com.example.tomatomall.order.TimeoutPublisher publisher;
    Account user;
    @BeforeEach void setup() {
        user=new Account(); user.setUsername(UUID.randomUUID().toString()); user=accounts.save(user);
        lenient().when(stockCache.decrStock(anyInt(),anyInt())).thenReturn(10L);
    }
    CartItem cart(int available,int quantity) {
        Product p=new Product();p.setTitle("Book");p.setPrice(new BigDecimal("10.00"));p.setRate(5.0);p.setTag("science");p=products.save(p);
        Stockpile s=new Stockpile();s.setProduct(p);s.setAmount(available);stocks.save(s);
        CartItem c=new CartItem();c.setProductId(p.getId());c.setUserId(user.getId());c.setQuantity(quantity);return carts.save(c);
    }
    CheckoutVO checkout(CartItem... cs) {
        CheckoutVO v=new CheckoutVO();v.setRequestId(UUID.randomUUID().toString());v.setCartItemIds(Arrays.stream(cs).map(c->c.getCartItemId().toString()).toList());
        ShopAddress a=new ShopAddress();a.setName("Test");a.setPhone("13800000000");a.setAddress("Test address");a.setPostalCode("100000");v.setShoppingAddress(a);v.setPaymentMethod("ALIPAY");return v;
    }
    @Test void checkoutReservesDatabaseStock() {
        CartItem c=cart(5,2);service.createOrder(user.getUsername(),checkout(c));
        assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isEqualTo(2);
    }
    @Test void unavailableSecondItemRollsBackWholeOrder() {
        CartItem first=cart(5,2), second=cart(1,2);long before=orders.count();
        assertThatThrownBy(()->service.createOrder(user.getUsername(),checkout(first,second))).isInstanceOf(RuntimeException.class);
        assertThat(stocks.findByProductId(first.getProductId()).getFrozen()).isZero();
        assertThat(orders.count()).isEqualTo(before);
    }

    Order expire(int id,boolean attempted) {
        Order o=orders.findById(id).orElseThrow();o.setExpiresAt(new Date(System.currentTimeMillis()-1000));
        o.setNextCheckAt(o.getExpiresAt());o.setPaymentAttempted(attempted);return orders.save(o);
    }
    @Test void identicalRequestIsIdempotentEvenAfterCartWasConsumed() {
        CartItem c=cart(5,2);CheckoutVO v=checkout(c);
        int id=service.createOrder(user.getUsername(),v).getOrderId();
        assertThat(service.createOrder(user.getUsername(),v).getOrderId()).isEqualTo(id);
        assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isEqualTo(2);
        assertThat(carts.findById(c.getCartItemId())).isEmpty();
        v.getShoppingAddress().setAddress("Changed");
        assertThatThrownBy(()->service.createOrder(user.getUsername(),v)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsOtherBuyersCartAndDuplicateItems() {
        CartItem c=cart(5,2);c.setUserId(-1);carts.save(c);
        assertThatThrownBy(()->service.createOrder(user.getUsername(),checkout(c))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.createOrder(user.getUsername(),checkout(c,c))).isInstanceOf(IllegalArgumentException.class);
        assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isZero();
    }
    @Test void paymentUsesSnapshotAndOnlyConsumesOwnReservationOnce() {
        CartItem c=cart(10,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();
        CartItem another=new CartItem();another.setProductId(c.getProductId());another.setUserId(user.getId());another.setQuantity(3);another=carts.save(another);
        service.createOrder(user.getUsername(),checkout(another));
        service.updateOrderStatus(""+id,"snapshot-trade-"+id,"20.00");
        service.updateOrderStatus(""+id,"snapshot-trade-"+id,"20.00");
        Stockpile stock=stocks.findByProductId(c.getProductId());assertThat(stock.getAmount()).isEqualTo(8);assertThat(stock.getFrozen()).isEqualTo(3);
    }
    @Test void duplicateAndEarlyTimeoutsAreHarmless() {
        CartItem c=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();
        timeout.handle(id);assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isEqualTo(2);
        expire(id,false);timeout.handle(id);timeout.handle(id);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.TIMEOUT);
        assertThat(stocks.findByProductId(c.getProductId()).getAmount()).isEqualTo(5);
        assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isZero();
        verifyNoInteractions(gateway);
    }
    @Test void unknownPaymentRetainsStockAndDatabaseScanCanRetry() {
        CartItem c=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();expire(id,true);
        when(gateway.resolveAndClose(any())).thenAnswer(inv->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return com.example.tomatomall.order.PaymentGateway.Resolution.unknown();
        });
        timeout.handle(id);
        Order closing=orders.findById(id).orElseThrow();assertThat(closing.getStatus()).isEqualTo(Order.OrderStatus.CLOSING);
        assertThat(closing.getNextCheckAt()).isAfter(new Date());assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isEqualTo(2);
        when(gateway.resolveAndClose(any())).thenReturn(com.example.tomatomall.order.PaymentGateway.Resolution.closed());
        closing.setNextCheckAt(new Date(0));orders.save(closing);
        new com.example.tomatomall.configure.OrderTimeoutTask(orders,timeout).processTimeoutOrders();
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.TIMEOUT);
    }
    @Test void paidQueryRecoversMissingCallback() {
        CartItem c=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();expire(id,true);
        when(gateway.resolveAndClose(any())).thenReturn(new com.example.tomatomall.order.PaymentGateway.Resolution(com.example.tomatomall.order.PaymentGateway.State.PAID,"query-trade-"+id,"20.00"));
        timeout.handle(id);timeout.handle(id);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.SUCCESS);
        assertThat(stocks.findByProductId(c.getProductId()).getAmount()).isEqualTo(3);
    }
    @Test void latePaymentAfterReleaseRecordsIncidentWithoutTakingStockAgain() {
        CartItem c=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();expire(id,false);timeout.handle(id);
        service.updateOrderStatus(""+id,"late-trade-"+id,"20.00");
        Order o=orders.findById(id).orElseThrow();assertThat(o.getStatus()).isEqualTo(Order.OrderStatus.TIMEOUT);assertThat(o.getPaymentIncident()).contains("PAID_AFTER_CLOSE");
        assertThat(stocks.findByProductId(c.getProductId()).getAmount()).isEqualTo(5);
    }
    @Test void inventoryFailureRollsBackPaymentStatusAndEveryItem() {
        CartItem first=cart(5,2),second=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(first,second)).getOrderId();
        Stockpile broken=stocks.findByProductId(second.getProductId());broken.setFrozen(0);stocks.save(broken);
        assertThatThrownBy(()->service.updateOrderStatus(""+id,"rollback-trade-"+id,"40.00")).isInstanceOf(IllegalStateException.class);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.PENDING);
        assertThat(stocks.findByProductId(first.getProductId()).getAmount()).isEqualTo(5);
        assertThat(stocks.findByProductId(first.getProductId()).getFrozen()).isEqualTo(2);
    }
    @Test void outboxRetriesAmbiguousSendWithSameEventAndDeadline() throws Exception {
        CartItem c=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();
        OutboxEvent event=outbox.findAll().stream().filter(e->e.getKind()==OutboxEvent.Kind.ORDER_TIMEOUT && e.getAggregateId()==id).findFirst().orElseThrow();
        Date deadline=event.getDeliverAt();java.util.List<String> delivered=new ArrayList<>();
        doAnswer(inv->{OutboxEvent e=inv.getArgument(0);delivered.add(e.getId());throw new java.io.IOException("ack lost after broker accepted");}).when(publisher).publish(any());
        worker.deliver(event.getId());OutboxEvent retry=outbox.findById(event.getId()).orElseThrow();
        assertThat(retry.isSent()).isFalse();assertThat(retry.getAttempts()).isEqualTo(1);assertThat(retry.getDeliverAt()).isEqualTo(deadline);
        retry.setNextAttemptAt(new Date(0));outbox.save(retry);
        doAnswer(inv->{delivered.add(((OutboxEvent)inv.getArgument(0)).getId());return null;}).when(publisher).publish(any());
        worker.deliver(event.getId());worker.deliver(event.getId());
        assertThat(delivered).containsExactly(event.getId(),event.getId());assertThat(outbox.findById(event.getId()).orElseThrow().isSent()).isTrue();
    }
    @Test void abandonedOutboxLeaseIsRecovered() throws Exception {
        OutboxEvent e=OutboxEvent.create(OutboxEvent.Kind.STOCK_INVALIDATE,999,new Date());e.setLeaseToken("dead-process");e.setLeaseUntil(new Date(0));e=outbox.save(e);
        worker.deliver(e.getId());verify(stockCache).deleteStockCache(999);assertThat(outbox.findById(e.getId()).orElseThrow().isSent()).isTrue();
    }
    @Test void stockContentionDoesNotOversell() throws Exception {
        CartItem seed=cart(5,1);List<Account> buyers=new ArrayList<>();List<CheckoutVO> requests=new ArrayList<>();
        for(int i=0;i<12;i++) { Account a=new Account();a.setUsername(UUID.randomUUID().toString());a=accounts.save(a);buyers.add(a);
            CartItem c=new CartItem();c.setUserId(a.getId());c.setProductId(seed.getProductId());c.setQuantity(1);requests.add(checkout(carts.save(c))); }
        var pool=java.util.concurrent.Executors.newFixedThreadPool(12);var start=new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Boolean>> futures=new ArrayList<>();
        try {
            for(int i=0;i<12;i++) { final int n=i;futures.add(pool.submit(()->{start.await();try {service.createOrder(buyers.get(n).getUsername(),requests.get(n));return true;} catch(com.example.tomatomall.exception.TomatoMallException expected) {return false;}})); }
            start.countDown();int success=0;for(var future:futures) if(future.get(20,java.util.concurrent.TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(5);assertThat(stocks.findByProductId(seed.getProductId()).getFrozen()).isEqualTo(5);
        } finally {pool.shutdownNow();}
    }
    @Test void paymentAndTimeoutRaceHasOneInventoryEffect() throws Exception {
        CartItem c=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();expire(id,false);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var pay=pool.submit(()->{start.await();service.updateOrderStatus(""+id,"race-trade-"+id,"20.00");return true;});
            var close=pool.submit(()->{start.await();timeout.handle(id);return true;});start.countDown();
            pay.get(10,java.util.concurrent.TimeUnit.SECONDS);close.get(10,java.util.concurrent.TimeUnit.SECONDS);
            Order o=orders.findById(id).orElseThrow();Stockpile s=stocks.findByProductId(c.getProductId());
            assertThat(s.getFrozen()).isZero();assertThat(s.getAmount()).isEqualTo(o.getStatus()==Order.OrderStatus.SUCCESS?3:5);
            assertThat(o.getStatus()).isIn(Order.OrderStatus.SUCCESS,Order.OrderStatus.TIMEOUT);
        } finally {pool.shutdownNow();}
    }

    @Test void cancellationBeforeDeadlineReleasesOnlyOnce() {
        CartItem c=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();
        assertThatThrownBy(()->service.requestCancellation(id,-1)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        service.requestCancellation(id,user.getId());timeout.handle(id);
        service.requestCancellation(id,user.getId());timeout.handle(id);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isZero();assertThat(stocks.findByProductId(c.getProductId()).getAmount()).isEqualTo(5);
    }

    @Test void databaseInsertFailureRollsBackReservationCartAndOutbox() {
        CartItem c=cart(5,2);Product p=products.findById(c.getProductId()).orElseThrow();p.setPrice(new BigDecimal("99999999.99"));products.save(p);
        long before=outbox.count();
        assertThatThrownBy(()->service.createOrder(user.getUsername(),checkout(c))).isInstanceOf(RuntimeException.class);
        assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isZero();
        assertThat(carts.findById(c.getCartItemId())).isPresent();assertThat(outbox.count()).isEqualTo(before);
    }
    @Test void simultaneousRetryCreatesOneOrder() throws Exception {
        CartItem c=cart(5,2);CheckoutVO v=checkout(c);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> submit=()->{start.await();return service.createOrder(user.getUsername(),v).getOrderId();};
            var a=pool.submit(submit);var b=pool.submit(submit);start.countDown();
            assertThat(a.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(b.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isEqualTo(2);
        } finally {pool.shutdownNow();}
    }
    @Test void cacheFailureCannotRollBackCommittedOrderAndCanRetry() {
        CartItem c=cart(5,2);int id=service.createOrder(user.getUsername(),checkout(c)).getOrderId();
        OutboxEvent e=outbox.findAll().stream().filter(x->x.getKind()==OutboxEvent.Kind.STOCK_INVALIDATE && x.getAggregateId().equals(c.getProductId())).findFirst().orElseThrow();
        doThrow(new IllegalStateException("Redis unavailable")).when(stockCache).deleteStockCache(c.getProductId());worker.deliver(e.getId());
        assertThat(orders.findById(id)).isPresent();assertThat(stocks.findByProductId(c.getProductId()).getFrozen()).isEqualTo(2);
        OutboxEvent retry=outbox.findById(e.getId()).orElseThrow();assertThat(retry.isSent()).isFalse();retry.setNextAttemptAt(new Date(0));outbox.save(retry);
        doNothing().when(stockCache).deleteStockCache(c.getProductId());worker.deliver(e.getId());assertThat(outbox.findById(e.getId()).orElseThrow().isSent()).isTrue();
    }
    @Test void oldPublisherCannotAcknowledgeNewOwnersLease() throws Exception {
        OutboxEvent e=outbox.save(OutboxEvent.create(OutboxEvent.Kind.ORDER_TIMEOUT,999,new Date()));
        doAnswer(inv->{OutboxEvent current=outbox.findById(e.getId()).orElseThrow();current.setLeaseToken("new-owner");outbox.save(current);return null;}).when(publisher).publish(any());
        worker.deliver(e.getId());assertThat(outbox.findById(e.getId()).orElseThrow().isSent()).isFalse();
    }
}

package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.order.OutboxWorker;
import com.example.tomatomall.po.*;
import com.example.tomatomall.repository.*;
import com.example.tomatomall.retrieval.ProductChangedPublisher;
import com.example.tomatomall.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false,properties={"spring.datasource.url=jdbc:h2:mem:productevents;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.globally_quoted_identifiers=true","spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=ProductOutboxTransactionTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ProductOutboxTransactionTest {
    @Configuration @EntityScan("com.example.tomatomall.po")
    @EnableJpaRepositories("com.example.tomatomall.repository")
    @Import({ProductServiceImpl.class,SpecificationServiceImpl.class,OutboxWorker.class})
    static class Config {}
    @Autowired ProductServiceImpl products;
    @Autowired SpecificationServiceImpl specifications;
    @Autowired ProductRepository repository;
    @Autowired SpecificationRepository specRepository;
    @Autowired StockpileRepository stocks;
    @SpyBean OutboxEventRepository outbox;
    @MockBean ProductChangedPublisher publisher;
    @MockBean StockCacheService stockCache;
    @MockBean CartCacheService cartCache;
    @Autowired OutboxWorker worker;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    Product product() {
        Product p=new Product();p.setTitle(UUID.randomUUID().toString());p.setPrice(new BigDecimal("39.90"));
        p.setRate(8.0);p.setTag("science");return p;
    }
    List<OutboxEvent> events(int id) {
        return outbox.findAll().stream().filter(e->e.getKind()==OutboxEvent.Kind.PRODUCT_CHANGED && e.getAggregateId()==id).toList();
    }
    @Test void createUpdateAndUnlistCommitProductEvents() {
        Product p=product();assertThat(products.addProduct(p)).isTrue();
        assertThat(events(p.getId())).hasSize(1);
        Product change=new Product();change.setId(p.getId());change.setDescription("changed");
        products.updateProduct(change);products.deleteProduct(p.getId().toString());
        assertThat(events(p.getId())).hasSize(3).allMatch(e->!e.isSent());
        assertThat(repository.findById(p.getId()).orElseThrow().getStatus()).isEqualTo("unavailable");
    }
    @Test void failedOutboxInsertRollsBackNewProductAndStock() {
        Product p=product();long beforeProducts=repository.count(),beforeStocks=stocks.count(),beforeEvents=outbox.count();
        doThrow(new DataIntegrityViolationException("forced failure")).when(outbox).save(any(OutboxEvent.class));
        assertThatThrownBy(()->products.addProduct(p)).isInstanceOf(RuntimeException.class);
        assertThat(repository.count()).isEqualTo(beforeProducts);
        assertThat(stocks.count()).isEqualTo(beforeStocks);
        assertThat(outbox.count()).isEqualTo(beforeEvents);
    }
    @Test void failedOutboxInsertRollsBackProductUpdate() {
        Product p=repository.save(product());Product change=new Product();change.setId(p.getId());change.setTitle("should rollback");
        doThrow(new DataIntegrityViolationException("forced failure")).when(outbox).save(any(OutboxEvent.class));
        assertThatThrownBy(()->products.updateProduct(change)).isInstanceOf(RuntimeException.class);
        assertThat(repository.findById(p.getId()).orElseThrow().getTitle()).isEqualTo(p.getTitle());
    }
    @Test void specificationCreationAndMoveInvalidateBothProducts() {
        Product first=repository.save(product()),second=repository.save(product());
        Specification spec=new Specification();spec.setProductId(first.getId());spec.setItem("难度");spec.setValue("入门");
        specifications.addSpecification(spec);
        assertThat(events(first.getId())).hasSize(1);
        Specification change=new Specification();change.setId(spec.getId());change.setProductId(second.getId());change.setValue("进阶");
        specifications.updateSpecification(change);
        assertThat(events(first.getId())).hasSize(2);
        assertThat(events(second.getId())).hasSize(1);
        assertThat(specRepository.findById(spec.getId()).orElseThrow().getProductId()).isEqualTo(second.getId());
    }
    @Test void failedOutboxRollsBackSpecificationChange() {
        Product p=repository.save(product());Specification spec=new Specification();spec.setProductId(p.getId());spec.setItem("难度");spec.setValue("入门");
        spec=specRepository.save(spec);Specification change=new Specification();change.setId(spec.getId());change.setValue("changed");
        doThrow(new DataIntegrityViolationException("forced failure")).when(outbox).save(any(OutboxEvent.class));
        assertThatThrownBy(()->specifications.updateSpecification(change)).isInstanceOf(RuntimeException.class);
        assertThat(specRepository.findById(spec.getId()).orElseThrow().getValue()).isEqualTo("入门");
    }
    @Test void disabledProductMqLeavesDurableEventForLaterReplay() {
        var beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        OutboxWorker disabled=new OutboxWorker(outbox,beans.getBeanProvider(com.example.tomatomall.order.TimeoutPublisher.class),
            stockCache,cartCache,transactionManager,beans.getBeanProvider(ProductChangedPublisher.class));
        OutboxEvent event=outbox.save(OutboxEvent.create(OutboxEvent.Kind.PRODUCT_CHANGED,456,new Date()));
        disabled.deliver(event.getId());
        OutboxEvent pending=outbox.findById(event.getId()).orElseThrow();
        assertThat(pending.isSent()).isFalse();
        assertThat(pending.getLastError()).isEqualTo("IllegalStateException");
        pending.setNextAttemptAt(new Date(0));outbox.save(pending);
        worker.deliver(event.getId());
        assertThat(outbox.findById(event.getId()).orElseThrow().isSent()).isTrue();
    }
    @Test void secondEventFailureRollsBackSpecificationMoveAndFirstEvent() {
        Product first=repository.save(product()),second=repository.save(product());
        Specification spec=new Specification();spec.setProductId(first.getId());spec.setItem("难度");spec.setValue("入门");
        spec=specRepository.save(spec);Specification change=new Specification();change.setId(spec.getId());change.setProductId(second.getId());
        doThrow(new DataIntegrityViolationException("second event failure")).when(outbox)
            .save(argThat((OutboxEvent e)->e!=null && e.getAggregateId().equals(second.getId())));
        long before=outbox.count();
        assertThatThrownBy(()->specifications.updateSpecification(change)).isInstanceOf(RuntimeException.class);
        assertThat(outbox.count()).isEqualTo(before);
        assertThat(specRepository.findById(spec.getId()).orElseThrow().getProductId()).isEqualTo(first.getId());
    }
    @Test void publishFailureRemainsPendingAndRetryAcknowledgesOnlyAfterSend() throws Exception {
        OutboxEvent event=outbox.save(OutboxEvent.create(OutboxEvent.Kind.PRODUCT_CHANGED,123,new Date()));
        doThrow(new IllegalStateException("MQ unavailable")).doNothing().when(publisher).publish(any());
        worker.deliver(event.getId());
        OutboxEvent failed=outbox.findById(event.getId()).orElseThrow();
        assertThat(failed.isSent()).isFalse();assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isAfter(new Date());
        failed.setNextAttemptAt(new Date(0));outbox.save(failed);worker.deliver(event.getId());
        assertThat(outbox.findById(event.getId()).orElseThrow().isSent()).isTrue();
        verify(publisher,times(2)).publish(any());
    }
}

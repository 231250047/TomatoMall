package com.example.tomatomall.retrieval;

import com.example.tomatomall.po.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.*;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** NORMAL topic; order deadline messages keep their separate DELAY topic and transport. */
@Component
@ConditionalOnProperty(name="rag.mq.enabled",havingValue="true")
public class RocketMqProductTransport implements ProductChangedPublisher {
    private static final Logger log=LoggerFactory.getLogger(RocketMqProductTransport.class);
    public record ProductChangedMessage(int version,String eventId,Integer productId) {}
    private final ObjectProvider<ProductVectorIndex> indexes;
    private final ObjectMapper json=new ObjectMapper();
    private final ClientServiceProvider provider=ClientServiceProvider.loadService();
    @Value("${orders.mq.endpoints:localhost:8081}") private String endpoints;
    @Value("${rag.mq.topic:product-changed}") private String topic;
    @Value("${rag.mq.consumer-group:product-index-consumer}") private String group;
    private volatile Producer producer;
    private volatile PushConsumer consumer;

    public RocketMqProductTransport(ObjectProvider<ProductVectorIndex> indexes) { this.indexes=indexes; }

    @Scheduled(fixedDelayString="${rag.mq.reconnect-ms:10000}")
    public synchronized void connect() {
        try {
            ClientConfiguration config=ClientConfiguration.newBuilder().setEndpoints(endpoints).enableSsl(false)
                .setRequestTimeout(Duration.ofSeconds(5)).build();
            if(producer==null) producer=provider.newProducerBuilder().setClientConfiguration(config).setTopics(topic).build();
            if(consumer==null) consumer=provider.newPushConsumerBuilder().setClientConfiguration(config).setConsumerGroup(group)
                .setSubscriptionExpressions(Map.of(topic,new FilterExpression("*",FilterExpressionType.TAG)))
                .setMessageListener(this::consume).build();
        } catch(Exception e) { log.warn("Product MQ unavailable; durable outbox will retry",e); }
    }

    private static void validate(ProductChangedMessage payload) {
        if(payload==null || payload.version()!=1 || payload.productId()==null || payload.productId()<=0
                || payload.eventId()==null || payload.eventId().length()!=36
                || !UUID.fromString(payload.eventId()).toString().equalsIgnoreCase(payload.eventId()))
            throw new IllegalArgumentException("Invalid product change event");
    }

    ConsumeResult consume(MessageView message) {
        try {
            ByteBuffer buffer=message.getBody().duplicate();
            byte[] body=new byte[buffer.remaining()];buffer.get(body);
            ProductChangedMessage payload=json.readValue(body,ProductChangedMessage.class);
            validate(payload);
            ProductVectorIndex index=indexes.getIfAvailable();
            if(index==null) throw new IllegalStateException("Product vector index disabled");
            // sync reads the latest MySQL state and commits PostgreSQL before returning.
            // Replayed/out-of-order events never carry a stale product snapshot to overwrite it.
            index.sync(payload.productId());
            return ConsumeResult.SUCCESS;
        } catch(Exception e) {
            log.warn("Product indexing failed message={}; delivery will be retried",message.getMessageId(),e);
            return ConsumeResult.FAILURE;
        }
    }

    @Override public void publish(OutboxEvent event) throws Exception {
        if(event.getKind()!=OutboxEvent.Kind.PRODUCT_CHANGED) throw new IllegalArgumentException("Wrong outbox event kind");
        ProductChangedMessage payload=new ProductChangedMessage(1,event.getId(),event.getAggregateId());
        validate(payload);
        Producer current=producer;
        if(current==null) throw new IllegalStateException("Product MQ producer not connected");
        current.send(provider.newMessageBuilder().setTopic(topic).setKeys(event.getId())
            .setBody(json.writeValueAsBytes(payload)).build());
    }

    @PreDestroy public synchronized void close() throws java.io.IOException {
        try { if(consumer!=null) consumer.close(); } finally { if(producer!=null) producer.close(); }
    }
}

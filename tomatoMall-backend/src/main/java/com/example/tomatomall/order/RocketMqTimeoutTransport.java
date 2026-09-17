package com.example.tomatomall.order;
import com.example.tomatomall.po.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.apis.*;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.consumer.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import java.time.Duration;
import java.util.Map;

@Component @ConditionalOnProperty(name="orders.mq.enabled",havingValue="true")
public class RocketMqTimeoutTransport implements TimeoutPublisher {
    private static final Logger log=LoggerFactory.getLogger(RocketMqTimeoutTransport.class);
    public record TimeoutMessage(int version,String eventId,Integer orderId,long expiresAt) {}
    private final OrderTimeoutHandler handler;
    private final ObjectMapper json=new ObjectMapper();
    private final ClientServiceProvider provider=ClientServiceProvider.loadService();
    @Value("${orders.mq.endpoints:localhost:8081}") private String endpoints;
    @Value("${orders.mq.topic:order-timeout}") private String topic;
    @Value("${orders.mq.consumer-group:order-timeout-consumer}") private String group;
    private volatile Producer producer;
    private volatile PushConsumer consumer;
    public RocketMqTimeoutTransport(OrderTimeoutHandler handler) { this.handler=handler; }
    // Lazy recovery allows HTTP checkout + durable outbox to work during broker outages.
    @Scheduled(fixedDelayString="${orders.mq.reconnect-ms:10000}")
    public synchronized void connect() {
        try {
            ClientConfiguration config=ClientConfiguration.newBuilder().setEndpoints(endpoints).enableSsl(false)
                .setRequestTimeout(Duration.ofSeconds(5)).build();
            if(producer==null) producer=provider.newProducerBuilder().setClientConfiguration(config).setTopics(topic).build();
            if(consumer==null) consumer=provider.newPushConsumerBuilder().setClientConfiguration(config).setConsumerGroup(group)
                .setSubscriptionExpressions(Map.of(topic,new FilterExpression("*",FilterExpressionType.TAG)))
                .setMessageListener(message->{
                    try {
                        byte[] body=new byte[message.getBody().remaining()];message.getBody().get(body);
                        TimeoutMessage payload=json.readValue(body,TimeoutMessage.class);
                        if(payload.version()!=1 || payload.orderId()==null || payload.eventId()==null)
                            throw new IllegalArgumentException("Invalid timeout event");
                        handler.handle(payload.orderId());
                        // DB transaction has committed before acknowledging delivery.
                        return ConsumeResult.SUCCESS;
                    } catch(Exception e) { log.error("Timeout consumption failed message={}",message.getMessageId(),e);return ConsumeResult.FAILURE; }
                }).build();
        } catch(Exception e) { log.warn("RocketMQ unavailable; retrying connection",e); }
    }
    @Override public void publish(OutboxEvent event) throws Exception {
        Producer p=producer;if(p==null) throw new IllegalStateException("RocketMQ producer not connected");
        byte[] body=json.writeValueAsBytes(new TimeoutMessage(1,event.getId(),event.getAggregateId(),event.getDeliverAt().getTime()));
        // Retrying never moves the original business deadline forward.
        p.send(provider.newMessageBuilder().setTopic(topic).setKeys(event.getId()).setBody(body)
            .setDeliveryTimestamp(event.getDeliverAt().getTime()).build());
    }
    @PreDestroy public synchronized void close() throws java.io.IOException {
        try { if(consumer!=null) consumer.close(); } finally { if(producer!=null) producer.close(); }
    }
}

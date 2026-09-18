package com.example.tomatomall.retrieval;

import com.example.tomatomall.po.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RocketMqProductTransportTest {
    ProductVectorIndex index=mock(ProductVectorIndex.class);
    @SuppressWarnings("unchecked") ObjectProvider<ProductVectorIndex> indexes=mock(ObjectProvider.class);
    RocketMqProductTransport transport=new RocketMqProductTransport(indexes);
    ObjectMapper json=new ObjectMapper();

    MessageView message(int version,String eventId,Integer id) throws Exception {
        MessageView view=mock(MessageView.class);
        when(view.getBody()).thenReturn(ByteBuffer.wrap(json.writeValueAsBytes(new RocketMqProductTransport.ProductChangedMessage(version,eventId,id))));
        return view;
    }
    @Test void acknowledgesOnlyAfterSyncAndRetriesAfterFailure() throws Exception {
        when(indexes.getIfAvailable()).thenReturn(index);
        MessageView view=message(1,UUID.randomUUID().toString(),12);
        doThrow(new IllegalStateException("Postgres unavailable")).doNothing().when(index).sync(12);
        assertThat(transport.consume(view)).isEqualTo(ConsumeResult.FAILURE);
        assertThat(transport.consume(view)).isEqualTo(ConsumeResult.SUCCESS);
        verify(index,times(2)).sync(12);
    }
    @Test void rejectsInvalidSchemaIdentifiersAndMissingIndex() throws Exception {
        String eventId=UUID.randomUUID().toString();
        assertThat(transport.consume(message(2,eventId,12))).isEqualTo(ConsumeResult.FAILURE);
        assertThat(transport.consume(message(1,"invalid",12))).isEqualTo(ConsumeResult.FAILURE);
        assertThat(transport.consume(message(1,eventId,0))).isEqualTo(ConsumeResult.FAILURE);
        assertThat(transport.consume(message(1,eventId,null))).isEqualTo(ConsumeResult.FAILURE);
        assertThat(transport.consume(message(1,eventId,12))).isEqualTo(ConsumeResult.FAILURE);
        verifyNoInteractions(index);
    }
    @Test void publishesNormalMessageWithoutDeliveryTimestamp() throws Exception {
        Producer producer=mock(Producer.class);
        ReflectionTestUtils.setField(transport,"producer",producer);
        ReflectionTestUtils.setField(transport,"topic","product-changed");
        OutboxEvent event=OutboxEvent.create(OutboxEvent.Kind.PRODUCT_CHANGED,12,new Date());
        transport.publish(event);
        ArgumentCaptor<Message> sent=ArgumentCaptor.forClass(Message.class);
        verify(producer).send(sent.capture());
        assertThat(sent.getValue().getTopic()).isEqualTo("product-changed");
        assertThat(sent.getValue().getDeliveryTimestamp()).isEmpty();
        ByteBuffer buffer=sent.getValue().getBody();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
        var payload=json.readValue(bytes,RocketMqProductTransport.ProductChangedMessage.class);
        assertThat(payload.productId()).isEqualTo(12);
        assertThat(payload.eventId()).isEqualTo(event.getId());
    }
}

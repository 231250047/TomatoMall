package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Order;
import com.example.tomatomall.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRegressionTest {
    @Mock OrderRepository orderRepository;
    @Mock CartsOrdersRelationRepository cartsOrdersRelationRepository;
    @InjectMocks OrderServiceImpl service;

    @Test void rejectsWrongPaymentAmountBeforeChangingOrder() {
        Order order = new Order();
        order.setOrderId(1);
        order.setTotalAmount(new BigDecimal("100.00"));
        when(orderRepository.lockById(1)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.updateOrderStatus("1", "trade-1", "0.01"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
    }
}

package com.example.tomatomall.repository;

import com.example.tomatomall.po.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByUserIdAndStatus(Integer userId, Order.OrderStatus status);

    List<Order> findByUserId(Integer userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.orderId = :id")
    java.util.Optional<Order> lockById(Integer id);
    java.util.Optional<Order> findByUserIdAndRequestId(Integer userId, String requestId);
    @org.springframework.data.jpa.repository.Query("select o.orderId from Order o where o.status in :statuses and o.nextCheckAt <= :now order by o.nextCheckAt, o.orderId")
    List<Integer> findDueCandidates(java.util.Date now, java.util.Collection<Order.OrderStatus> statuses, org.springframework.data.domain.Pageable page);
    default List<Integer> findDue(java.util.Date now, org.springframework.data.domain.Pageable page) {
        return findDueCandidates(now,List.of(Order.OrderStatus.PENDING,Order.OrderStatus.CLOSING),page);
    }
}

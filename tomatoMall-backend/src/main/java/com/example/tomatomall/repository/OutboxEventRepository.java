package com.example.tomatomall.repository;
import com.example.tomatomall.po.OutboxEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface OutboxEventRepository extends JpaRepository<OutboxEvent,String> {
    @Query("select e.id from OutboxEvent e where e.sent=false and e.nextAttemptAt<=:now and (e.leaseUntil is null or e.leaseUntil<=:now) order by e.nextAttemptAt,e.id")
    List<String> findDue(Date now,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from OutboxEvent e where e.id=:id")
    Optional<OutboxEvent> lockById(String id);
}

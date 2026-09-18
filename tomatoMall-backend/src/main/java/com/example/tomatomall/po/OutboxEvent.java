package com.example.tomatomall.po;
import jakarta.persistence.*;
import lombok.*;
import java.util.Date;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @Entity
@Table(name="outbox_event", indexes=@Index(name="idx_outbox_due",columnList="sent,next_attempt_at"))
public class OutboxEvent {
    public enum Kind { ORDER_TIMEOUT, STOCK_INVALIDATE, CART_INVALIDATE, PRODUCT_CHANGED }
    @Id @Column(length=36) private String id=UUID.randomUUID().toString();
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Kind kind;
    @Column(nullable=false) private Integer aggregateId;
    @Column(nullable=false) private Date deliverAt;
    @Column(nullable=false) private Date nextAttemptAt;
    @Column(nullable=false) private boolean sent;
    @Column(nullable=false) private int attempts;
    @Column(length=36) private String leaseToken;
    private Date leaseUntil;
    @Column(length=100) private String lastError;
    public static OutboxEvent create(Kind kind,Integer id,Date deliverAt) {
        OutboxEvent e=new OutboxEvent();e.kind=kind;e.aggregateId=id;e.deliverAt=deliverAt;e.nextAttemptAt=new Date();return e;
    }
}

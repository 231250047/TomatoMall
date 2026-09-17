package com.example.tomatomall.po;

import com.example.tomatomall.repository.AccountRepository;
import com.example.tomatomall.vo.OrderVO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}), indexes = @Index(name="idx_order_due", columnList="status,next_check_at"))
public class Order {
    // 定义订单状态枚�?
    public enum OrderStatus {
        PENDING,   // 待支�?
        SUCCESS,   // 支付成功
        FAILED,    // 支付失败
        CLOSING,
        CANCELLED,
        TIMEOUT    // 超时未支�?
    }

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "orderId")
    private Integer orderId;

    @Basic
    @Column(name = "userId", nullable = false)
    private Integer userId;

    @Basic
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Basic
    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    // 使用枚举类型，映射到数据库的字符�?
    @Enumerated(EnumType.STRING)  // 存储枚举的名称(�?"PENDING"�?
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Basic
    @Column(name = "create_time")
    private Date createTime;

    @Basic
    @Column(name = "payment_time")
    private Date paymentTime;  // 新增:支付时�?

    @Basic
    @Column(name = "receiver_name", length = 50)
    private String receiverName; // 收货人姓�?

    @Basic
    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone; // 收货人电�?

    @Basic
    @Column(name = "receiver_address", length = 255)
    private String receiverAddress; // 收货地址

    @Basic
    @Column(name = "receiver_postal_code", length = 10)
    private String receiverPostalCode; // 邮政编码

    @Column(name="request_id", length=64)
    private String requestId;
    @Column(name="request_hash", length=64)
    private String requestHash;
    @Column(name="expires_at")
    private Date expiresAt;
    @Column(name="next_check_at")
    private Date nextCheckAt;
    @Column(name="cancel_requested", nullable=false)
    private boolean cancelRequested;
    @Column(name="close_attempts", nullable=false)
    private int closeAttempts;
    @Column(name="payment_attempted", nullable=false)
    private boolean paymentAttempted;
    @Column(name="trade_no", unique=true, length=100)
    private String tradeNo;
    @Column(name="payment_incident", length=255)
    private String paymentIncident;
    @Column(name="hidden", nullable=false)
    private boolean hidden;

    // 金额非负校验
    public void setTotalAmount(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.signum() < 0) {
            throw new IllegalArgumentException("订单金额不能为负");
        }
        this.totalAmount = totalAmount;
    }
    public OrderVO partToVO() {//无法填充username字段，需要手动填�?
        OrderVO orderVO = new OrderVO();
        orderVO.setOrderId(orderId);
        orderVO.setExpiresAt(expiresAt);
        orderVO.setStatus(this.status.toString());
        orderVO.setPaymentMethod(this.paymentMethod);
        orderVO.setCreateTime(this.createTime);
        orderVO.setPaymentTime(paymentTime);  // 新增:设置支付时�?
        orderVO.setTotalAmount(this.totalAmount);

        // 填充收货信息
        orderVO.setReceiverName(this.receiverName);
        orderVO.setReceiverPhone(this.receiverPhone);
        orderVO.setReceiverAddress(this.receiverAddress);
        orderVO.setReceiverPostalCode(this.receiverPostalCode);
        return orderVO;
    }
}

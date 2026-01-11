package com.example.tomatomall.po;

import com.example.tomatomall.vo.ProductVO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products") // 映射数据库表名
public class Product {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Integer id;

    @Basic
    @Column(name = "title", nullable = false) // 对应 NOT NULL 约束
    private String title;

    @Basic
    @Column(name = "price", nullable = false, precision = 10, scale = 2) // DECIMAL(10,2)
    private BigDecimal price;

    @Basic
    @Column(name = "rate", nullable = false) // 最低0分，最高10分需业务逻辑校验
    private Double rate;

    @Basic
    @Column(name = "description", length = 255)
    private String description;

    @Basic
    @Column(name = "cover", length = 500)
    private String cover;

    @Basic
    @Column(name = "detail", length = 500)
    private String detail;

    @Basic
    @Column(name = "tag", nullable = false)
    private String tag;

    @Basic
    @Column(name = "create_time") // 补充示例中未包含的字段
    private Date createTime;

    @Basic
    @Column(name = "seller_id")
    private Integer sellerId;

    @Basic
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'available'")
    private String status = "available";

    // 新增：商品成色（字符串，限定为枚举值）
    @Basic
    @Column(name = "product_condition", nullable = false, columnDefinition = "VARCHAR(10) DEFAULT '10_NEW'")
    private String condition = "10_NEW";

    @OneToOne(mappedBy = "product", cascade = CascadeType.REMOVE)
    private Stockpile stockpile;

    public void setRate(Double rate) {
        if (rate == null) {
            throw new IllegalArgumentException("商品评分不能为 null");
        }
        if (rate < 0.0 || rate > 10.0) {
            throw new IllegalArgumentException("商品评分必须在 0 到 10 之间");
        }
        this.rate = rate;
    }

    public void setPrice(BigDecimal price) {
        if (price.doubleValue() < 0.0) {
            throw new IllegalArgumentException("商品价格最低为0元");
        }
        this.price = price;
    }

    public void setTag(String tag) {
        // if(tag == null || tag.isEmpty()) {
        // throw new IllegalArgumentException("商品标签不能为空");
        // }
        if (!tag.equals("education") && !tag.equals("literature") && !tag.equals("art") && !tag.equals("management")
                && !tag.equals("history") && !tag.equals("philosophy") && !tag.equals("health")
                && !tag.equals("science")) {
            throw new IllegalArgumentException("商品标签不合法");
        }
        this.tag = tag;
    }

    // 校验允许的枚举值
    private static final Set<String> ALLOWED_CONDITIONS = new HashSet<>(
            Arrays.asList("10_NEW","9_NEW", "8_NEW", "5_NEW", "4_NEW", "OLD")
    );

    public void setCondition(String condition) {
        if (condition == null || !ALLOWED_CONDITIONS.contains(condition)) {
            throw new IllegalArgumentException("商品成色不合法，必须为: 10_NEW, 8_NEW, 5_NEW, 4_NEW, OLD 之一");
        }
        this.condition = condition;
    }

    // 转换VO方法
    public ProductVO toVO() {
        ProductVO productVO = new ProductVO();
        productVO.setId(String.valueOf(this.id));
        productVO.setTitle(this.title);
        productVO.setPrice(BigDecimal.valueOf(this.price.doubleValue()));
        productVO.setRate(this.rate);
        productVO.setDescription(this.description);
        productVO.setCover(this.cover);
        productVO.setDetail(this.detail);
        productVO.setTag(this.tag);
        productVO.setSellerId(this.sellerId);
        productVO.setStatus(this.status);
        // 新增：成色
        productVO.setCondition(this.condition);
        return productVO;
    }
}
package com.example.tomatomall.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FavoriteVO {
    private Integer id;
    private Integer productId;
    private String productTitle;
    private String productCover;
    private BigDecimal price;
    private String description;
    private Double rate;
    private String detail;
    private String tag;
    private Integer sellerId;
    private String status;
    private String condition;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    public FavoriteVO() {
    }

    public FavoriteVO(Integer id, Integer productId, String productTitle, String productCover,
                      BigDecimal price, String description, Double rate, String detail,
                      String tag, Integer sellerId, String status, String condition, LocalDateTime createTime) {
        this.id = id;
        this.productId = productId;
        this.productTitle = productTitle;
        this.productCover = productCover;
        this.price = price;
        this.description = description;
        this.rate = rate;
        this.detail = detail;
        this.tag = tag;
        this.sellerId = sellerId;
        this.status = status;
        this.condition = condition;
        this.createTime = createTime;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public String getProductCover() {
        return productCover;
    }

    public void setProductCover(String productCover) {
        this.productCover = productCover;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Double getRate() {
        return rate;
    }
    public void setRate(Double rate) {
        this.rate = rate;
    }
}
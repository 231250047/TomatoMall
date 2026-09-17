package com.example.tomatomall.vo;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.Specification;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProductVO {
    private String id;
    private String title;
    private BigDecimal price;
    private Double rate;
    private String description;
    private String cover;
    private String detail;
    private SpecificationVO[] specifications;
    private String tag;
    private Integer sellerId;
    private String sellerAvatar;
    private String sellerName;
    private String status;

    // 新增:商品成�?
    private String condition;

    public Product toProductPO() {
        Product product = new Product();
        // 转换 id(假设前端传递的 id 为字符串，需转为 Integer�?
        if (this.id != null && !this.id.isEmpty()) {
            try {
                product.setId(Integer.parseInt(this.id));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("商品ID格式错误");
            }
        }
        product.setTitle(this.title);
        product.setPrice(this.price);
        product.setRate(this.rate);
        product.setDescription(this.description);
        product.setCover(this.cover);
        product.setDetail(this.detail);
        product.setTag(this.tag);
        product.setSellerId(this.sellerId);
        // 新增:传递成色(允许后端校验抛错�?
        if (this.condition != null && !this.condition.isEmpty()) {
            product.setCondition(this.condition);
        }
        return product;
    }

    // �?Specification[] 转换�?List<Specification> 并关联到 Product
    public Specification[] toSpecificationsPO() {
        List<Specification> specList = new ArrayList<>();
        if (this.specifications != null) {
            for (SpecificationVO spec : this.specifications) {
                Specification specification = new Specification();
                if (spec.getProductId() != null && !spec.getProductId().isEmpty()) {
                    try {
                        specification.setProductId(Integer.parseInt(spec.getProductId()));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("商品ID格式错误");
                    }
                }
                specification.setItem(spec.getItem());
                specification.setValue(spec.getValue());
                specList.add(specification);
            }
        }
        return specList.toArray(new Specification[specList.size()]);
    }

}

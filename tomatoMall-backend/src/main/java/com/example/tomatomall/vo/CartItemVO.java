package com.example.tomatomall.vo;

import com.example.tomatomall.po.CartItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CartItemVO {
    private Integer cartItemId;

    private Integer userId;
    private Integer productId;
    private Integer quantity;

    private String title;
    private BigDecimal price;
    private String description;
    private String cover;
    private String detail;

    CartItem toPO() {

        CartItem cartItem = new CartItem();
        cartItem.setCartItemId(this.cartItemId);
        cartItem.setUserId(this.userId);
        cartItem.setProductId(this.productId);
        cartItem.setQuantity(this.quantity);

//        cart.setTitle(this.title);
//        cart.setPrice(this.price);
//        cart.setDescription(this.description);
        return cartItem;
    }
}

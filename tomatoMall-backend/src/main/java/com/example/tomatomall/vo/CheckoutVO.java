package com.example.tomatomall.vo;

import com.alipay.api.domain.ShopAddressInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
public class  CheckoutVO {
    private String requestId;
    List<String> cartItemIds;
    ShopAddress shoppingAddress;//文档给的是Object shipping_address，这里觉得PartAccountVO更为合适，而且shipping_address也不符合java命名规范
    String paymentMethod;//同样是文档不符合java命名规范
    String discount;

}

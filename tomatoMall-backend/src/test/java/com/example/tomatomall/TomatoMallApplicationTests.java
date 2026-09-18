package com.example.tomatomall;
import com.example.tomatomall.controller.OrderController;
import com.example.tomatomall.service.AliPayService;
import com.example.tomatomall.service.serviceImpl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
/** HTTP boundary regressions run without live payment credentials, Redis, or an AI provider. */
class TomatoMallApplicationTests {
    @Test void browserReturnCannotMarkOrderPaid() throws Exception {
        OrderServiceImpl orders=mock(OrderServiceImpl.class);
        OrderController controller=new OrderController(orders,mock(AliPayService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"frontend","http://localhost:5173");
        MockHttpServletResponse response=new MockHttpServletResponse();controller.returnUrl("123",response);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/orders/123");verifyNoInteractions(orders);
    }
    @Test void unsignedPaymentNotificationIsRejected() {
        OrderServiceImpl orders=mock(OrderServiceImpl.class);OrderController controller=new OrderController(orders,mock(AliPayService.class));
        MockHttpServletRequest request=new MockHttpServletRequest();request.setParameter("out_trade_no","123");request.setParameter("trade_status","TRADE_SUCCESS");
        assertThat(controller.notify(request)).isEqualTo("fail");verifyNoInteractions(orders);
    }
    @Test void anotherBuyersPurchaseHistoryIsForbidden() {
        OrderServiceImpl orders=mock(OrderServiceImpl.class);OrderController controller=new OrderController(orders,mock(AliPayService.class));
        MockHttpServletRequest request=new MockHttpServletRequest();request.setAttribute("accountId",1);
        assertThatThrownBy(()->controller.purchased(2,request)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(orders);
    }

    @Test void validSignatureStillRequiresExpectedMerchantAndApp() throws Exception {
        OrderServiceImpl orders=mock(OrderServiceImpl.class);OrderController controller=new OrderController(orders,mock(AliPayService.class));
        java.security.KeyPairGenerator generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        String publicKey=java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String privateKey=java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"publicKey",publicKey);
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"appId","expected-app");
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"sellerId","expected-seller");
        for(String[] identity:new String[][]{{"wrong-app","expected-seller"},{"expected-app","wrong-seller"},{"expected-app","expected-seller"}}) {
            java.util.Map<String,String> params=new java.util.HashMap<>();params.put("app_id",identity[0]);params.put("seller_id",identity[1]);
            params.put("out_trade_no","123");params.put("trade_no","signed-trade");params.put("total_amount","20.00");params.put("trade_status","TRADE_SUCCESS");
            params.put("sign",com.alipay.api.internal.util.AlipaySignature.sign(params,privateKey,"UTF-8","RSA2"));
            MockHttpServletRequest request=new MockHttpServletRequest();params.forEach(request::setParameter);
            boolean expected=identity[0].equals("expected-app")&&identity[1].equals("expected-seller");
            assertThat(controller.notify(request)).isEqualTo(expected?"success":"fail");
        }
        verify(orders,times(1)).updateOrderStatus("123","signed-trade","20.00");
    }
}

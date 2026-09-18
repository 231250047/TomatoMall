package com.example.tomatomall.controller;
import com.alipay.api.internal.util.AlipaySignature;
import com.example.tomatomall.po.AliPayOrder;
import com.example.tomatomall.service.AliPayService;
import com.example.tomatomall.service.serviceImpl.OrderServiceImpl;
import com.example.tomatomall.vo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/orders")
public class OrderController {
    private final OrderServiceImpl orders;
    private final AliPayService alipay;
    @org.springframework.beans.factory.annotation.Autowired private com.example.tomatomall.order.OrderTimeoutHandler timeout;
    @Value("${alipay.alipay-public-key}") private String publicKey;
    @Value("${alipay.app-id}") private String appId;
    @Value("${alipay.seller-id}") private String sellerId;
    @Value("${orders.frontend-url:http://localhost:5173}") private String frontend;
    public OrderController(OrderServiceImpl orders,AliPayService alipay) { this.orders=orders;this.alipay=alipay; }
    private Integer actor(HttpServletRequest request) {
        Integer id=(Integer)request.getAttribute("accountId");
        if(id==null) throw new org.springframework.security.access.AccessDeniedException("请先登录");
        return id;
    }
    @PostMapping("/{orderId}/pay")
    public Response<AliPayOrder> pay(@PathVariable Integer orderId,HttpServletRequest request) {
        orders.requireOwner(orderId,actor(request));return Response.buildSuccess(alipay.createAliPayOrder(orderId));
    }
    @PostMapping(value="/notify",produces="text/plain")
    public String notify(HttpServletRequest request) {
        Map<String,String> params=request.getParameterMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,e->e.getValue()[0]));
        try {
            if(!AlipaySignature.rsaCheckV1(params,publicKey,"UTF-8","RSA2") || !appId.equals(params.get("app_id"))
                || !sellerId.equals(params.get("seller_id"))) return "fail";
            if("TRADE_SUCCESS".equals(params.get("trade_status")) || "TRADE_FINISHED".equals(params.get("trade_status")))
                orders.updateOrderStatus(params.get("out_trade_no"),params.get("trade_no"),params.get("total_amount"));
            return "success";
        } catch(Exception e) { return "fail"; }
    }
    // Browser redirects are untrusted navigation, never proof of payment.
    @GetMapping("/returnUrl")
    public void returnUrl(@RequestParam(required=false) String out_trade_no,HttpServletResponse response) throws java.io.IOException {
        String path=out_trade_no!=null && out_trade_no.matches("[0-9]{1,10}") ? "/orders/"+out_trade_no : "/orders";
        response.sendRedirect(frontend+path);
    }
    @GetMapping("/purchased/{userId}")
    public Response<List<ProductVO>> purchased(@PathVariable Integer userId,HttpServletRequest request) {
        if(!actor(request).equals(userId)) throw new org.springframework.security.access.AccessDeniedException("无权读取他人订单");
        return Response.buildSuccess(orders.getPurchasedProducts(userId));
    }
    @GetMapping("/list")
    public Response<List<OrderVO>> list(HttpServletRequest request) { return Response.buildSuccess(orders.getOrderList(actor(request))); }
    @GetMapping("/{orderId}")
    public Response<OrderVO> detail(@PathVariable Integer orderId,HttpServletRequest request) {
        orders.requireOwner(orderId,actor(request));return Response.buildSuccess(orders.getOrderDetail(orderId));
    }
    @PostMapping("/{orderId}/cancel")
    public Response<String> cancel(@PathVariable Integer orderId,HttpServletRequest request) {
        orders.requestCancellation(orderId,actor(request));
        timeout.handle(orderId);
        return Response.buildSuccess("已提交取消申请，请刷新查看订单状态");
    }
    @DeleteMapping("/{orderId}")
    public Response<String> delete(@PathVariable Integer orderId,HttpServletRequest request) {
        orders.requireOwner(orderId,actor(request));orders.deleteOrder(orderId);return Response.buildSuccess("已隐藏订单，交易记录保留");
    }
}

package com.example.tomatomall.service.serviceImpl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.AliPayOrder;
import com.example.tomatomall.po.Order;
import com.example.tomatomall.repository.OrderRepository;
import com.example.tomatomall.service.AliPayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AliPayServiceImpl implements AliPayService, com.example.tomatomall.order.PaymentGateway {
    @Value("${alipay.app-id}")
    private String appId;
    @Value("${alipay.private-key}")
    private String privateKey;
    @Value("${alipay.alipay-public-key}")
    private String alipayPublicKey;
    @Value("${alipay.server-url}")
    private String serverUrl;
    @Value("${alipay.charset}")
    private String charset;
    @Value("${alipay.sign-type}")
    private String signType;
    @Value("${alipay.notify-url}")
    private String notifyUrl;
    @Value("${alipay.return-url}")
    private String returnUrl;
    private static final String FORMAT = "JSON";
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderServiceImpl orderService;
    @Autowired
    private com.example.tomatomall.Util.SecurityUtil securityUtil;
    @Override
    public AliPayOrder createAliPayOrder(Integer orderId) {
        // 1. 创建Client，通用SDK提供的Client，负责调用支付宝的API
        AlipayClient alipayClient = new DefaultAlipayClient(serverUrl, appId,
                privateKey, FORMAT, charset, alipayPublicKey, signType);
        // 2. 创建 Request并设置Request参数
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();  // 发送请求的 Request�?
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);
        if(!orderRepository.existsById(orderId)){
            throw TomatoMallException.orderNotExist();
        }
        Order order = orderService.beginPayment(orderId, securityUtil.getCurrentAccount().getId());
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", order.getOrderId());  // 我们自己生成的订单编�?
        bizContent.put("total_amount", order.getTotalAmount()); // 订单的总金�?
        bizContent.put("subject", "订单");   // 支付的名�?
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");  // 固定配置
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        bizContent.put("time_expire", format.format(order.getExpiresAt()));
        request.setBizContent(bizContent.toString());
        // 执行请求，拿到响应的结果，返回给浏览�?
        String form = "";
        try {
            form = alipayClient.pageExecute(request).getBody(); // 调用SDK生成表单
        } catch (AlipayApiException e) {
            // Leave the reservation and deadline intact; payment form generation can be retried.
            throw TomatoMallException.aliPayError();
        }
        AliPayOrder aliPayOrder = new AliPayOrder();
        aliPayOrder.setOrderId(order.getOrderId());
        aliPayOrder.setPaymentForm(form);
        aliPayOrder.setTotalAmount(order.getTotalAmount());
        aliPayOrder.setPaymentMethod("Alipay");
        return aliPayOrder;
    }

    @Override
    public Resolution resolveAndClose(Order order) {
        AlipayClient client=new DefaultAlipayClient(serverUrl,appId,privateKey,FORMAT,charset,alipayPublicKey,signType);
        JSONObject data=new JSONObject();data.put("out_trade_no",order.getOrderId().toString());
        com.alipay.api.request.AlipayTradeQueryRequest query=new com.alipay.api.request.AlipayTradeQueryRequest();
        query.setBizContent(data.toJSONString());
        try {
            var result=client.execute(query);
            if(!result.isSuccess()) return Resolution.unknown();
            if(!order.getOrderId().toString().equals(result.getOutTradeNo())) return Resolution.unknown();
            if("TRADE_SUCCESS".equals(result.getTradeStatus()) || "TRADE_FINISHED".equals(result.getTradeStatus()))
                return new Resolution(State.PAID,result.getTradeNo(),result.getTotalAmount());
            if("TRADE_CLOSED".equals(result.getTradeStatus())) return Resolution.closed();
            if(!"WAIT_BUYER_PAY".equals(result.getTradeStatus())) return Resolution.unknown();
            com.alipay.api.request.AlipayTradeCloseRequest close=new com.alipay.api.request.AlipayTradeCloseRequest();
            close.setBizContent(data.toJSONString());
            var closed=client.execute(close);
            // A close failure may mean a concurrent payment won; query on the next retry.
            return closed.isSuccess() && order.getOrderId().toString().equals(closed.getOutTradeNo())
                ? Resolution.closed():Resolution.unknown();
        } catch(AlipayApiException e) { return Resolution.unknown(); }
    }
}

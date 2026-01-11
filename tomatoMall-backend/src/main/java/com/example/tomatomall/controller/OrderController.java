package com.example.tomatomall.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.example.tomatomall.po.AliPayOrder;
import com.example.tomatomall.service.AliPayService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.vo.OrderVO;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    @Value("${alipay.alipay-public-key}")
    private String ALIPAY_PUBLIC_KEY;
    @Autowired
    private OrderService orderService;
    @Autowired
    private AliPayService aliPayService;
    @Autowired
    private ProductService productService;

    // vvvyyv9548@sandbox.com 支付邮箱
    @PostMapping("/{orderId}/pay") // subject=xxx&traceNo=xxx&totalAmount=xxx
    public Response<AliPayOrder> createAliPayOrder(@PathVariable Integer orderId) {
        return Response.buildSuccess(aliPayService.createAliPayOrder(orderId));
    }

    @PostMapping("/notify") // 注意这里必须是POST接口
    public void handleAlipayNotify(HttpServletRequest request, HttpServletResponse response)
            throws IOException, AlipayApiException {
        System.out.println("notify");

        // 1. 解析支付宝回调参数（通常是 application/x-www-form-urlencoded）
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0]));

        // 2. 验证支付宝签名（防止伪造请求）
        boolean signVerified = AlipaySignature.rsaCheckV1(params, ALIPAY_PUBLIC_KEY, "UTF-8", "RSA2");
        if (!signVerified) {
            response.getWriter().print("fail"); // 签名验证失败，返回 fail
            return;
        }

        // 3. 处理业务逻辑（更新订单、减库存等）
        String tradeStatus = params.get("trade_status");
        if ("TRADE_SUCCESS".equals(tradeStatus)) {
            String orderId = params.get("out_trade_no"); // 您的订单号
            String alipayTradeNo = params.get("trade_no"); // 支付宝交易号
            String amount = params.get("total_amount"); // 支付金额

            System.out.println("订单支付成功，订单号：" + orderId);
            // 更新订单状态（注意幂等性，防止重复处理）
            // 注意：updateOrderStatus 内部负责一次性减库存，Controller 不应再重复调用 reduceStock
            orderService.updateOrderStatus(orderId, alipayTradeNo, amount);

        }

        // 4. 必须返回纯文本的 "success"（支付宝要求）
        response.getWriter().print("success");
    }

    @GetMapping("/returnUrl")
    public String returnUrl() {
        return "支付成功了";
    }

    // Get purchased products by user ID
    @GetMapping("/purchased/{userId}")
    public Response<List<ProductVO>> getPurchasedProducts(@PathVariable Integer userId) {
        List<ProductVO> products = orderService.getPurchasedProducts(userId);
        return Response.buildSuccess(products);
    }

    /**
     * 获取用户的订单列表
     */
    @GetMapping("/list")
    public Response<List<OrderVO>> getOrderList(HttpServletRequest request) {
        // 从请求中获取用户 ID（假设通过拦截器设置了 userId）
        Integer userId = (Integer) request.getAttribute("accountId");

        // 调用服务层获取订单列表
        List<OrderVO> orderList = orderService.getOrderList(userId);

        return Response.buildSuccess(orderList);
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/{orderId}")
    public Response<OrderVO> getOrderDetail(@PathVariable Integer orderId) {
        OrderVO orderDetail = orderService.getOrderDetail(orderId);
        return Response.buildSuccess(orderDetail);
    }

    /**
     * 删除订单
     */
    @DeleteMapping("/{orderId}")
    public Response<String> deleteOrder(@PathVariable Integer orderId) {
        try {
            orderService.deleteOrder(orderId);
            return Response.buildSuccess("订单删除成功");
        } catch (Exception e) {
            return Response.buildFailure("订单删除失败: " + e.getMessage(), "500");
        }
    }
}
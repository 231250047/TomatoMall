package com.example.tomatomall.service.serviceImpl;
import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.*;
import com.example.tomatomall.repository.*;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.vo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.security.MessageDigest;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired CartItemRepository cartItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired StockpileRepository stockpileRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired OutboxEventRepository outbox;
    private static final long TIMEOUT_MS=30*60*1000L;

    @Override @Transactional
    public OrderVO createOrder(String userName, CheckoutVO request) {
        if(request.getRequestId()==null || !request.getRequestId().matches("[a-zA-Z0-9_-]{8,64}"))
            throw new IllegalArgumentException("下单需要 8–64 位 requestId，重试时请保持不变");
        if(request.getCartItemIds()==null || request.getCartItemIds().isEmpty() || request.getCartItemIds().size()>100)
            throw new IllegalArgumentException("请选择 1–100 个购物项");
        List<Integer> ids=request.getCartItemIds().stream().map(Integer::valueOf).sorted().toList();
        if(new HashSet<>(ids).size()!=ids.size()) throw new IllegalArgumentException("购物项不能重复");
        ShopAddress address=request.getShoppingAddress();
        if(address==null || blank(address.getName()) || blank(address.getPhone()) || blank(address.getAddress()))
            throw new IllegalArgumentException("请填写完整收货信息");
        if(!"ALIPAY".equalsIgnoreCase(request.getPaymentMethod())) throw new IllegalArgumentException("暂只支持支付宝");
        // Serialize checkouts for one buyer; different buyers only contend on the stock rows they need.
        Account account=accountRepository.lockByUsername(userName).orElseThrow(()->new IllegalArgumentException("用户不存在"));
        String hash=fingerprint(ids,address,request.getPaymentMethod());
        Optional<Order> previous=orderRepository.findByUserIdAndRequestId(account.getId(),request.getRequestId());
        if(previous.isPresent()) {
            if(!hash.equals(previous.get().getRequestHash())) throw new IllegalArgumentException("requestId 已被不同请求使用");
            OrderVO v=previous.get().partToVO();v.setUsername(userName);return v;
        }
        List<CartItem> carts=new ArrayList<>();
        for(Integer id:ids) {
            CartItem c=cartItemRepository.lockById(id).orElseThrow(TomatoMallException::cartItemNotExist);
            if(!account.getId().equals(c.getUserId())) throw new IllegalArgumentException("不能结算他人的购物项");
            if(c.getQuantity()==null || c.getQuantity()<=0) throw new IllegalArgumentException("购买数量必须大于 0");
            carts.add(c);
        }
        carts.sort(Comparator.comparing(CartItem::getProductId));
        BigDecimal total=BigDecimal.ZERO;
        List<OrderItem> snapshots=new ArrayList<>();
        for(CartItem cart:carts) {
            Product p=productRepository.findById(cart.getProductId()).orElseThrow(TomatoMallException::productNotExist);
            if(!"available".equals(p.getStatus())) throw new IllegalArgumentException("商品已下架");
            if(stockpileRepository.reserve(p.getId(),cart.getQuantity())!=1) throw TomatoMallException.stockpileNotEnough();
            OrderItem item=new OrderItem();item.setProductId(p.getId());item.setTitle(p.getTitle());
            item.setCover(p.getCover());item.setPrice(p.getPrice());item.setQuantity(cart.getQuantity());snapshots.add(item);
            total=total.add(p.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
            event(OutboxEvent.Kind.STOCK_INVALIDATE,p.getId(),new Date());
        }
        // Existing full-reduction offers are now computed by the server; client discount is only a legacy field.
        BigDecimal reduction=total.compareTo(new BigDecimal("300"))>=0?new BigDecimal("50"):
            total.compareTo(new BigDecimal("200"))>=0?new BigDecimal("30"):
            total.compareTo(new BigDecimal("50"))>=0?new BigDecimal("5"):BigDecimal.ZERO;
        Order order=new Order();order.setUserId(account.getId());order.setTotalAmount(total.subtract(reduction));
        order.setPaymentMethod("ALIPAY");order.setCreateTime(new Date());
        order.setExpiresAt(new Date(order.getCreateTime().getTime()+TIMEOUT_MS));order.setNextCheckAt(order.getExpiresAt());
        order.setRequestId(request.getRequestId());order.setRequestHash(hash);
        order.setReceiverName(address.getName());order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(address.getAddress());order.setReceiverPostalCode(address.getPostalCode());
        orderRepository.save(order);
        for(OrderItem item:snapshots) { item.setOrderId(order.getOrderId());orderItemRepository.save(item); }
        // Consume the selected cart rows in the same transaction, never use mutable carts for payment/release.
        cartItemRepository.deleteAll(carts);
        event(OutboxEvent.Kind.CART_INVALIDATE,account.getId(),new Date());
        event(OutboxEvent.Kind.ORDER_TIMEOUT,order.getOrderId(),order.getExpiresAt());
        OrderVO v=order.partToVO();v.setUsername(userName);return v;
    }
    private static boolean blank(String s) { return s==null || s.isBlank(); }
    private String fingerprint(List<Integer> ids,ShopAddress a,String payment) {
        try {
            byte[] bytes=new ObjectMapper().writeValueAsBytes(Arrays.asList(ids,a.getName(),a.getPhone(),a.getAddress(),a.getPostalCode(),payment.toUpperCase(Locale.ROOT)));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch(Exception e) { throw new IllegalArgumentException("无法解析下单参数",e); }
    }
    private void event(OutboxEvent.Kind kind,Integer id,Date time) { outbox.save(OutboxEvent.create(kind,id,time)); }
    private Order lock(Integer id) { return orderRepository.lockById(id).orElseThrow(TomatoMallException::orderNotExist); }
    public void requireOwner(Integer id,Integer userId) {
        Order o=orderRepository.findById(id).orElseThrow(TomatoMallException::orderNotExist);
        if(!o.getUserId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("无权操作该订单");
    }
    @Transactional
    public Order beginPayment(Integer id,Integer userId) {
        Order o=lock(id);
        if(!o.getUserId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("无权支付该订单");
        if(o.getStatus()!=Order.OrderStatus.PENDING || o.getExpiresAt()==null || !o.getExpiresAt().after(new Date()))
            throw new IllegalArgumentException("订单已过期或不可支付");
        o.setPaymentAttempted(true);return o;
    }
    @Override @Transactional
    public void updateOrderStatus(String orderId,String tradeNo,String amount) {
        Order o=lock(Integer.valueOf(orderId));
        if(blank(tradeNo) || o.getTotalAmount().compareTo(new BigDecimal(amount))!=0)
            throw new IllegalArgumentException("支付交易号或金额不匹配");
        if(o.getTradeNo()!=null && !o.getTradeNo().equals(tradeNo)) throw new IllegalArgumentException("交易号不匹配");
        if(o.getStatus()==Order.OrderStatus.SUCCESS) return;
        if(o.getStatus()!=Order.OrderStatus.PENDING && o.getStatus()!=Order.OrderStatus.CLOSING) {
            // Keep a durable reconciliation signal; never revive released stock or silently lose a paid receipt.
            o.setTradeNo(tradeNo);o.setPaymentIncident("PAID_AFTER_CLOSE: refund/reconciliation required");return;
        }
        changeStock(o,true);
        o.setTradeNo(tradeNo);o.setPaymentTime(new Date());o.setStatus(Order.OrderStatus.SUCCESS);o.setNextCheckAt(null);o.setPaymentIncident(null);
    }
    private void changeStock(Order order,boolean paid) {
        List<OrderItem> items=orderItemRepository.findByOrderId(order.getOrderId());
        if(items.isEmpty()) throw new IllegalStateException("订单缺少快照，请人工核对旧订单");
        items.sort(Comparator.comparing(OrderItem::getProductId));
        for(OrderItem item:items) {
            int changed=paid?stockpileRepository.consume(item.getProductId(),item.getQuantity()):stockpileRepository.release(item.getProductId(),item.getQuantity());
            if(changed!=1) throw new IllegalStateException("库存预占不一致，事务回滚，禁止修正为 0");
            event(OutboxEvent.Kind.STOCK_INVALIDATE,item.getProductId(),new Date());
        }
    }
    @Transactional
    public Order prepareTimeout(Integer id) {
        Order o=lock(id);Date now=new Date();
        if(!o.isCancelRequested() && (o.getExpiresAt()==null || o.getExpiresAt().after(now))) return null;
        if(o.getStatus()!=Order.OrderStatus.PENDING && o.getStatus()!=Order.OrderStatus.CLOSING) return null;
        o.setStatus(Order.OrderStatus.CLOSING);o.setNextCheckAt(new Date(now.getTime()+60_000));
        o.setCloseAttempts(o.getCloseAttempts()+1);
        if(o.getCloseAttempts()>=10) o.setPaymentIncident("CLOSE_RETRY_EXHAUSTED: verify provider before releasing stock");
        return o;
    }
    @Transactional
    public void requestCancellation(Integer id,Integer userId) {
        Order o=lock(id);
        if(!o.getUserId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("无权取消该订单");
        if(o.getStatus()==Order.OrderStatus.SUCCESS) throw new IllegalArgumentException("已支付订单应走退款流程");
        if(o.getStatus()==Order.OrderStatus.CANCELLED || o.getStatus()==Order.OrderStatus.TIMEOUT) return;
        if(o.getStatus()!=Order.OrderStatus.PENDING && o.getStatus()!=Order.OrderStatus.CLOSING) throw new IllegalArgumentException("当前状态不能取消");
        o.setCancelRequested(true);o.setStatus(Order.OrderStatus.CLOSING);o.setNextCheckAt(new Date());
    }
    @Transactional
    public void finishTimeout(Integer id) {
        Order o=lock(id);
        if(o.getStatus()!=Order.OrderStatus.CLOSING) return;
        changeStock(o,false);o.setStatus(o.isCancelRequested()?Order.OrderStatus.CANCELLED:Order.OrderStatus.TIMEOUT);o.setNextCheckAt(null);o.setPaymentIncident(null);
    }
    @Override
    @Transactional(readOnly = true)
    public List<ProductVO> getPurchasedProducts(Integer userId) {
        // 查询用户的所有成功订�?
        List<Order> orders = orderRepository.findByUserIdAndStatus(userId, Order.OrderStatus.SUCCESS);

        List<ProductVO> purchasedProducts = new ArrayList<>();
        Set<Integer> productIdSet = new HashSet<>(); // 用于去重

        for (Order order : orders) {
            // 查询订单下的所有商品快�?
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getOrderId());
            for (OrderItem orderItem : orderItems) {
                if (!productIdSet.contains(orderItem.getProductId())) {
                    ProductVO productVO = new ProductVO();
                    productVO.setId(String.valueOf(orderItem.getProductId()));
                    productVO.setTitle(orderItem.getTitle());
                    productVO.setCover(orderItem.getCover());
                    productVO.setPrice(orderItem.getPrice());
                    purchasedProducts.add(productVO);
                    productIdSet.add(orderItem.getProductId());
                }
            }
        }
        return purchasedProducts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderVO> getOrderList(Integer userId) {
        // 打印传入的用�?ID

        // 查询用户的所有订�?
        List<Order> orders = orderRepository.findByUserId(userId);

        // 打印查询到的订单列表

        // 转换�?OrderVO 列表
        List<OrderVO> orderVOList = new ArrayList<>();
        for (Order order : orders) {
            if (order.isHidden()) continue;
            OrderVO orderVO = order.partToVO();
            // 打印每个订单的基本信�?

            // 填充用户�?
            String username = accountRepository.findById(userId).get().getUsername();
            orderVO.setUsername(username);

            // 打印转换后的 OrderVO

            orderVOList.add(orderVO);
        }

        // 打印最终的 OrderVO 列表

        return orderVOList;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderVO getOrderDetail(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> TomatoMallException.orderNotExist());

        OrderVO orderVO = order.partToVO();
        orderVO.setUsername(accountRepository.findById(order.getUserId()).get().getUsername());

        // 填充收货信息
        orderVO.setReceiverName(order.getReceiverName());
        orderVO.setReceiverPhone(order.getReceiverPhone());
        orderVO.setReceiverAddress(order.getReceiverAddress());
        orderVO.setReceiverPostalCode(order.getReceiverPostalCode());

        // 查询订单商品
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        List<OrderItemVO> items = new ArrayList<>();
        for (OrderItem orderItem : orderItems) {
            OrderItemVO itemVO = new OrderItemVO(
                    orderItem.getProductId(),
                    orderItem.getTitle(),
                    orderItem.getCover(),
                    orderItem.getPrice(),
                    orderItem.getQuantity()
            );
            items.add(itemVO);
        }
        orderVO.setItems(items);

        return orderVO;
    }

    @Override @Transactional
    public void deleteOrder(Integer orderId) {
        Order o=lock(orderId);
        if(o.getStatus()==Order.OrderStatus.PENDING || o.getStatus()==Order.OrderStatus.CLOSING)
            throw new IllegalArgumentException("进行中的订单不能删除，请等待支付或超时关单");
        o.setHidden(true);
    }
}

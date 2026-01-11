package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.Util.SecurityUtil;
import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.CartItem;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.Stockpile;
import com.example.tomatomall.repository.CartItemRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockpileRepository;
import com.example.tomatomall.service.CartService;
import com.example.tomatomall.vo.CartItemVO;
import com.example.tomatomall.vo.CartListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CartServiceImpl implements CartService {

    // 进程内按 productId 的轻量锁，避免同一实例中并发超卖（跨 JVM 无效）
    private static final ConcurrentHashMap<Integer, Object> productLocks = new ConcurrentHashMap<>();

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockpileRepository stockpileRepository;

    @Autowired
    SecurityUtil securityUtil;

    @Autowired
    private ProductServiceImpl productService;

    @Override
    @Transactional
    public String addToCart(String productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        Account account = securityUtil.getCurrentAccount();
        Integer userId = account.getId();

        Integer productIdInt = Integer.parseInt(productId);

        // 检查 product 是否存在
        Product product = productRepository.findById(productIdInt)
                .orElseThrow(TomatoMallException::productNotExist);

        // 事务内重新读取库存状态，避免先前检查后库存变化导致的问题
        Stockpile stockpile = stockpileRepository.findByProductId(productIdInt);
        if (stockpile == null) {
            throw TomatoMallException.stockpileNotExist();
        }

        // 进程内对同一商品进行串行化处理，降低并发超卖
        Object lock = productLocks.computeIfAbsent(productIdInt, k -> new Object());
        synchronized (lock) {
            // 重新读取当前用户已有购物项（事务内）
            Optional<CartItem> existingOpt = cartItemRepository.findByUserIdAndProductId(userId, productIdInt);
            int existingQty = existingOpt.map(CartItem::getQuantity).orElse(0);
            int newTotal = existingQty + quantity;

            if (newTotal > stockpile.getAmount()) {
                throw TomatoMallException.stockpileNotEnough();
            }

            if (existingOpt.isPresent()) {
                CartItem item = existingOpt.get();
                item.setQuantity(newTotal);
                cartItemRepository.save(item);
            } else {
                CartItem newItem = new CartItem();
                newItem.setUserId(userId);
                newItem.setProductId(productIdInt);
                newItem.setQuantity(quantity);
                
                try {
                    cartItemRepository.save(newItem);
                } catch (DataIntegrityViolationException e) {
                    // 唯一约束冲突（并发插入），重新查询并更新数量
                    CartItem existing = cartItemRepository.findByUserIdAndProductId(userId, productIdInt)
                            .orElseThrow(() -> new RuntimeException("并发插入失败，无法找到已存在项"));
                    existing.setQuantity(existing.getQuantity() + quantity);
                    
                    // 再次检查库存
                    Stockpile recheck = stockpileRepository.findByProductId(productIdInt);
                    if (recheck != null && existing.getQuantity() > recheck.getAmount()) {
                        throw TomatoMallException.stockpileNotEnough();
                    }
                    
                    cartItemRepository.save(existing);
                }
            }
        }

        return "成功添加到购物车";
    }

    // 删除购物车整个商品，注意不是数量减少
    @Override
    @Transactional
    public String deleteCartItem(String cartItemId) {
        Integer id= Integer.parseInt(cartItemId);
        if (!cartItemRepository.existsById(id)) {
            throw TomatoMallException.cartItemNotExist();
        }
        cartItemRepository.deleteById(id);
        return "购物车商品删除成功";
    }

    // 更新购物车商品数量,注意不能超过库存
    @Override
    @Transactional
    public String updateQuantity(String cartItemId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("quantity must be >= 0");
        }
        Integer id = Integer.parseInt(cartItemId);

        Account account = securityUtil.getCurrentAccount();
        Integer userId = account.getId();

        CartItem item = cartItemRepository.findById(id)
                .orElseThrow(TomatoMallException::cartItemNotExist);

        if (!item.getUserId().equals(userId)) {
            // 防止越权修改（如果需要更严格检查，可抛出特定异常）
            throw new IllegalArgumentException("非法操作");
        }

        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(TomatoMallException::productNotExist);

        // 事务内重新读取库存
        Stockpile stockpile = stockpileRepository.findByProductId(product.getId());
        if (stockpile == null) {
            throw TomatoMallException.stockpileNotExist();
        }

        if (quantity > stockpile.getAmount()) {
            throw TomatoMallException.stockpileNotEnough();
        }
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return "购物车商品数量修改成功";
    }

    @Override
    @Transactional(readOnly = true)
    public CartListVO listCartItems() {
        Account account = securityUtil.getCurrentAccount();
        Integer userId = account.getId();

        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);

        List<CartItemVO> itemVOs = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem item : cartItems) {
            Optional<Product> productOptional = productRepository.findById(item.getProductId());

            // 如果商品不存在，跳过当前商品
            if (!productOptional.isPresent()) {
                continue; // 直接跳到下一个商品
            }

            Product product = productOptional.get();

            CartItemVO vo = toVO(item, product);
            vo.setUserId(userId);
            itemVOs.add(vo);

            // 小计 = 单价 × 数量
            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);
        }

        CartListVO result = new CartListVO();
        result.setItems(itemVOs);
        result.setTotal(itemVOs.size());  // 商品种类数
        result.setTotalAmount(totalAmount);

        return result;
    }


    private CartItemVO toVO(CartItem item, Product product) {
        CartItemVO vo = new CartItemVO();
        vo.setCartItemId(item.getCartItemId());
        vo.setProductId(item.getProductId());
        vo.setTitle(product.getTitle());
        vo.setPrice(product.getPrice());
        vo.setDescription(product.getDescription());
        vo.setCover(product.getCover());
        vo.setDetail(product.getDetail());
        vo.setQuantity(item.getQuantity());
        return vo;
    }
}

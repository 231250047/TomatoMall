package com.example.tomatomall.controller;

import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.Specification;
import com.example.tomatomall.po.Stockpile;
import com.example.tomatomall.service.*;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.StockpileVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 商品控制器
 * 
 * 【Redis 集成说明】
 * 1. 商品详情：使用 Hash 缓存，支持缓存穿透/击穿/雪崩防护
 * 2. 商品浏览：使用 ZSet 记录热度，支持热门商品排行
 * 3. PV/UV 统计：使用 String + HyperLogLog 统计访问量
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Resource
    private ProductService productService;

    @Resource
    private SpecificationService specificationService;
    
    // ========== Redis 缓存服务注入 ==========
    
    @Autowired
    private ProductCacheService productCacheService;
    
    @Autowired
    private HotRankService hotRankService;
    
    @Autowired
    private StatisticsService statisticsService;
    
    @Autowired
    private StockCacheService stockCacheService;

    @GetMapping()
    public Response<ProductVO[]> getProducts() {
        return Response.buildSuccess(productService.getProducts());
    }

    /**
     * 获取商品详情
     * 
     * 【Redis 功能】
     * 1. 优先从 Redis Hash 缓存获取商品详情
     * 2. 记录商品浏览热度（ZSet）
     * 3. 记录 PV/UV 统计
     */
    @GetMapping("/{id}")
    public Response<ProductVO> getProduct(@PathVariable String id, HttpServletRequest request) {
        try {
            // 【Redis】从缓存获取商品详情（包含缓存穿透/击穿/雪崩防护）
            ProductVO product = productCacheService.getProductWithCache(id);
            
            if (product == null) {
                return Response.buildFailure("商品不存在", "404");
            }
            
            // 【Redis】记录商品浏览热度（ZSet ZINCRBY）
            hotRankService.recordProductView(Integer.parseInt(id));
            
            // 【Redis】记录 PV（String INCR）
            statisticsService.incrProductPV(Integer.parseInt(id));
            
            // 【Redis】记录 UV（HyperLogLog PFADD）
            String userId = getVisitorId(request);
            statisticsService.recordProductUV(Integer.parseInt(id), userId);
            
            return Response.buildSuccess(product);
        } catch (TomatoMallException e) {
            return Response.buildFailure(e.getMessage(), "400");
        }
    }

    /**
     * 更新商品
     * 
     * 【Redis 功能】
     * 更新后删除缓存，保证数据一致性（Cache Aside Pattern）
     */
    @PutMapping()
    public Response<String> updateProduct(@RequestBody ProductVO productVO) {
        try {
            Product product = productVO.toProductPO();
            productService.updateProduct(product); // 1. 先更新数据库
            
            // 2. 再删除缓存（Cache Aside）
            // 【Redis】删除商品缓存，保证数据一致性
            if (productVO.getId() != null) {
                productCacheService.deleteProductCache(productVO.getId().toString());
            }
        } catch (TomatoMallException e) {
            return Response.buildFailure(e.getMessage(), "400");
        }
        if (productVO.getSpecifications() != null) {
            Specification[] specifications = productVO.toSpecificationsPO();
            for (Specification specification : specifications) {
                specificationService.updateSpecification(specification);
            }
        }
        return Response.buildSuccess("更新成功");
    }

    @PostMapping()
    public Response<ProductVO> addProduct(@RequestBody ProductVO productVO) {
        System.out.println("接收到的 ProductVO: " + productVO);
        System.out.println("ProductVO.sellerId: " + productVO.getSellerId());
        Product product = productVO.toProductPO();
        System.out.println("转换后的 Product.sellerId: " + product.getSellerId());
        Boolean success = productService.addProduct(product);
        if (!success)
            return Response.buildFailure("商品已存在", "400");
        if (productVO.getSpecifications() != null) {
            Specification[] specifications = productVO.toSpecificationsPO();
            int product_id = Integer.parseInt(productService.getProductByTitle(productVO.getTitle()).getId());
            for (Specification specification : specifications) {
                specification.setProductId(product_id);
                specificationService.addSpecification(specification);
            }
        }
        return Response.buildSuccess(productService.getProductByTitle(productVO.getTitle()));
    }

    /**
     * 删除商品
     * 
     * 【Redis 功能】
     * 删除商品时同时清理相关缓存
     */
    @DeleteMapping("/{id}")
    public Response<String> deleteProduct(@PathVariable String id) {
        try {
            productService.deleteProduct(id);
            
            // 【Redis】删除商品相关缓存
            productCacheService.deleteProductCache(id);
            stockCacheService.deleteStockCache(Integer.parseInt(id));
        } catch (TomatoMallException e) {
            return Response.buildFailure(e.getMessage(), "400");
        }
        return Response.buildSuccess("删除成功");
    }

    /**
     * 更新库存
     * 
     * 【Redis 功能】
     * 更新后同步 Redis 库存缓存
     */
    @PatchMapping("/stockpile/{productId}")
    public Response<String> updateStockpile(@PathVariable String productId, @RequestBody Stockpile stockpile) {
        try {
            Integer amountInt = Integer.valueOf(stockpile.getAmount());
            productService.updateStockpile(productId, amountInt);
            
            // 【Redis】同步库存到 Redis
            stockCacheService.syncStock(Integer.parseInt(productId));
        } catch (TomatoMallException e) {
            return Response.buildFailure(e.getMessage(), "400");
        }
        return Response.buildSuccess("调整库存成功");
    }

    @GetMapping("/stockpile/{productId}")
    public Response<StockpileVO> getStockpile(@PathVariable String productId) {
        return Response.buildSuccess(productService.findStockpile(productId).toVO());
    }

    // Get products by category
    @GetMapping("tag/{tag}")
    public Response<List<ProductVO>> getProductsByTag(@PathVariable String tag) {
        List<ProductVO> products = productService.getProductsByTag(tag);
        return Response.buildSuccess(products);
    }

    // Get top 10 ranked products
    @GetMapping("/totalRank")
    public Response<List<ProductVO>> getTopRankedProducts() {
        List<ProductVO> products = productService.getTopRankedProducts();
        return Response.buildSuccess(products);
    }

    // Get top 10 ranked products by category
    @GetMapping("/rank/{tag}")
    public Response<List<ProductVO>> getTopRankedProductsByTag(@PathVariable String tag) {
        List<ProductVO> products = productService.getTopRankedProductsByTag(tag);
        return Response.buildSuccess(products);
    }

    // Get products by seller ID
    @GetMapping("/seller/{sellerId}")
    public Response<List<ProductVO>> getProductsBySellerId(@PathVariable Integer sellerId) {
        List<ProductVO> products = productService.getProductsBySellerId(sellerId);
        return Response.buildSuccess(products);
    }

    @GetMapping("/newArrivals")
    public Response<List<ProductVO>> getNewArrivals() {
        // 调用 Service 的 getNewArrivals()
        List<ProductVO> products = productService.getNewArrivals();
        return Response.buildSuccess(products);
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 获取访客标识
     * 用于 UV 统计，优先使用用户ID，否则使用 Session ID
     */
    private String getVisitorId(HttpServletRequest request) {
        // 尝试从 Session 获取当前用户
        Object account = request.getSession().getAttribute("currentAccount");
        if (account != null) {
            try {
                return "user:" + account.toString();
            } catch (Exception e) {
                // 忽略
            }
        }
        // 使用 Session ID 作为访客标识
        return "session:" + request.getSession().getId();
    }
}

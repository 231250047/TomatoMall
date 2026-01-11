package com.example.tomatomall.controller;

import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.Specification;
import com.example.tomatomall.po.Stockpile;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.service.SpecificationService;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.StockpileVO;
import com.example.tomatomall.vo.Response;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Resource
    private ProductService productService;

    @Resource
    private SpecificationService specificationService;

    @GetMapping()
    public Response<ProductVO[]> getProducts() {
        return Response.buildSuccess(productService.getProducts());
    }

    @GetMapping("/{id}")
    public Response<ProductVO> getProduct(@PathVariable String id) {
        try {
            productService.getProduct(id);
        } catch (TomatoMallException e) {
            return Response.buildFailure(e.getMessage(), "400");
        }
        return Response.buildSuccess(productService.getProduct(id));
    }

    @PutMapping()
    public Response<String> updateProduct(@RequestBody ProductVO productVO) {
        try {
            Product product = productVO.toProductPO();
            productService.updateProduct(product);
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

    @DeleteMapping("/{id}")
    public Response<String> deleteProduct(@PathVariable String id) {
        try {
            productService.deleteProduct(id);
        } catch (TomatoMallException e) {
            return Response.buildFailure(e.getMessage(), "400");
        }
        return Response.buildSuccess("删除成功");
    }

    @PatchMapping("/stockpile/{productId}")
    public Response<String> updateStockpile(@PathVariable String productId, @RequestBody Stockpile stockpile) {
        try {
            Integer amountInt = Integer.valueOf(stockpile.getAmount());
            productService.updateStockpile(productId, amountInt);
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
}

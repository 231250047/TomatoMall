package com.example.tomatomall.service;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.Stockpile;
import com.example.tomatomall.vo.ProductVO;

import java.util.List;

public interface ProductService {
    ProductVO[] getProducts();

    ProductVO getProduct(String id);

    Boolean updateProduct(Product product);

    Boolean addProduct(Product product);

    Boolean deleteProduct(String id);

    Boolean updateStockpile(String productId, Integer stockpile);

    Stockpile findStockpile(String productId);

    ProductVO getProductByTitle(String title);

    List<ProductVO> getProductsByTag(String tag);

    List<ProductVO> getTopRankedProducts();

    List<ProductVO> getTopRankedProductsByTag(String tag);

    List<ProductVO> getProductsBySellerId(Integer sellerId);

    /**
     * 获取近两个月上新的商品列�?
     * @return List<ProductVO>
     */
    List<ProductVO> getNewArrivals();
}

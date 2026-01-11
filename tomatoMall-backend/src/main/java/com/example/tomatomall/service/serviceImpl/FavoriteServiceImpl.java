package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Favorite;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.FavoriteRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.FavoriteService;
import com.example.tomatomall.vo.FavoriteVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private ProductRepository productRepository;

    @Override
    @Transactional
    public Response<String> addFavorite(Integer accountId, Integer productId) {
        try {
            // 检查商品是否存在
            Optional<Product> productOptional = productRepository.findById(productId);
            if (!productOptional.isPresent()) {
                return Response.buildFailure("商品不存在", "404");
            }

            // 检查是否已经收藏
            if (favoriteRepository.existsByAccountIdAndProductId(accountId, productId)) {
                return Response.buildFailure("商品已在收藏列表中", "400");
            }

            // 添加收藏
            Favorite favorite = new Favorite(accountId, productId);
            favoriteRepository.save(favorite);

            return Response.buildSuccess("收藏成功");
        } catch (Exception e) {
            return Response.buildFailure("收藏失败：" + e.getMessage(), "500");
        }
    }

    @Override
    @Transactional
    public Response<String> removeFavorite(Integer accountId, Integer productId) {
        try {
            // 检查收藏记录是否存在
            if (!favoriteRepository.existsByAccountIdAndProductId(accountId, productId)) {
                return Response.buildFailure("收藏记录不存在", "404");
            }

            // 删除收藏
            favoriteRepository.deleteByAccountIdAndProductId(accountId, productId);

            return Response.buildSuccess("取消收藏成功");
        } catch (Exception e) {
            return Response.buildFailure("取消收藏失败：" + e.getMessage(), "500");
        }
    }

    @Override
    public Response<List<FavoriteVO>> getFavoriteList(Integer accountId) {
        try {
            List<Favorite> favorites = favoriteRepository.findByAccountIdOrderByCreateTimeDesc(accountId);
            List<FavoriteVO> favoriteVOList = new ArrayList<>();

            for (Favorite favorite : favorites) {
                Optional<Product> productOptional = productRepository.findById(favorite.getProductId());
                if (productOptional.isPresent()) {
                    Product product = productOptional.get();
                    FavoriteVO favoriteVO = new FavoriteVO(
                            favorite.getId(),
                            product.getId(),
                            product.getTitle(),
                            product.getCover(),
                            product.getPrice(),
                            product.getDescription(),
                            product.getRate(),
                            product.getDetail(),
                            product.getTag(),
                            product.getSellerId(),
                            product.getStatus(),
                            product.getCondition(),
                            favorite.getCreateTime()
                    );
                    favoriteVOList.add(favoriteVO);
                }
            }

            return Response.buildSuccess(favoriteVOList);
        } catch (Exception e) {
            return Response.buildFailure("获取收藏列表失败：" + e.getMessage(), "500");
        }
    }

    @Override
    public Response<Boolean> isFavorited(Integer accountId, Integer productId) {
        try {
            boolean isFavorited = favoriteRepository.existsByAccountIdAndProductId(accountId, productId);
            return Response.buildSuccess(isFavorited);
        } catch (Exception e) {
            return Response.buildFailure("查询失败：" + e.getMessage(), "500");
        }
    }

    @Override
    public Response<Integer> getFavoriteCount(Integer accountId) {
        try {
            Integer count = favoriteRepository.countByAccountId(accountId);
            return Response.buildSuccess(count);
        } catch (Exception e) {
            return Response.buildFailure("获取收藏数量失败：" + e.getMessage(), "500");
        }
    }
}
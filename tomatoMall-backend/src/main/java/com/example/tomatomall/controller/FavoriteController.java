package com.example.tomatomall.controller;

import com.example.tomatomall.service.FavoriteService;
import com.example.tomatomall.vo.FavoriteVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    /**
     * 添加收藏
     */
    @PostMapping("/add")
    public Response<String> addFavorite(@RequestParam Integer productId, HttpServletRequest request) {
        Integer accountId = (Integer) request.getAttribute("accountId");
        return favoriteService.addFavorite(accountId, productId);
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/remove")
    public Response<String> removeFavorite(@RequestParam Integer productId, HttpServletRequest request) {
        Integer accountId = (Integer) request.getAttribute("accountId");
        return favoriteService.removeFavorite(accountId, productId);
    }

    /**
     * 获取收藏列表
     */
    @GetMapping("/list")
    public Response<List<FavoriteVO>> getFavoriteList(HttpServletRequest request) {
        Integer accountId = (Integer) request.getAttribute("accountId");
        return favoriteService.getFavoriteList(accountId);
    }

    /**
     * 检查是否已收藏
     */
    @GetMapping("/check")
    public Response<Boolean> isFavorited(@RequestParam Integer productId, HttpServletRequest request) {
        Integer accountId = (Integer) request.getAttribute("accountId");
        return favoriteService.isFavorited(accountId, productId);
    }

    /**
     * 获取收藏数量
     */
    @GetMapping("/count")
    public Response<Integer> getFavoriteCount(HttpServletRequest request) {
        Integer accountId = (Integer) request.getAttribute("accountId");
        return favoriteService.getFavoriteCount(accountId);
    }
}

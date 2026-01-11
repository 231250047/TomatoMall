package com.example.tomatomall.controller;

import com.example.tomatomall.service.AdvertisementService;
import com.example.tomatomall.vo.AdvertisementVO;
import com.example.tomatomall.vo.Response;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/advertisements")
public class AdvertisementController {

    @Resource
    AdvertisementService advertisementService;

    /**
     * 获取所有广告
     */
    @GetMapping
    public Response<List<AdvertisementVO>> getAllAds() {
        List<AdvertisementVO> ads = advertisementService.getAllAds();
        return Response.buildSuccess(ads);
    }

    /**
     * 创建广告
     */
     @PostMapping
     public Response<AdvertisementVO> createAd(@RequestBody AdvertisementVO advertisementVO) {
         System.out.println("Id"+advertisementVO.getId());
         System.out.println("Title"+advertisementVO.getTitle());
            System.out.println("Content"+advertisementVO.getContent());
            System.out.println("Image URL"+advertisementVO.getImgUrl());
         return Response.buildSuccess(advertisementService.createAd(advertisementVO));
     }

    /**
     * 更新广告
     */
    @PutMapping
    public Response<String> updateAd(@RequestBody AdvertisementVO advertisementVO) {
        return Response.buildSuccess(advertisementService.updateAd(advertisementVO));
    }

    /**
     * 删除广告
     */
    @DeleteMapping("/{id}")
    public Response<String> deleteAd(@PathVariable Integer id) {
        return Response.buildSuccess(advertisementService.deleteAd(id));
    }
}

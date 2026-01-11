package com.example.tomatomall.service;

import com.example.tomatomall.vo.AdvertisementVO;

import java.util.List;

public interface AdvertisementService {
    List<AdvertisementVO> getAllAds();
    AdvertisementVO createAd(AdvertisementVO vo);
    String updateAd(AdvertisementVO vo);
    String  deleteAd(Integer id);
}


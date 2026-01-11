package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.Advertisement;
import com.example.tomatomall.repository.AdvertisementRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.AdvertisementService;
import com.example.tomatomall.vo.AdvertisementVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdvertisementServiceImpl implements AdvertisementService {

    @Autowired
    private AdvertisementRepository advertisementRepository;

    @Autowired
    private ProductRepository productRepository;

    /**
     * 查询所有广告（只读事务）
     */
    @Override
    @Transactional(readOnly = true)
    public List<AdvertisementVO> getAllAds() {
        return advertisementRepository.findAll()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 创建广告（事务，发生异常回滚）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdvertisementVO createAd(AdvertisementVO vo) {
        Advertisement ad = vo.toPO();
//        try {
            Advertisement saved = advertisementRepository.save(ad);
            return saved.toVO();
//        } catch (DataAccessException ex) {
//            throw TomatoMallException.concurrentUpdate();
//        }
    }

    /**
     * 更新广告（部分更新，事务，发生异常回滚）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateAd(AdvertisementVO vo) {
        if (vo.getId() == null) {
            throw TomatoMallException.advertisementIdNull();
        }

        Advertisement ad = advertisementRepository.findById(vo.getId())
                .orElseThrow(TomatoMallException::advertisementNotExist);

        // 允许部分更新
        if (vo.getTitle() != null) ad.setTitle(vo.getTitle());
        if (vo.getContent() != null) ad.setContent(vo.getContent());
        if (vo.getImgUrl() != null) ad.setImgUrl(vo.getImgUrl());
        if(vo.getRelatedUrl()!=null) ad.setRelatedUrl(vo.getRelatedUrl());

//        try {
            advertisementRepository.save(ad);
            return "更新成功";
//        } catch (DataAccessException ex) {
//            throw TomatoMallException.concurrentUpdate();
//        }
    }

    /**
     * 删除广告（先查询，再删除，事务，发生异常回滚）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteAd(Integer id) {
        Advertisement ad = advertisementRepository.findById(id)
                .orElseThrow(TomatoMallException::advertisementNotExist);
        try {
            advertisementRepository.delete(ad);
            return "删除成功";
        } catch (DataAccessException ex) {
            throw TomatoMallException.concurrentUpdate();
        }
    }

    /**
     * 实体转VO
     */
    private AdvertisementVO toVO(Advertisement ad) {
        AdvertisementVO vo = new AdvertisementVO();
        BeanUtils.copyProperties(ad, vo);
        return vo;
    }
}

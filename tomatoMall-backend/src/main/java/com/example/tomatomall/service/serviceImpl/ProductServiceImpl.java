package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Stockpile;
import com.example.tomatomall.repository.AccountRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.SpecificationRepository;
import com.example.tomatomall.repository.StockpileRepository;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.Specification;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.SpecificationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {
    @Autowired
    SpecificationRepository specificationRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StockpileRepository stockpileRepository;
    @Autowired
    AccountRepository accountRepository;

    /**
     * 填充商品VO的卖家信息（头像和姓名）
     */
    private void fillSellerInfo(ProductVO productVO) {
        if (productVO.getSellerId() != null) {
            accountRepository.findById(productVO.getSellerId()).ifPresent(seller -> {
                productVO.setSellerAvatar(seller.getAvatar());
                productVO.setSellerName(seller.getName());
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVO[] getProducts() {
        List<ProductVO> products = new ArrayList<ProductVO>();
        List<Product> productList = productRepository.findAll();
        for (Product product : productList) {
            ProductVO productVO = product.toVO();
            List<Specification> specificationList = specificationRepository.findByProductId(product.getId());
            List<SpecificationVO> specificationVOList = new ArrayList<>();
            for (Specification specification : specificationList) {
                specificationVOList.add(specification.toVO());
            }
            productVO.setSpecifications(specificationVOList.toArray(new SpecificationVO[specificationVOList.size()]));
            fillSellerInfo(productVO);
            products.add(productVO);
        }
        return products.toArray(new ProductVO[products.size()]);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVO getProduct(String id) {
        Product product = productRepository.findById(Integer.parseInt(id))
                .orElseThrow(TomatoMallException::productNotExist);
        ProductVO productVO = product.toVO();
        List<Specification> specificationList = specificationRepository.findByProductId(product.getId());
        List<SpecificationVO> specificationVOList = new ArrayList<>();
        for (Specification specification : specificationList) {
            specificationVOList.add(specification.toVO());
        }
        productVO.setSpecifications(specificationVOList.toArray(new SpecificationVO[specificationVOList.size()]));
        fillSellerInfo(productVO);
        return productVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateProduct(Product product) {
        Product oldProduct = productRepository.findById(product.getId())
                .orElseThrow(TomatoMallException::productNotExist);

        if (product.getTitle() != null)
            oldProduct.setTitle(product.getTitle());
        if (product.getPrice() != null)
            oldProduct.setPrice(product.getPrice());
        if (product.getDescription() != null)
            oldProduct.setDescription(product.getDescription());
        if (product.getRate() != null)
            oldProduct.setRate(product.getRate());
        if (product.getCover() != null)
            oldProduct.setCover(product.getCover());
        if (product.getDetail() != null)
            oldProduct.setDetail(product.getDetail());
        if (product.getTag() != null)
            oldProduct.setTag(product.getTag());
        // 新增：更新成色
        if (product.getCondition() != null)
            oldProduct.setCondition(product.getCondition());

        try {
            productRepository.save(oldProduct);
            return true;
        } catch (DataAccessException ex) {
            throw TomatoMallException.concurrentUpdate();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addProduct(Product product) {
        if ((product.getId() == null || !productRepository.existsById(product.getId()))
                && (product.getTitle() == null || !productRepository.existsByTitle(product.getTitle()))) {
            try {
                product.setCreateTime(new Date());
                Product saved = productRepository.save(product);
                Stockpile stockpile = new Stockpile();
                stockpile.setProduct(saved);
                stockpileRepository.save(stockpile);
                return true;
            } catch (DataAccessException ex) {
                throw TomatoMallException.concurrentUpdate();
            }
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteProduct(String id) {
        Product product = productRepository.findById(Integer.parseInt(id))
                .orElseThrow(TomatoMallException::productNotExist);
        try {
            productRepository.delete(product);
            return true;
        } catch (DataAccessException ex) {
            throw TomatoMallException.concurrentUpdate();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateStockpile(String productId, Integer amount) {
        Product product = productRepository.findById(Integer.parseInt(productId))
                .orElseThrow(TomatoMallException::productNotExist);

        Stockpile oldStockpile = stockpileRepository.findByProductId(product.getId());
        try {
            if (oldStockpile == null) {
                Stockpile stockpile = new Stockpile();
                stockpile.setProduct(product);
                stockpile.setAmount(amount);
                stockpile.setFrozen(0); // 默认为0
                stockpileRepository.save(stockpile);
                return true;
            }
            oldStockpile.setAmount(amount);
            stockpileRepository.save(oldStockpile);
            return true;
        } catch (DataAccessException ex) {
            throw TomatoMallException.concurrentUpdate();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Stockpile findStockpile(String productId) {
        Stockpile stockpile = stockpileRepository.findByProductId(Integer.parseInt(productId));
        return stockpile;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVO getProductByTitle(String title) {
        Product product = productRepository.findByTitle(title);
        if (product == null) {
            throw TomatoMallException.productNotExist();
        }
        return getProduct(product.getId().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVO> getProductsByTag(String tag) {
        List<Product> products = productRepository.findByTag(tag);
        List<ProductVO> productVOs = products.stream().map(Product::toVO).collect(Collectors.toList());
        productVOs.forEach(this::fillSellerInfo);
        return productVOs;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVO> getTopRankedProducts() {
        List<Product> products = productRepository.findTop10ByOrderByRateDesc();
        List<ProductVO> productVOs = products.stream().map(Product::toVO).collect(Collectors.toList());
        productVOs.forEach(this::fillSellerInfo);
        return productVOs;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVO> getTopRankedProductsByTag(String tag) {
        List<Product> products = productRepository.findTop10ByTagOrderByRateDesc(tag);
        List<ProductVO> productVOs = products.stream().map(Product::toVO).collect(Collectors.toList());
        productVOs.forEach(this::fillSellerInfo);
        return productVOs;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVO> getProductsBySellerId(Integer sellerId) {
        List<Product> products = productRepository.findBySellerId(sellerId);
        List<ProductVO> productVOs = products.stream().map(Product::toVO).collect(Collectors.toList());
        productVOs.forEach(this::fillSellerInfo);
        return productVOs;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVO> getNewArrivals() {
        // 计算两个月前的时间点
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MONTH, -2);
        Date twoMonthsAgo = cal.getTime();

        List<Product> products = productRepository.findByCreateTimeAfter(twoMonthsAgo);
        List<ProductVO> productVOs = products.stream().map(product -> {
            ProductVO vo = product.toVO();
            // 填充规格（如果需要）
            List<Specification> specificationList = specificationRepository.findByProductId(product.getId());
            List<SpecificationVO> specificationVOList = new ArrayList<>();
            for (Specification specification : specificationList) {
                specificationVOList.add(specification.toVO());
            }
            vo.setSpecifications(specificationVOList.toArray(new SpecificationVO[specificationVOList.size()]));
            // 填充卖家信息
            fillSellerInfo(vo);
            return vo;
        }).collect(Collectors.toList());
        return productVOs;
    }
}

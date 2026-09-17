package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Specification;
import com.example.tomatomall.po.OutboxEvent;
import com.example.tomatomall.repository.OutboxEventRepository;
import java.util.Date;
import java.util.Objects;
import com.example.tomatomall.repository.SpecificationRepository;
import com.example.tomatomall.service.SpecificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SpecificationServiceImpl implements SpecificationService {
    @Autowired
    private SpecificationRepository specificationRepository;
    @Autowired
    private OutboxEventRepository outbox;

    private void productChanged(Integer productId) {
        outbox.save(OutboxEvent.create(OutboxEvent.Kind.PRODUCT_CHANGED,productId,new Date()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSpecification(Specification specification) {
        Optional<Specification> opt = specificationRepository.findById(specification.getId());
        if (!opt.isPresent()) {
            return false;
        } else {
            Specification oldSpecification = opt.get();
            Integer oldProductId=oldSpecification.getProductId();
            if (specification.getItem() != null) oldSpecification.setItem(specification.getItem());
            if (specification.getValue() != null) oldSpecification.setValue(specification.getValue());
            if (specification.getProductId() != null) oldSpecification.setProductId(specification.getProductId());
            try {
                specificationRepository.save(oldSpecification);
                productChanged(oldProductId);
                if(!Objects.equals(oldProductId,oldSpecification.getProductId())) productChanged(oldSpecification.getProductId());
                return true;
            } catch (DataAccessException ex) {
                throw com.example.tomatomall.exception.TomatoMallException.concurrentUpdate();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addSpecification(Specification specification) {
        if (specification.getId() == null || !specificationRepository.existsById(specification.getId())) {
            try {
                specificationRepository.save(specification);
                productChanged(specification.getProductId());
                return true;
            } catch (DataAccessException ex) {
                throw com.example.tomatomall.exception.TomatoMallException.concurrentUpdate();
            }
        }
        return false;
    }
}

package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Specification;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSpecification(Specification specification) {
        Optional<Specification> opt = specificationRepository.findById(specification.getId());
        if (!opt.isPresent()) {
            return false;
        } else {
            Specification oldSpecification = opt.get();
            if (specification.getItem() != null) oldSpecification.setItem(specification.getItem());
            if (specification.getValue() != null) oldSpecification.setValue(specification.getValue());
            if (specification.getProductId() != null) oldSpecification.setProductId(specification.getProductId());
            try {
                specificationRepository.save(oldSpecification);
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
                return true;
            } catch (DataAccessException ex) {
                throw com.example.tomatomall.exception.TomatoMallException.concurrentUpdate();
            }
        }
        return false;
    }
}

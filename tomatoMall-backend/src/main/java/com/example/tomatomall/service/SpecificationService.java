package com.example.tomatomall.service;


import com.example.tomatomall.po.Specification;

public interface SpecificationService {
   boolean updateSpecification(Specification specification);
   boolean addSpecification(Specification specification);
}
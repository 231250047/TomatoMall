package com.example.tomatomall.repository;

import com.example.tomatomall.po.CartsOrdersRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartsOrdersRelationRepository extends JpaRepository<CartsOrdersRelation, Integer> {
    List<CartsOrdersRelation> findBycartitemId(Integer id);
    List<CartsOrdersRelation> findByOrderId(Integer id);
}

package com.example.tomatomall.retrieval;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class ProductCatalog {
    private final ProductRepository products;
    private final SpecificationRepository specifications;
    // A fresh short MySQL transaction per read, never held during remote model calls.
    @Transactional(readOnly=true, propagation=Propagation.REQUIRES_NEW)
    public List<ProductSnapshot> all() { return products.findAll().stream().map(this::snapshot).toList(); }
    @Transactional(readOnly=true, propagation=Propagation.REQUIRES_NEW)
    public Optional<ProductSnapshot> find(int id) { return products.findById(id).map(this::snapshot); }
    private ProductSnapshot snapshot(Product p) {
        var s=p.getStockpile();
        return new ProductSnapshot(p.getId(),p.getTitle(),p.getPrice(),p.getTag(),p.getDescription(),p.getDetail(),p.getStatus(),
            s==null?0:s.getAmount()-s.getFrozen(), specifications.findByProductId(p.getId()).stream()
                .map(v->v.getItem()+"："+v.getValue()).sorted().toList());
    }
}

package com.example.tomatomall.retrieval;

import com.example.tomatomall.po.OutboxEvent;

public interface ProductChangedPublisher {
    void publish(OutboxEvent event) throws Exception;
}

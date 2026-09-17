package com.example.tomatomall.order;
import com.example.tomatomall.po.OutboxEvent;
public interface TimeoutPublisher { void publish(OutboxEvent event) throws Exception; }

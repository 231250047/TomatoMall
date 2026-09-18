package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.ChatService;
import com.example.tomatomall.shopping.BookShoppingAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final BookShoppingAgent agent;
    @Override public String chat(String message) { return agent.chat(message); }
    @Override public String recommendBooks(String query) { return agent.chat(query); }
}

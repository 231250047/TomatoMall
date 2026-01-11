package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.example.tomatomall.exception.TomatoMallException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final RestTemplate restTemplate;

    @Value("${aliyun.dashscope.api-key}")
    private String apiKey;

    @Override
    @Transactional(readOnly = true)
    public String getAiResponse(String userMessage) {
        // 构造请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-v3");

        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message);

        Map<String, Object> input = new HashMap<>();
        input.put("messages", messages);

        requestBody.put("input", input);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw TomatoMallException.aiServiceError();
            }
        } catch (RestClientException ex) {
            throw TomatoMallException.aiServiceError();
        }
    }
}
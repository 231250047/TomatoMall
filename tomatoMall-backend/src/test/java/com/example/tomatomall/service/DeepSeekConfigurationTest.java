package com.example.tomatomall.service;

import com.example.tomatomall.service.serviceImpl.DeepSeekServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekConfigurationTest {
    @Test void bothChatMethodsUseConfiguredModelAndEndpoint() {
        try(var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.ai.openai.api-key", "test-key",
                "spring.ai.openai.base-url", "https://example.test/v1/",
                "spring.ai.openai.chat.options.model", "deepseek-v4-flash")));
            context.register(DeepSeekServiceImpl.class);
            context.refresh();
            var service = context.getBean(DeepSeekServiceImpl.class);
            var client = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
            var server = MockRestServiceServer.bindTo(client).build();
            for(int i=0;i<2;i++) server.expect(requestTo("https://example.test/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.max_tokens").value(i==1 ? 4096 : 2000))
                .andExpect(jsonPath("$.thinking").doesNotExist())
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));
            assertEquals("ok", service.chat("hello"));
            assertEquals("ok", service.chatWithPrompt("choose a book"));
            server.verify();
        }
    }
}

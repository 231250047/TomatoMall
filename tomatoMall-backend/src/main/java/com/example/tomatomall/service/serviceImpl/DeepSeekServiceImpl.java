package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.DeepSeekService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import java.net.SocketTimeoutException;
import java.util.*;

/** Direct DeepSeek adapter. Logs metadata only, never prompts, answers, or provider error bodies. */
@Slf4j
@Service
public class DeepSeekServiceImpl implements DeepSeekService {
    @Value("${spring.ai.openai.api-key}") private String apiKey;
    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") private String baseUrl;
    @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}") private String model;
    private final RestTemplate restTemplate = new RestTemplate(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
        setConnectTimeout(5000); setReadTimeout(30000);
    }});
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override public String chat(String message) { return complete(message,false); }
    @Override public String chatWithPrompt(String prompt) { return complete(prompt,true); }

    public static final class ModelCallException extends RuntimeException {
        private final String reason;
        ModelCallException(String reason) { super("Chat model unavailable: " + reason); this.reason=reason; }
        public String reason() { return reason; }
    }

    private String complete(String prompt,boolean selection) {
        long started=System.nanoTime();
        String callId=UUID.randomUUID().toString(), outcome="INTERNAL_ERROR";
        String responseModel="unknown", finishReason="unknown";
        Long promptTokens=null, completionTokens=null, totalTokens=null, reasoningTokens=null;
        int httpStatus=0;
        boolean hasReasoning=false;
        try {
            if(apiKey==null || apiKey.isBlank() || apiKey.contains("your-") || apiKey.contains("placeholder"))
                throw new ModelCallException("NOT_CONFIGURED");
            if(prompt==null || prompt.isBlank()) throw new ModelCallException("INVALID_INPUT");
            var request=new HashMap<String,Object>(Map.of("model",model,"messages",List.of(Map.of("role","user","content",prompt)),
                    "max_tokens",selection?4096:2000,"temperature",0.7));
            var headers=new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(apiKey);
            var response=restTemplate.exchange(apiUrl(),HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(request),headers),String.class);
            httpStatus=response.getStatusCode().value();
            if(!response.getStatusCode().is2xxSuccessful()) throw new ModelCallException("HTTP_ERROR");
            if(response.getBody()==null || response.getBody().isBlank()) throw new ModelCallException("EMPTY_RESPONSE");
            JsonNode root=objectMapper.readTree(response.getBody());
            if(root==null || !root.isObject()) throw new ModelCallException("INVALID_RESPONSE");
            responseModel=safe(root.path("model").asText("unknown"));
            JsonNode usage=root.path("usage");
            promptTokens=count(usage.path("prompt_tokens")); completionTokens=count(usage.path("completion_tokens"));
            totalTokens=count(usage.path("total_tokens"));
            reasoningTokens=count(usage.path("completion_tokens_details").path("reasoning_tokens"));
            JsonNode choices=root.path("choices");
            if(!choices.isArray() || choices.isEmpty()) throw new ModelCallException("EMPTY_CHOICES");
            JsonNode choice=choices.get(0), message=choice.path("message");
            finishReason=safe(choice.path("finish_reason").asText("unknown"));
            hasReasoning=message.path("reasoning_content").isTextual() && !message.path("reasoning_content").asText().isBlank();
            JsonNode content=message.path("content");
            if(content.isMissingNode() || content.isNull() || (content.isTextual() && content.asText().isBlank()))
                throw new ModelCallException("EMPTY_CONTENT");
            if(!content.isTextual()) throw new ModelCallException("INVALID_RESPONSE");
            if("length".equals(finishReason)) throw new ModelCallException("TRUNCATED");
            if(!Set.of("stop","unknown").contains(finishReason)) throw new ModelCallException("UNSUPPORTED_FINISH");
            outcome="SUCCESS";
            return content.asText();
        } catch(ModelCallException e) {
            outcome=e.reason(); throw e;
        } catch(RestClientResponseException e) {
            httpStatus=e.getStatusCode().value(); outcome="HTTP_ERROR";
            throw new ModelCallException(outcome);
        } catch(ResourceAccessException e) {
            outcome=isTimeout(e)?"TIMEOUT":"TRANSPORT_ERROR";
            throw new ModelCallException(outcome);
        } catch(JsonProcessingException e) {
            outcome="INVALID_RESPONSE"; throw new ModelCallException(outcome);
        } catch(RuntimeException e) {
            outcome="CLIENT_ERROR"; throw new ModelCallException(outcome);
        } finally {
            log.info("chat_call callId={} outcome={} requestedModel={} responseModel={} finishReason={} httpStatus={} promptTokens={} completionTokens={} totalTokens={} reasoningTokens={} hasReasoning={} elapsedMs={}",
                    callId,outcome,safe(model),responseModel,finishReason,httpStatus,promptTokens,completionTokens,totalTokens,
                    reasoningTokens,hasReasoning,(System.nanoTime()-started)/1_000_000);
        }
    }
    private static Long count(JsonNode node) { return node.isIntegralNumber() && node.canConvertToLong() && node.asLong()>=0 ? node.asLong() : null; }
    private static String safe(String value) {
        return value!=null && value.matches("[A-Za-z0-9_.:-]{1,100}") ? value : "unknown";
    }
    private static boolean isTimeout(Throwable error) {
        for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof SocketTimeoutException) return true;
        return false;
    }
    private String apiUrl() {
        String base=baseUrl.replaceAll("/+$", "");
        return base+(base.endsWith("/v1")?"":"/v1")+"/chat/completions";
    }
}

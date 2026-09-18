package com.example.tomatomall.config;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashScopeEmbeddingModelTest {
    private static TextEmbeddingResult result(String values) {
        return new Gson().fromJson("{\"output\":{\"embeddings\":[{\"text_index\":0,\"embedding\":"
                + values + "}]}}", TextEmbeddingResult.class);
    }

    @Test
    void sendsConfiguredDimensionAndOnlyDocumentContent() {
        AtomicReference<TextEmbeddingParam> sent = new AtomicReference<>();
        try (var ignored = mockConstruction(TextEmbedding.class, (sdk, context) ->
                when(sdk.call(any(TextEmbeddingParam.class))).thenAnswer(invocation -> {
                    sent.set(invocation.getArgument(0));
                    return result("[1,2,3]");
                }))) {
            var model = new DashScopeEmbeddingModel("test-model", 3, "test-key");
            assertArrayEquals(new float[]{1,2,3}, model.embed(new Document("Java practice", Map.of("price", 99))));
            assertEquals(3, sent.get().getParameters().get("dimension"));
            assertEquals("Java practice", sent.get().getInput().getAsJsonArray("texts").get(0).getAsString());
            assertFalse(sent.get().getInput().toString().contains("price"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[1,2]", "[0,0,0]", "[null,1,2]", "[1e100,1,2]"})
    void rejectsInvalidVectors(String values) {
        try (var ignored = mockConstruction(TextEmbedding.class, (sdk, context) ->
                when(sdk.call(any(TextEmbeddingParam.class))).thenReturn(result(values)))) {
            assertThrows(IllegalStateException.class,
                    () -> new DashScopeEmbeddingModel("test-model", 3, "test-key").embed("book"));
        }
    }

    @Test
    void rejectsMissingOutput() {
        try (var ignored = mockConstruction(TextEmbedding.class, (sdk, context) ->
                when(sdk.call(any(TextEmbeddingParam.class))).thenReturn(new Gson().fromJson("{}", TextEmbeddingResult.class)))) {
            assertThrows(IllegalStateException.class,
                    () -> new DashScopeEmbeddingModel("test-model", 3, "test-key").embed("book"));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "placeholder", "your-api-key", "your_dashscope_api_key", "sk-placeholder"})
    void missingCredentialsAllowConstructionButFailBeforeNetwork(String key) {
        try (var sdk = mockConstruction(TextEmbedding.class)) {
            var model = assertDoesNotThrow(() -> new DashScopeEmbeddingModel("test-model", 3, key));
            assertEquals(3, model.dimensions());
            assertThrows(IllegalStateException.class, () -> model.embed("book"));
            assertTrue(sdk.constructed().isEmpty());
        }
    }

    @Test
    void preservesInputOrder() {
        try (var ignored = mockConstruction(TextEmbedding.class, (sdk, context) ->
                when(sdk.call(any(TextEmbeddingParam.class))).thenAnswer(invocation -> {
                    TextEmbeddingParam param = invocation.getArgument(0);
                    String text = param.getInput().getAsJsonArray("texts").get(0).getAsString();
                    return result(text.equals("first") ? "[1,0,0]" : "[0,1,0]");
                }))) {
            var model = new DashScopeEmbeddingModel("test-model", 3, "test-key");
            var response = model.call(new EmbeddingRequest(List.of("first", "second"), EmbeddingOptionsBuilder.builder().build()));
            assertArrayEquals(new float[]{1,0,0}, response.getResults().get(0).getOutput());
            assertEquals(0, response.getResults().get(0).getIndex());
            assertArrayEquals(new float[]{0,1,0}, response.getResults().get(1).getOutput());
            assertEquals(1, response.getResults().get(1).getIndex());
        }
    }

    @Test
    void providerFailureDoesNotExposeProviderPayload() {
        try (var ignored = mockConstruction(TextEmbedding.class, (sdk, context) ->
                when(sdk.call(any(TextEmbeddingParam.class))).thenThrow(new RuntimeException("secret-key private-query")))) {
            var error = assertThrows(IllegalStateException.class,
                    () -> new DashScopeEmbeddingModel("test-model", 3, "test-key").embed("book"));
            assertFalse(error.toString().contains("secret-key"));
            assertNull(error.getCause());
        }
    }
}

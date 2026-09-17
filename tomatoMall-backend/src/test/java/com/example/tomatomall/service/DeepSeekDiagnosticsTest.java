package com.example.tomatomall.service;

import com.example.tomatomall.service.serviceImpl.DeepSeekServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class DeepSeekDiagnosticsTest {
    DeepSeekServiceImpl service;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        service=new DeepSeekServiceImpl();
        ReflectionTestUtils.setField(service,"apiKey","secret-test-key");
        ReflectionTestUtils.setField(service,"baseUrl","https://example.test");
        ReflectionTestUtils.setField(service,"model","deepseek-v4-flash");
        server=MockRestServiceServer.bindTo((RestTemplate)ReflectionTestUtils.getField(service,"restTemplate")).build();
    }
    @Test void blankContentIsFailureWithSafeUsageDiagnostics(CapturedOutput output) {
        server.expect(requestTo("https://example.test/v1/chat/completions"))
            .andRespond(withSuccess("{\"model\":\"deepseek-flash\",\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"\",\"reasoning_content\":\"private-reasoning\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2000,\"total_tokens\":2010,\"completion_tokens_details\":{\"reasoning_tokens\":2000}}}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->service.chatWithPrompt("private-user-prompt")).hasMessageContaining("EMPTY_CONTENT");
        assertThat(output.getAll()).contains("outcome=EMPTY_CONTENT","finishReason=length","responseModel=deepseek-flash","completionTokens=2000","reasoningTokens=2000","elapsedMs=")
            .doesNotContain("private-user-prompt","private-reasoning","secret-test-key");
        server.verify();
    }
    @Test void nonEmptyTruncatedOutputIsNotSuccessful(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"private-incomplete-answer\"}}]}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->service.chat("private-user-prompt")).hasMessageContaining("TRUNCATED");
        assertThat(output.getAll()).contains("outcome=TRUNCATED").doesNotContain("private-incomplete-answer","private-user-prompt");
    }
    @Test void providerHttpErrorDoesNotLeakBody(CapturedOutput output) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-provider-body"));
        assertThatThrownBy(()->service.chat("private-user-prompt")).hasMessageContaining("HTTP_ERROR").hasMessageNotContaining("secret-provider-body");
        assertThat(output.getAll()).contains("outcome=HTTP_ERROR","httpStatus=401").doesNotContain("secret-provider-body","private-user-prompt","secret-test-key");
    }
    @Test void timeoutIsIdentified(CapturedOutput output) {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("private-transport-detail")));
        assertThatThrownBy(()->service.chat("private-user-prompt")).hasMessageContaining("TIMEOUT");
        assertThat(output.getAll()).contains("outcome=TIMEOUT").doesNotContain("private-transport-detail","private-user-prompt");
    }
    @Test void malformedProviderJsonHasItsOwnReason(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("private-invalid-json",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->service.chat("private-user-prompt")).hasMessageContaining("INVALID_RESPONSE");
        assertThat(output.getAll()).contains("outcome=INVALID_RESPONSE").doesNotContain("private-invalid-json","private-user-prompt");
    }
}

package com.example.tomatomall.shopping;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BookToolPlannerTest {
    @Test void usesNativeToolsAndHasNoModelControlledWeightsOrPrices() {
        var planner=new BookToolPlanner();
        ReflectionTestUtils.setField(planner,"apiKey","test-key");
        ReflectionTestUtils.setField(planner,"baseUrl","https://example.test");
        var server=MockRestServiceServer.bindTo((RestTemplate)ReflectionTestUtils.getField(planner,"client")).build();
        server.expect(requestTo("https://example.test/v1/chat/completions"))
            .andExpect(jsonPath("$.tools[0].function.name").value("search_books"))
            .andExpect(jsonPath("$.thinking.type").value("disabled"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("vectorWeight"))))
            .andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"type\":\"function\",\"function\":{\"name\":\"search_books\",\"arguments\":\"{\\\"semanticQuery\\\":\\\"Java入门书\\\"}\"}}]}}]}",MediaType.APPLICATION_JSON));
        var call=planner.choose("Java入门书，50块以下");
        assertThat(call.name()).isEqualTo("search_books");
        assertThat(call.arguments().path("semanticQuery").asText()).isEqualTo("Java入门书");
        server.verify();
    }
    @Test void unknownToolOrMultipleCallsAreRejectedBeforeExecution() {
        var planner=new BookToolPlanner();ReflectionTestUtils.setField(planner,"apiKey","test-key");ReflectionTestUtils.setField(planner,"baseUrl","https://example.test");
        var server=MockRestServiceServer.bindTo((RestTemplate)ReflectionTestUtils.getField(planner,"client")).build();
        server.expect(anything()).andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"type\":\"function\",\"function\":{\"name\":\"run_sql\",\"arguments\":\"{}\"}}]}}]}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->planner.choose("Java")).isInstanceOf(IllegalStateException.class);
    }
}

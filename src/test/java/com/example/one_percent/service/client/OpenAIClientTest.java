package com.example.one_percent.service.client;

import com.example.one_percent.config.AiSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the client actually sends over HTTP, checked against a local fake server. */
class OpenAIClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private MockWebServer server;

    @BeforeEach
    void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() throws IOException {
        server.shutdown();
    }

    /** Settings like the Gemini profile's, pointed at the fake server. */
    private OpenAIClient client(String apiKey, String reasoningEffort) {
        return client(apiKey, reasoningEffort, reasoningEffort, null);
    }

    private OpenAIClient client(String apiKey, String smartEffort, String fastEffort, Integer dimensions) {
        String base = server.url("/v1beta/openai/").toString(); // trailing slash on purpose
        return new OpenAIClient(new AiSettings(apiKey, base, "smart-model", "fast-model", "embed-model", dimensions, smartEffort, fastEffort));
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private JsonNode body(RecordedRequest request) throws IOException {
        return mapper.readTree(request.getBody().readUtf8());
    }

    private String chat(OpenAIClient client, AiModel model) throws IOException {
        var messages = client.createMessages(List.of(client.createSingleMessage("user", "hello")));
        return client.sendChatCompletion(messages, model, 60, 0.8);
    }

    @Test
    void chatPostsToTheConfiguredServiceWithTheModelForTheRole() throws Exception {
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"  A tip.  \"}}]}"));
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"Feedback.\"}}]}"));
        OpenAIClient client = client("secret-key", null);

        assertThat(chat(client, AiModel.FAST)).isEqualTo("A tip.");
        assertThat(chat(client, AiModel.SMART)).isEqualTo("Feedback.");

        RecordedRequest first = server.takeRequest();
        assertThat(first.getPath()).isEqualTo("/v1beta/openai/chat/completions");
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer secret-key");
        JsonNode payload = body(first);
        assertThat(payload.get("model").asText()).isEqualTo("fast-model");
        assertThat(payload.get("max_tokens").asInt()).isEqualTo(60);
        assertThat(payload.get("messages").get(0).get("content").asText()).isEqualTo("hello");
        assertThat(payload.has("reasoning_effort")).isFalse();
        assertThat(body(server.takeRequest()).get("model").asText()).isEqualTo("smart-model");
    }

    @Test
    void sendsReasoningEffortOnlyWhenConfigured() throws Exception {
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));

        chat(client("k", "none"), AiModel.FAST);

        assertThat(body(server.takeRequest()).get("reasoning_effort").asText()).isEqualTo("none");
    }

    @Test
    void eachModelRoleGetsItsOwnReasoningEffort() throws Exception {
        // Gemini: the flash model accepts only "none", flash-lite rejects "none" but accepts "minimal"
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));
        OpenAIClient client = client("k", "none", "minimal", null);

        chat(client, AiModel.SMART);
        chat(client, AiModel.FAST);

        assertThat(body(server.takeRequest()).get("reasoning_effort").asText()).isEqualTo("none");
        assertThat(body(server.takeRequest()).get("reasoning_effort").asText()).isEqualTo("minimal");
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutAKey() throws Exception {
        // e.g. a local Ollama, which needs none
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));

        chat(client("", null), AiModel.FAST);

        assertThat(server.takeRequest().getHeader("Authorization")).isNull();
    }

    @Test
    void anEmptyReplyIsAnErrorThatSaysWhy() {
        // what a thinking model that spent its whole token budget thinking sends back
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":null},\"finish_reason\":\"length\"}]}"));

        assertThatThrownBy(() -> chat(client("k", null), AiModel.FAST))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no text")
                .hasMessageContaining("length");
    }

    @Test
    void aFailedRequestReportsTheProvidersMessageButNeverTheKey() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"message\":\"Quota exceeded\"}}"));

        assertThatThrownBy(() -> chat(client("secret-key", null), AiModel.FAST))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 429")
                .hasMessageContaining("Quota exceeded")
                .hasMessageNotContaining("secret-key");
    }

    @Test
    void embedsSeveralTextsInOneRequestAndKeepsTheirOrder() throws Exception {
        // the service may answer out of order; each item's index says which text it belongs to
        server.enqueue(json("{\"data\":[{\"index\":1,\"embedding\":[0.3,0.4]},{\"index\":0,\"embedding\":[0.1,0.2]}]}"));

        List<List<Double>> vectors = client("k", null).embedAll(List.of("first", "second"));

        assertThat(vectors).containsExactly(List.of(0.1, 0.2), List.of(0.3, 0.4));
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1beta/openai/embeddings");
        JsonNode payload = body(request);
        assertThat(payload.get("model").asText()).isEqualTo("embed-model");
        assertThat(payload.get("input").get(1).asText()).isEqualTo("second");
    }

    @Test
    void readsGeminiEmbeddingRepliesWhichHaveNoIndexField() throws Exception {
        // Gemini's OpenAI-compatible endpoint returns the vectors in input order without "index"
        server.enqueue(json("{\"data\":[{\"object\":\"embedding\",\"embedding\":[0.1,0.2]},{\"object\":\"embedding\",\"embedding\":[0.3,0.4]}]}"));

        assertThat(client("k", null).embedAll(List.of("first", "second"))).containsExactly(List.of(0.1, 0.2), List.of(0.3, 0.4));
    }

    @Test
    void asksForShorterVectorsWhenDimensionsAreConfigured() throws Exception {
        server.enqueue(json("{\"data\":[{\"embedding\":[0.1]}]}"));
        server.enqueue(json("{\"data\":[{\"embedding\":[0.1]}]}"));

        client("k", null, null, 768).embed("text");
        client("k", null).embed("text");

        assertThat(body(server.takeRequest()).get("dimensions").asInt()).isEqualTo(768);
        assertThat(body(server.takeRequest()).has("dimensions")).isFalse();
    }

    @Test
    void aMissingEmbeddingIsAnError() {
        server.enqueue(json("{\"data\":[{\"index\":0,\"embedding\":[0.1]}]}"));

        assertThatThrownBy(() -> client("k", null).embedAll(List.of("a", "b")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("1 embeddings for 2 texts");
    }
}

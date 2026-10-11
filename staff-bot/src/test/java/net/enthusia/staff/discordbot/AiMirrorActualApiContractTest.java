package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Owner-local, opt-in contract test against a disposable Python Moderation API.
 * The synthetic test-only endpoint must never be a production service.
 */
class AiMirrorActualApiContractTest {
    private static final String ENDPOINT_ENV = "ENTHUSIA_AI_SYNTHETIC_MIRROR_URL";
    private static final String CLIENT = "synthetic-staffbot-bridge";
    private static final String TOKEN = "synthetic-mirror-only-token";
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final String ID = "1234567890123456787";
    private static final String CANONICAL = "rosechat:canonical:network-test";
    private static final String TEXT = "Minecraft PvP café 💎";

    @Test
    void realPythonApiRegistersAndReplaysActualJavaPublisherRequest() throws Exception {
        String configured = System.getenv(ENDPOINT_ENV);
        assumeTrue(configured != null && !configured.isBlank(),
                "synthetic loopback Python API fixture not enabled");
        URI uri = URI.create(configured);
        assertEquals("http", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost());
        assertEquals("/v1/mirror-aliases", uri.getRawPath());
        assertTrue(uri.getPort() > 0);
        assertFalse(uri.getPort() == 80);

        AiMirrorPublicationConfig config = new AiMirrorPublicationConfig(
                uri, CLIENT, TOKEN, GUILD, Set.of(CHANNEL), "discord_general");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        CountDownLatch finished = new CountDownLatch(2);
        List<Integer> statuses = new CopyOnWriteArrayList<>();
        List<JsonNode> bodies = new CopyOnWriteArrayList<>();
        ObjectMapper json = new ObjectMapper();
        try (AiMirrorRegistrationPublisher publisher = new AiMirrorRegistrationPublisher(
                config, request -> {
                    HttpResponse<String> response = client.send(
                            request, HttpResponse.BodyHandlers.ofString());
                    statuses.add(response.statusCode());
                    bodies.add(json.readTree(response.body()));
                    finished.countDown();
                    return response.statusCode();
                })) {
            publisher.resume();
            DiscordChatPublication publication = new DiscordChatPublication(
                    GUILD, CHANNEL, ID, CANONICAL, TEXT);
            publisher.published(publication);
            publisher.published(publication);
            assertTrue(finished.await(8, TimeUnit.SECONDS));
        }
        assertEquals(List.of(200, 200), statuses);
        assertEquals(2, bodies.size());
        assertEquals(bodies.get(0).get("event_id"), bodies.get(1).get("event_id"));
        assertEquals("ALLOW", bodies.get(0).get("message_action").asText());
        assertTrue(bodies.get(1).get("idempotent_replay").asBoolean());
        assertEquals("NONE", bodies.get(1).get("strike_recommendation").asText());
        assertEquals("NONE", bodies.get(1).get("containment").asText());
    }
}

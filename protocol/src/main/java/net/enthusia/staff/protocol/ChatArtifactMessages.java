package net.enthusia.staff.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Dedicated bounded codec for rich-chat binary artifact bundles. */
public final class ChatArtifactMessages {
    public static final String ARTIFACTS = "CHAT_BRIDGE_ARTIFACTS_V1";
    public static final int MAX_PAYLOAD_BYTES = 720_896;

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ChatArtifactMessages() {
    }

    public static String encode(ChatBridgeArtifactBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("artifact bundle is required");
        }
        try {
            String encoded = JSON.writeValueAsString(bundle);
            requirePayloadSize(encoded);
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("artifact bundle cannot be encoded", exception);
        }
    }

    public static ChatBridgeArtifactBundle decode(String payloadJson) {
        requirePayloadSize(payloadJson);
        try {
            return JSON.readValue(payloadJson, ChatBridgeArtifactBundle.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("artifact bundle payload is invalid", exception);
        }
    }

    public static UUID transportMessageId(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("artifact event ID is required");
        }
        return UUID.nameUUIDFromBytes(
                ("chat-artifacts:" + eventId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void requirePayloadSize(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("artifact bundle payload is required");
        }
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("artifact bundle payload exceeds maximum size");
        }
    }
}

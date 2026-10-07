package net.enthusia.staff.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Dedicated codec for styled chat metadata; binary rich artifacts use a separate contract. */
public final class ChatRenderMessages {
    public static final String RENDERED = "CHAT_BRIDGE_RENDERED_V1";
    public static final int MAX_PAYLOAD_BYTES = 393_216;

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ChatRenderMessages() {
    }

    public static String encode(ChatBridgeRenderedMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("rendered chat message is required");
        }
        try {
            String encoded = JSON.writeValueAsString(message);
            requirePayloadSize(encoded);
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("rendered chat message cannot be encoded", exception);
        }
    }

    public static ChatBridgeRenderedMessage decode(String payloadJson) {
        requirePayloadSize(payloadJson);
        try {
            return JSON.readValue(payloadJson, ChatBridgeRenderedMessage.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("rendered chat payload is invalid", exception);
        }
    }

    private static void requirePayloadSize(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("rendered chat payload is required");
        }
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("rendered chat payload exceeds maximum size");
        }
    }
}

package net.enthusia.staff.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Message type and JSON codec for the ephemeral Minecraft-to-Discord chat relay. */
public final class ChatBridgeMessages {
    public static final String OUTBOUND = "CHAT_BRIDGE_OUTBOUND_V1";
    public static final int MAX_PAYLOAD_BYTES = 16_384;

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ChatBridgeMessages() {
    }

    public static String encodeOutbound(ChatBridgeOutboundMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("chat bridge outbound message is required");
        }
        try {
            String encoded = JSON.writeValueAsString(message);
            requirePayloadSize(encoded);
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("chat bridge outbound message cannot be encoded", exception);
        }
    }

    public static ChatBridgeOutboundMessage decodeOutbound(String payloadJson) {
        requirePayloadSize(payloadJson);
        try {
            return JSON.readValue(payloadJson, ChatBridgeOutboundMessage.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("chat bridge outbound payload is invalid", exception);
        }
    }

    private static void requirePayloadSize(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("chat bridge payload is required");
        }
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("chat bridge payload exceeds maximum size");
        }
    }
}

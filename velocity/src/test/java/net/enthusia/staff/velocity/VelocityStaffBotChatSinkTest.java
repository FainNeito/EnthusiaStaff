package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.PersistentChannelServer;
import org.junit.jupiter.api.Test;

class VelocityStaffBotChatSinkTest {
    @Test
    void sendsOnlyEphemeralChatToPinnedStaffBotPeer() {
        AtomicReference<String> peer = new AtomicReference<>();
        AtomicReference<UUID> messageId = new AtomicReference<>();
        AtomicReference<String> type = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        VelocityStaffBotChatSink sink = new VelocityStaffBotChatSink(
                (peerId, id, messageType, payloadJson, timeout) -> {
                    peer.set(peerId);
                    messageId.set(id);
                    type.set(messageType);
                    payload.set(payloadJson);
                    assertEquals(VelocityStaffBotChatSink.ACK_TIMEOUT, timeout);
                    return CompletableFuture.completedFuture(
                            PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED);
                }
        );
        ChatBridgeOutboundMessage message = message();

        assertTrue(sink.offer(message));
        assertEquals(VelocityStaffBotChatSink.PEER_ID, peer.get());
        assertEquals(message.eventId(), messageId.get());
        assertEquals(ChatBridgeMessages.OUTBOUND, type.get());
        assertEquals(message, ChatBridgeMessages.decodeOutbound(payload.get()));
    }

    @Test
    void missingOrRejectedStaffBotPeerDropsWithoutRetry() {
        ChatBridgeOutboundMessage message = message();
        VelocityStaffBotChatSink disconnected = new VelocityStaffBotChatSink(
                (peerId, id, messageType, payloadJson, timeout) ->
                        CompletableFuture.completedFuture(
                                PersistentChannelServer.DeliveryStatus.NOT_CONNECTED)
        );
        VelocityStaffBotChatSink rejected = new VelocityStaffBotChatSink(
                (peerId, id, messageType, payloadJson, timeout) ->
                        CompletableFuture.completedFuture(
                                PersistentChannelServer.DeliveryStatus.REJECTED)
        );

        assertFalse(disconnected.offer(message));
        assertFalse(rejected.offer(message));
    }

    private static ChatBridgeOutboundMessage message() {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                1_800_000_000_000L,
                1_800_000_030_000L,
                "SMP",
                "global",
                UUID.randomUUID(),
                "Player",
                "hello"
        );
    }
}

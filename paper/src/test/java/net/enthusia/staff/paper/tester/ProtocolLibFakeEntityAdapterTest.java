package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ProtocolLibFakeEntityAdapterTest {
    @Test
    void presentButUnsupportedDedicatedPacketFallsBackToLegacyUseEntity() {
        AtomicBoolean supportChecked = new AtomicBoolean();

        Object packetLikeValue = new Object();

        assertTrue(ProtocolLibFakeEntityAdapter.supportedValue(
                packetLikeValue,
                Object.class,
                ignored -> {
                    supportChecked.set(true);
                    return false;
                }
        ).isEmpty());
        assertTrue(supportChecked.get());
    }

    @Test
    void supportedDedicatedPacketIsRetained() {
        Object packetLikeValue = new Object();

        assertEquals(
                packetLikeValue,
                ProtocolLibFakeEntityAdapter.supportedValue(
                        packetLikeValue,
                        Object.class,
                        ignored -> true
                ).orElseThrow()
        );
    }

    @Test
    void dedicatedAttackPacketIsRecordedAsAttackWithoutLegacyWrapperAccess() {
        AtomicBoolean legacyRead = new AtomicBoolean();

        String action = ProtocolLibFakeEntityAdapter.actionNameForPacket(
                true,
                true,
                () -> {
                    legacyRead.set(true);
                    return "LEGACY";
                }
        );

        assertEquals("ATTACK", action);
        assertFalse(legacyRead.get());
    }

    @Test
    void splitUseEntityPacketIsRecordedAsInteractionWithoutLegacyWrapperAccess() {
        AtomicBoolean legacyRead = new AtomicBoolean();

        String action = ProtocolLibFakeEntityAdapter.actionNameForPacket(
                true,
                false,
                () -> {
                    legacyRead.set(true);
                    return "LEGACY";
                }
        );

        assertEquals("INTERACT", action);
        assertFalse(legacyRead.get());
    }

    @Test
    void legacyPacketRetainsProtocolLibActionWrapperPath() {
        assertEquals(
                "ATTACK",
                ProtocolLibFakeEntityAdapter.actionNameForPacket(false, false, () -> "ATTACK")
        );
    }
}

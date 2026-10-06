package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ProtocolLibFakeEntityAdapterTest {
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

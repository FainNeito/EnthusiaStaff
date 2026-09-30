package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffDutyContextCalculatorTest {
    private static final UUID PLAYER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void activeStaffSessionPublishesDutyContext() {
        StaffDutyContextCalculator calculator = new StaffDutyContextCalculator(PLAYER_ID::equals);
        Map<String, String> contexts = new HashMap<>();

        calculator.calculate(player(), contexts::put);

        assertEquals(
                StaffDutyContextCalculator.ACTIVE_VALUE,
                contexts.get(StaffDutyContextCalculator.CONTEXT_KEY)
        );
    }

    @Test
    void inactivePlayerGetsNoDutyContext() {
        StaffDutyContextCalculator calculator = new StaffDutyContextCalculator(ignored -> false);
        Map<String, String> contexts = new HashMap<>();

        calculator.calculate(player(), contexts::put);

        assertTrue(contexts.isEmpty());
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> PLAYER_ID;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}

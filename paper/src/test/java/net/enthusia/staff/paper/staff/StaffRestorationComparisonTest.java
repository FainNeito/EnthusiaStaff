package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class StaffRestorationComparisonTest {
    @Test
    void separatelyDecodedEmptyInventoryAndFullYawRotationsAreEquivalent() {
        var expected = baseline();
        var equivalent = baseline();
        assertEquals(expected, equivalent);
        equivalent.location().setYaw(equivalent.location().getYaw() + 360);
        // Construction canonicalizes teleport rotation; all other fields remain exact.
        var normalized = new StaffStateCodec.Decoded(equivalent.serverId(), equivalent.inventory(),
                equivalent.level(), equivalent.experienceProgress(), equivalent.totalExperience(), equivalent.health(),
                equivalent.absorption(), equivalent.food(), equivalent.saturation(), equivalent.exhaustion(),
                equivalent.effects(), equivalent.location(), equivalent.gameMode(), equivalent.allowFlight(),
                equivalent.flying(), equivalent.flySpeed(), equivalent.walkSpeed(), equivalent.invulnerable(),
                equivalent.collidable(), equivalent.canPickupItems(), equivalent.fireTicks(), equivalent.remainingAir(),
                equivalent.fallDistance());
        assertEquals(expected, normalized);
    }

    @Test
    void everyStoredFieldStillRejectsAnActualStateDifference() throws ReflectiveOperationException {
        var expected = baseline();
        var fields = StaffStateCodec.Decoded.class.getRecordComponents();
        var constructor = StaffStateCodec.Decoded.class.getDeclaredConstructor(
                Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));
        Object[] original = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) original[i] = fields[i].getAccessor().invoke(expected);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getName().equals("effects")) continue; // Effects use full PotionEffect value equality.
            Object[] changed = original.clone();
            Object value = changed[i];
            if (value instanceof String) changed[i] = "OTHER";
            else if (value instanceof Integer number) changed[i] = number + 1;
            else if (value instanceof Float number) changed[i] = number + 0.01f;
            else if (value instanceof Double number) changed[i] = number + 0.01;
            else if (value instanceof Boolean flag) changed[i] = !flag;
            else if (value instanceof Location location) changed[i] = location.clone().add(0.01, 0, 0);
            else if (value instanceof GameMode) changed[i] = GameMode.SURVIVAL;
            else if (value instanceof List<?>) changed[i] = new ArrayList<>();
            else throw new AssertionError("Uncovered field " + fields[i].getName());
            var actual = constructor.newInstance(changed);
            assertFalse(expected.equals(actual), fields[i].getName());
            assertEquals(List.of(fields[i].getName()), StaffStateCodec.differingFields(expected, actual));
        }
    }

    private static StaffStateCodec.Decoded baseline() {
        return new StaffStateCodec.Decoded("SMP", Arrays.asList(null, null), 3, .25f, 42, 20, 0,
                20, 5, 0, Set.of(), new Location(null, 1, 65, 2, 30, 10), GameMode.CREATIVE,
                true, false, .1f, .2f, false, true, true, 0, 300, 0);
    }
}

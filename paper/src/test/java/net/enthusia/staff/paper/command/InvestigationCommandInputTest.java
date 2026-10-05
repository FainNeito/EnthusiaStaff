package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InvestigationCommandInputTest {
    @Test void acceptsFiniteBoundsAndPermanentFlags() {
        assertEquals(Duration.ofHours(1), InvestigationCommand.parseLifetime("1"));
        assertEquals(Duration.ofDays(365), InvestigationCommand.parseLifetime("8760"));
        assertNull(InvestigationCommand.parseLifetime("permanent"));
    }
    @Test void rejectsZeroNegativeAndExcessiveDurations() {
        assertThrows(IllegalArgumentException.class, () -> InvestigationCommand.parseLifetime("0"));
        assertThrows(IllegalArgumentException.class, () -> InvestigationCommand.parseLifetime("-1"));
        assertThrows(IllegalArgumentException.class, () -> InvestigationCommand.parseLifetime("8761"));
    }
    @Test void rejectsOverflowingInputsBeforeConvertingToDuration() {
        assertThrows(IllegalArgumentException.class, () -> InvestigationCommand.parseLifetime(Long.toString(Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> InvestigationCommand.parseLifetime("9223372036854775808"));
        assertThrows(IllegalArgumentException.class, () -> InvestigationCommand.parseLifetime("tomorrow"));
    }
}

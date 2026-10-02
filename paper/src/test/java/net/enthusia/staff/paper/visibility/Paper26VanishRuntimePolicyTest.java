package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.*;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class Paper26VanishRuntimePolicyTest {
    @Test void acceptsOnlyExactPaperBuild129() {
        assertTrue(Paper26VanishClientGameModeAdapter.supportsRuntime(true,"26.2",OptionalInt.of(129)));
        assertFalse(Paper26VanishClientGameModeAdapter.supportsRuntime(true,"26.2",OptionalInt.of(128)));
        assertFalse(Paper26VanishClientGameModeAdapter.supportsRuntime(true,"26.3",OptionalInt.of(129)));
        assertFalse(Paper26VanishClientGameModeAdapter.supportsRuntime(false,"26.2",OptionalInt.of(129)));
        assertFalse(Paper26VanishClientGameModeAdapter.supportsRuntime(true,"26.2",OptionalInt.empty()));
    }
}

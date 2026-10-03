package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentConfirmationTargetTest {
    private static final UUID DRAFT = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void playerNameResolvesToTheStoredDraftIdentifier() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId("TargetPlayer", input -> {
            assertEquals("TargetPlayer", input);
            return Optional.of(DRAFT);
        }));
    }

    @Test
    void bedrockNamesKeepTheirPrefixWhenResolved() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId(".BedrockPlayer", input -> {
            assertEquals(".BedrockPlayer", input);
            return Optional.of(DRAFT);
        }));
    }

    @Test
    void existingDraftIdDoesNotGetInterpretedAsAPlayer() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId(DRAFT.toString(), input -> {
            fail("Draft IDs must retain their exact existing confirmation path");
            return Optional.empty();
        }));
    }

    @Test
    void missingDraftCannotProduceAConfirmationIdentifier() {
        assertTrue(PunishmentCommand.confirmationDraftId("UnknownPlayer", input -> Optional.empty()).isEmpty());
    }
}

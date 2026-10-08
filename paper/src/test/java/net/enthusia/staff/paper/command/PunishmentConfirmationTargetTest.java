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
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId("TargetPlayer", id -> false, input -> {
            assertEquals("TargetPlayer", input);
            return Optional.of(DRAFT);
        }));
    }

    @Test
    void bedrockNamesKeepTheirPrefixWhenResolved() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId(".BedrockPlayer", id -> false, input -> {
            assertEquals(".BedrockPlayer", input);
            return Optional.of(DRAFT);
        }));
    }

    @Test
    void existingDraftIdDoesNotGetInterpretedAsAPlayer() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId(DRAFT.toString(), id -> id.equals(DRAFT), input -> {
            fail("Draft IDs must retain their exact existing confirmation path");
            return Optional.empty();
        }));
    }

    @Test
    void missingDraftCannotProduceAConfirmationIdentifier() {
        assertTrue(PunishmentCommand.confirmationDraftId("UnknownPlayer", id -> false, input -> Optional.empty()).isEmpty());
    }
    @Test
    void playerUuidWithoutAnActorDraftResolvesItsTargetBoundDraft() {
        UUID player = UUID.fromString("30000000-0000-0000-0000-000000000001");
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId(player.toString(),
                id -> false, input -> {
                    assertEquals(player.toString(), input);
                    return Optional.of(DRAFT);
                }));
    }

    @Test
    void looseUuidLikeNameIsNotCoercedIntoADraftId() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId("1-1-1-1-1",
                id -> { fail("Only canonical UUIDs can be draft identifiers"); return false; },
                input -> { assertEquals("1-1-1-1-1", input); return Optional.of(DRAFT); }));
    }

    @Test
    void unknownOrForeignDraftUuidCannotConfirmWithoutATargetDraft() {
        assertTrue(PunishmentCommand.confirmationDraftId(DRAFT.toString(), id -> false,
                input -> Optional.empty()).isEmpty());
    }
}

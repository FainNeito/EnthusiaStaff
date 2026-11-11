package net.enthusia.staff.paper.config.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.paper.config.AtomicMessageConfiguration;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.MessageCatalog;
import net.enthusia.staff.paper.config.MessageConfigurationSnapshot;
import net.enthusia.staff.paper.config.MessageKey;
import org.junit.jupiter.api.Test;

class MessageConfigurationReloadActionTest {
    @Test
    void invalidCandidateLeavesPreviousMessagesAndDelegateUntouched() {
        MessageConfigurationSnapshot initial = defaults();
        AtomicMessageConfiguration active = new AtomicMessageConfiguration(initial);
        AtomicBoolean delegated = new AtomicBoolean();
        MessageConfigurationReloadAction action = new MessageConfigurationReloadAction(
                () -> {
                    delegated.set(true);
                    return unchanged();
                },
                () -> {
                    throw new ConfigurationValidationException("messages.yml is invalid");
                },
                active,
                ignored -> { }
        );

        ConfigurationReloadResult result = action.reload();

        assertEquals(ConfigurationReloadResult.Outcome.VALIDATION_FAILED, result.outcome());
        assertFalse(delegated.get());
        assertSame(initial, active.snapshot());
    }

    @Test
    void equivalentCandidateKeepsActiveSnapshotAndNoChangeOutcome() {
        MessageConfigurationSnapshot initial = defaults();
        MessageConfigurationSnapshot equivalent = defaults();
        AtomicMessageConfiguration active = new AtomicMessageConfiguration(initial);
        MessageConfigurationReloadAction action = new MessageConfigurationReloadAction(
                MessageConfigurationReloadActionTest::unchanged,
                () -> equivalent,
                active,
                ignored -> { }
        );

        ConfigurationReloadResult result = action.reload();

        assertEquals(ConfigurationReloadResult.Outcome.NO_CHANGES, result.outcome());
        assertSame(initial, active.snapshot());
    }

    @Test
    void successfulDelegatePublishesCandidateAtomically() {
        MessageConfigurationSnapshot initial = defaults();
        MessageConfigurationSnapshot candidate = withStatusDenial("Custom status denial");
        AtomicMessageConfiguration active = new AtomicMessageConfiguration(initial);
        MessageConfigurationReloadAction action = new MessageConfigurationReloadAction(
                MessageConfigurationReloadActionTest::unchanged,
                () -> candidate,
                active,
                ignored -> { }
        );

        ConfigurationReloadResult result = action.reload();

        assertTrue(result.successful());
        assertEquals(ConfigurationReloadResult.Outcome.APPLIED, result.outcome());
        assertSame(candidate, active.snapshot());
        assertEquals(
                "Custom status denial",
                active.snapshot().catalog().text(MessageKey.ESTAFF_STATUS_PERMISSION_DENIED)
        );
    }

    @Test
    void failedDelegateDoesNotPublishCandidate() {
        MessageConfigurationSnapshot initial = defaults();
        MessageConfigurationSnapshot candidate = withStatusDenial("Must not publish");
        AtomicMessageConfiguration active = new AtomicMessageConfiguration(initial);
        MessageConfigurationReloadAction action = new MessageConfigurationReloadAction(
                () -> new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.RESTART_REQUIRED,
                        "Restart required",
                        List.of("test"),
                        false
                ),
                () -> candidate,
                active,
                ignored -> { }
        );

        ConfigurationReloadResult result = action.reload();

        assertFalse(result.successful());
        assertSame(initial, active.snapshot());
    }

    private static MessageConfigurationSnapshot defaults() {
        return new MessageConfigurationSnapshot(
                MessageConfigurationSnapshot.CURRENT_SCHEMA_VERSION,
                MessageCatalog.builtIn()
        );
    }

    private static MessageConfigurationSnapshot withStatusDenial(String text) {
        EnumMap<MessageKey, String> templates = new EnumMap<>(MessageKey.class);
        templates.putAll(MessageCatalog.builtIn().templates());
        templates.put(MessageKey.ESTAFF_STATUS_PERMISSION_DENIED, text);
        return new MessageConfigurationSnapshot(
                MessageConfigurationSnapshot.CURRENT_SCHEMA_VERSION,
                new MessageCatalog(templates)
        );
    }

    private static ConfigurationReloadResult unchanged() {
        return new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.NO_CHANGES,
                "No reloadable settings changed",
                List.of(),
                false
        );
    }
}

package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class RoseChatAutomatedModerationProviderTest {
    @Test
    void aiPolicyIsAutomaticPublicThirtyDayMute() {
        ReasonPolicy policy = RoseChatAutomatedModerationProvider.policy();

        assertEquals("chat.ai-moderation", policy.id());
        assertTrue(policy.automaticDetectionAllowed());
        assertTrue(policy.publicByDefault());
        assertEquals(1, policy.steps().size());
        assertEquals(1, policy.steps().getFirst().sanctions().size());

        SanctionSpec sanction = policy.steps().getFirst().sanctions().getFirst();
        assertEquals(SanctionType.MUTE, sanction.type());
        assertEquals(SanctionLength.Kind.TEMPORARY, sanction.length().kind());
        assertEquals(Duration.ofDays(30), sanction.length().temporary().orElseThrow());
    }
}

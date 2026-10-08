package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.staff.PresenceContext;
import dev.rosewood.rosechat.api.staff.PresenceType;
import java.util.UUID;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import org.junit.jupiter.api.Test;

class RoseChatIntegrationPresenceTest {
    private static final UUID SUBJECT = UUID.fromString("7f7f7f7f-0000-0000-0000-000000000001");
    private static final UUID VIEWER = UUID.fromString("7f7f7f7f-0000-0000-0000-000000000002");

    @Test
    void suppressesLifecyclePresenceUntilDurableVanishStateIsKnown() {
        StaffVisibilityService visibility = visibility(false, true);

        assertFalse(RoseChatIntegration.shouldRenderLifecyclePresence(
                ignored -> false,
                visibility,
                new PresenceContext(SUBJECT, VIEWER, PresenceType.JOIN)
        ));
        assertFalse(RoseChatIntegration.shouldRenderLifecyclePresence(
                ignored -> false,
                visibility,
                new PresenceContext(SUBJECT, VIEWER, PresenceType.QUIT)
        ));
    }

    @Test
    void suppressesRealJoinAndQuitWhileSubjectRemainsVanishedEvenForAuthorizedViewer() {
        StaffVisibilityService visibility = visibility(true, true);

        assertFalse(RoseChatIntegration.shouldRenderLifecyclePresence(
                ignored -> true,
                visibility,
                new PresenceContext(SUBJECT, VIEWER, PresenceType.JOIN)
        ));
        assertFalse(RoseChatIntegration.shouldRenderLifecyclePresence(
                ignored -> true,
                visibility,
                new PresenceContext(SUBJECT, VIEWER, PresenceType.QUIT)
        ));
    }

    @Test
    void rendersRealJoinAndQuitOnlyWhenPresenceStateIsKnownAndSubjectIsNotVanished() {
        StaffVisibilityService visibility = visibility(false, false);

        assertTrue(RoseChatIntegration.shouldRenderLifecyclePresence(
                ignored -> true,
                visibility,
                new PresenceContext(SUBJECT, VIEWER, PresenceType.JOIN)
        ));
        assertTrue(RoseChatIntegration.shouldRenderLifecyclePresence(
                ignored -> true,
                visibility,
                new PresenceContext(SUBJECT, VIEWER, PresenceType.QUIT)
        ));
    }

    private static StaffVisibilityService visibility(boolean vanished, boolean canSee) {
        return new StaffVisibilityService() {
            @Override
            public boolean isVanished(UUID playerId) {
                return vanished;
            }

            @Override
            public boolean canSee(UUID viewerId, UUID targetId) {
                return canSee;
            }
        };
    }
}

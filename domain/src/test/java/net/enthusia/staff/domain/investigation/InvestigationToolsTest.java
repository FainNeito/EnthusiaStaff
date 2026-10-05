package net.enthusia.staff.domain.investigation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvestigationToolsTest {
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    @Test void patrolPrefersFreshTargetsAndKeepsFallbackWithBoundedHistory() {
        var history = new PatrolHistory(2, 2);
        UUID actor = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        history.visited(actor, a);
        assertEquals(List.of(b, c, a), history.preferFresh(actor, List.of(a, b, c)));
        history.visited(actor, b);
        history.visited(actor, c);
        assertEquals(List.of(a, b, c), history.preferFresh(actor, List.of(b, a, c)));
        history.forget(actor);
        assertEquals(List.of(c, b, a), history.preferFresh(actor, List.of(c, b, a)));
    }
    @Test void patrolActorCapacityAndTargetQuitPreventUnboundedStaleHistory() {
        var history = new PatrolHistory(1, 1);
        UUID actor = UUID.randomUUID(), other = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        history.visited(actor, a);
        history.visited(other, a);
        assertEquals(List.of(a, b), history.preferFresh(actor, List.of(a, b)));
        history.forget(a);
        assertEquals(List.of(a, b), history.preferFresh(other, List.of(a, b)));
    }
    @Test void activityIsImmutableBoundedAndSessionScoped() {
        var tracker = new PlayerActivityTracker(Clock.fixed(NOW, ZoneOffset.UTC), 1);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        tracker.record(a, PlayerActivityTracker.Activity.LEFT_CLICK);
        var snapshot = tracker.snapshot(a);
        assertEquals(NOW, snapshot.get(PlayerActivityTracker.Activity.LEFT_CLICK));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        tracker.record(b, PlayerActivityTracker.Activity.CROUCH);
        assertTrue(tracker.snapshot(a).isEmpty());
        tracker.forget(b);
        assertTrue(tracker.snapshot(b).isEmpty());
    }
    @Test void joinLimiterSuppressesReconnectsAndLimitsBusyJoinsPerViewer() {
        var limiter = new JoinAlertLimiter(2);
        UUID viewer = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(limiter.acquire(viewer, a, NOW));
        assertFalse(limiter.acquire(viewer, a, NOW.plusSeconds(5)));
        assertFalse(limiter.acquire(viewer, b, NOW.plusSeconds(1)));
        assertTrue(limiter.acquire(viewer, b, NOW.plusSeconds(2)));
        assertTrue(limiter.acquire(viewer, a, NOW.plusSeconds(300)));
    }
    @Test void flagValidationRejectsInvalidReasonsCategoriesAndExpiries() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new InvestigationFlag(id,id,id,"watch","",NOW,null,null));
        assertThrows(IllegalArgumentException.class, () -> new InvestigationFlag(id,id,id,"BAD","reason",NOW,null,null));
        assertThrows(IllegalArgumentException.class, () -> new InvestigationFlag(id,id,id,"watch","reason\n",NOW,null,null));
        assertThrows(IllegalArgumentException.class, () -> new InvestigationFlag(id,id,id,"watch","reason",NOW,NOW,null));
    }
}

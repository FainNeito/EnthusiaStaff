package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import org.junit.jupiter.api.Test;

final class OfflineDiscordPunishmentReviewTest {
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REQUEST_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String DISCORD_ID = "123456789012345678";
    private static final String DIFFERENT_DISCORD_ID = "234567890123456789";
    private static final Actor MODERATOR = new Actor(ACTOR_ID, "moderator", StaffRank.MOD);

    @Test
    void verifiedDiscordModeratorMayReviewOfflineWithBothGrants() {
        var service = service(() -> true, () -> true, (actor, discord) -> actor.equals(ACTOR_ID)
                && DISCORD_ID.equals(discord));

        assertDoesNotThrow(() -> service.requireReviewerContext(request(DISCORD_ID), MODERATOR, null));
    }

    @Test
    void noOfflineApprovalWhenFeatureDisabledDespiteRankAndPermission() {
        var service = service(() -> false, () -> true, (actor, discord) -> true);

        assertThrows(SecurityException.class,
                () -> service.requireReviewerContext(request(DISCORD_ID), MODERATOR, null));
    }

    @Test
    void noOfflineApprovalWithoutSeparatePermission() {
        var service = service(() -> true, () -> false, (actor, discord) -> true);

        assertThrows(SecurityException.class,
                () -> service.requireReviewerContext(request(DISCORD_ID), MODERATOR, null));
    }

    @Test
    void unknownOrStaleDiscordLinkCannotApproveEvenWhileInStaffMode() {
        var service = service(() -> true, () -> true,
                (actor, discord) -> actor.equals(ACTOR_ID) && DISCORD_ID.equals(discord));
        StaffSessionStore duty = activeSessionStore();
        assertThrows(SecurityException.class,
                () -> service.requireReviewerContext(request(DIFFERENT_DISCORD_ID), MODERATOR, duty));
        assertThrows(SecurityException.class,
                () -> service.requireReviewerContext(request(null), MODERATOR, duty));
        assertThrows(SecurityException.class,
                () -> service.requireReviewerContext(request("invalid"), MODERATOR, duty));
        assertDoesNotThrow(() -> service.requireReviewerContext(request(DISCORD_ID), MODERATOR, duty));
    }

    @Test
    void permissionRevocationBeforeCommitFailsSecondAuthorityCheck() {
        AtomicBoolean permitted = new AtomicBoolean(true);
        var service = service(() -> true, permitted::get, (actor, discord) -> true);

        assertDoesNotThrow(() -> service.requireReviewerContext(request(DISCORD_ID), MODERATOR, null));
        permitted.set(false);
        assertThrows(SecurityException.class,
                () -> service.requireReviewerContext(request(DISCORD_ID), MODERATOR, null));
    }

    @Test
    void activeMinecraftStaffModeNeedsLinkButNotSeparateOfflinePermission() {
        var service = service(() -> false, () -> false,
                (actor, discord) -> actor.equals(ACTOR_ID) && DISCORD_ID.equals(discord));

        assertDoesNotThrow(() ->
                service.requireReviewerContext(request(DISCORD_ID), MODERATOR, activeSessionStore()));
        assertThrows(SecurityException.class,
                () -> service.requireReviewerContext(request(DIFFERENT_DISCORD_ID),
                        MODERATOR, activeSessionStore()));
    }

    private static StaffWebReviewService.Request request(String discordId) {
        return new StaffWebReviewService.Request(ACTOR_ID, REQUEST_ID, "", discordId);
    }

    private static StaffWebReviewService service(
            java.util.function.Supplier<Boolean> feature,
            java.util.function.Supplier<Boolean> permission,
            java.util.function.BiPredicate<UUID, String> verifiedLink
    ) {
        var dependencies = new StaffWebReviewService.Dependencies(
                Clock.systemUTC(),
                () -> OperationalMode.ACTIVE,
                () -> { throw new AssertionError("eligibility must not commit a punishment"); },
                () -> { throw new AssertionError("use explicit duty fixture"); },
                feature,
                verifiedLink
        );
        return new StaffWebReviewService(dependencies,
                id -> MODERATOR, id -> Optional.empty(), id -> permission.get());
    }

    private static StaffSessionStore activeSessionStore() {
        var session = new StaffSessionSnapshot(
                UUID.randomUUID(), ACTOR_ID, "test-server", StaffSessionState.ACTIVE,
                false, 1, "0".repeat(64), new byte[]{1}, Instant.parse("2026-10-13T00:00:00Z"), 0L
        );
        InvocationHandler handler = (proxy, method, args) -> {
            if ("active".equals(method.getName())) {
                return Optional.of(session);
            }
            throw new UnsupportedOperationException(method.getName());
        };
        return (StaffSessionStore) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{StaffSessionStore.class}, handler);
    }
}

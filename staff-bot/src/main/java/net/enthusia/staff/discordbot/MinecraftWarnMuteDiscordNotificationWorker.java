package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.persistence.JdbcMinecraftWarnMuteDiscordNotificationStore;
import net.enthusia.staff.persistence.JdbcMinecraftWarnMuteDiscordNotificationStore.Lease;

/** Delivers linked-player Discord DMs for newly committed Minecraft warnings and mutes. */
final class MinecraftWarnMuteDiscordNotificationWorker {
    private static final int MIRROR_LIMIT = 50;
    private static final int CLAIM_LIMIT = 1;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE = Duration.ofSeconds(45);
    private static final Duration BASE_RETRY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY = Duration.ofMinutes(5);

    private final JdbcMinecraftWarnMuteDiscordNotificationStore store;
    private final JdaDiscordPunishmentGateway gateway;
    private final Clock clock;
    private final String workerId;

    MinecraftWarnMuteDiscordNotificationWorker(
            JdbcMinecraftWarnMuteDiscordNotificationStore store,
            JdaDiscordPunishmentGateway gateway,
            Clock clock,
            String workerId
    ) {
        if (store == null || gateway == null || clock == null
                || workerId == null || workerId.isBlank() || workerId.length() > 128) {
            throw new IllegalArgumentException(
                    "Minecraft warning/mute Discord notification worker configuration is invalid");
        }
        this.store = store;
        this.gateway = gateway;
        this.clock = clock;
        this.workerId = workerId;
        store.ensureCutover(clock.instant());
    }

    int runCycle() {
        Instant now = clock.instant();
        store.mirrorEligible(now, MIRROR_LIMIT);
        var work = store.claimDue(now, CLAIM_LIMIT, workerId, now.plus(LEASE));
        for (Lease lease : work) {
            deliver(lease);
        }
        return work.size();
    }

    private void deliver(Lease lease) {
        if (!lease.active()) {
            store.markDelivered(lease, clock.instant());
            return;
        }
        DiscordDeliveryOutcome outcome = gateway.notifyMinecraftWarningOrMute(lease);
        if (outcome == DiscordDeliveryOutcome.DELIVERED) {
            store.markDelivered(lease, clock.instant());
            return;
        }
        boolean retry = outcome == DiscordDeliveryOutcome.FAILED_RETRYABLE
                && lease.attemptCount() < MAX_ATTEMPTS;
        String errorCode = retry
                ? "MINECRAFT_WARN_MUTE_DM_RETRYABLE"
                : "MINECRAFT_WARN_MUTE_DM_FAILED";
        store.markFailed(lease, retry, retryAt(lease), errorCode);
    }

    private Instant retryAt(Lease lease) {
        if (lease.attemptCount() >= MAX_ATTEMPTS) {
            return clock.instant();
        }
        long multiplier = 1L << Math.min(Math.max(lease.attemptCount() - 1, 0), 16);
        Duration delay = BASE_RETRY.multipliedBy(multiplier);
        if (delay.compareTo(MAX_RETRY) > 0) {
            delay = MAX_RETRY;
        }
        return clock.instant().plus(delay);
    }

    static String workerId() {
        return "minecraft-warn-mute-dm-" + UUID.randomUUID();
    }
}

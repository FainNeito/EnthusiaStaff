package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class JdaPunishmentNotifierTest {
    private static final Instant NOW = Instant.parse("2026-10-05T05:00:00Z");
    private static final String TARGET_USER_ID = "123";
    private static final String GUILD_ID = "456";

    @Test
    void temporaryDiscordPunishmentIsReadableAndUsesDiscordExpiryTimestamp() {
        JdaPunishmentNotifier notifier = new JdaPunishmentNotifier(configuration());
        DiscordPunishment punishment = DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId(TARGET_USER_ID),
                new DiscordGuildId(GUILD_ID),
                new Actor(UUID.randomUUID(), "Staff", StaffRank.ADMIN),
                new DiscordPunishmentIntent(
                        DiscordConsequenceType.MUTE,
                        SanctionLength.temporary(Duration.ofDays(1)),
                        false,
                        false,
                        Optional.empty(),
                        "custom",
                        "Referencing a controversial historical figure\n"
                                + "Discord message reference: 1556501761831735297\n"
                                + "External evidence reference: private-ticket",
                        0,
                        true
                ),
                NOW,
                "issue"
        );

        String message = notifier.appliedMessage(punishment);

        assertTrue(message.startsWith("# Punishment Alert"));
        assertTrue(message.contains("You have been `muted` on the Enthusia SMP Discord for `1 day`."));
        assertTrue(message.contains("**Reason:** custom"));
        assertTrue(message.contains("**Staff explanation:** Referencing a controversial historical figure"));
        assertFalse(message.contains("Discord message reference:"));
        assertFalse(message.contains("External evidence reference:"));
        assertTrue(message.contains("<t:" + NOW.plus(Duration.ofDays(1)).getEpochSecond() + ":F>"));
        assertTrue(message.contains("<t:" + NOW.plus(Duration.ofDays(1)).getEpochSecond() + ":R>"));
        assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_CHANNEL));
        assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_SITE));
    }

    @Test
    void warningDoesNotPretendToBePermanent() {
        JdaPunishmentNotifier notifier = new JdaPunishmentNotifier(configuration());
        DiscordPunishment punishment = DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId(TARGET_USER_ID),
                new DiscordGuildId(GUILD_ID),
                new Actor(UUID.randomUUID(), "Staff", StaffRank.ADMIN),
                new DiscordPunishmentIntent(
                        DiscordConsequenceType.WARNING,
                        SanctionLength.instant(),
                        false,
                        false,
                        Optional.empty(),
                        "Chat behavior",
                        "",
                        0,
                        true
                ),
                NOW,
                "issue"
        );

        String message = notifier.appliedMessage(punishment);

        assertTrue(message.contains("You have been `warned` on the Enthusia SMP Discord."));
        assertFalse(message.contains("**Expires:**"));
    }

    @Test
    void linkedMinecraftBanMessageUsesPlayerNameReasonAndAppealRoutes() {
        String message = JdaPunishmentNotifier.minecraftBanMessage(
                new JdaPunishmentNotifier.MinecraftBanNotification(
                        TARGET_USER_ID,
                        "ExamplePlayer",
                        "Cheating",
                        NOW,
                        Optional.of(NOW.plus(Duration.ofDays(30)))
                )
        );

        assertTrue(message.startsWith("# Punishment Alert"));
        assertTrue(message.contains("Minecraft account `ExamplePlayer`"));
        assertTrue(message.contains("**Reason:** Cheating"));
        assertTrue(message.contains("<t:" + NOW.plus(Duration.ofDays(30)).getEpochSecond() + ":R>"));
        assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_CHANNEL));
        assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_SITE));
    }

    private static DiscordPunishmentConfiguration configuration() {
        return new DiscordPunishmentConfiguration(
                new DiscordAuthorizationLimits(
                        Duration.ofHours(1),
                        Duration.ofDays(7),
                        Duration.ofDays(30),
                        Duration.ofDays(30)
                ),
                TARGET_USER_ID,
                Set.of(GUILD_ID),
                "Support message",
                Duration.ofMinutes(1),
                Duration.ofSeconds(1)
        );
    }
}

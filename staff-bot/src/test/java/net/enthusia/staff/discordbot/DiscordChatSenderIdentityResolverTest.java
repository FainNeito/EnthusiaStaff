package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DiscordChatSenderIdentityResolverTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final String DISCORD_ID = "1410303324745371709";

    @Test
    void cachesCurrentLinkForShortPresentationWindow() {
        MutableClock clock = new MutableClock(NOW);
        AtomicInteger lookups = new AtomicInteger();
        DiscordChatSenderIdentityResolver resolver = new DiscordChatSenderIdentityResolver(
                clock,
                8,
                Duration.ofSeconds(30),
                ignored -> {
                    lookups.incrementAndGet();
                    return Optional.of(DISCORD_ID);
                }
        );
        UUID player = UUID.randomUUID();

        assertEquals(Optional.of(DISCORD_ID), resolver.resolve(player));
        assertEquals(Optional.of(DISCORD_ID), resolver.resolve(player));
        assertEquals(1, lookups.get());

        clock.advanceMillis(30_001L);
        assertEquals(Optional.of(DISCORD_ID), resolver.resolve(player));
        assertEquals(2, lookups.get());
    }

    @Test
    void invalidAndFailedLookupsDegradeToUnlinkedPresentation() {
        MutableClock clock = new MutableClock(NOW);
        DiscordChatSenderIdentityResolver invalid = new DiscordChatSenderIdentityResolver(
                clock, 8, Duration.ofSeconds(30), ignored -> Optional.of("not-a-snowflake"));
        DiscordChatSenderIdentityResolver failed = new DiscordChatSenderIdentityResolver(
                clock, 8, Duration.ofSeconds(30), ignored -> {
                    throw new IllegalStateException("database unavailable");
                });

        assertTrue(invalid.resolve(UUID.randomUUID()).isEmpty());
        assertTrue(failed.resolve(UUID.randomUUID()).isEmpty());
    }

    @Test
    void capacityRemainsBoundedByEvictingOldestEntry() {
        MutableClock clock = new MutableClock(NOW);
        AtomicInteger lookups = new AtomicInteger();
        DiscordChatSenderIdentityResolver resolver = new DiscordChatSenderIdentityResolver(
                clock,
                2,
                Duration.ofMinutes(1),
                ignored -> {
                    lookups.incrementAndGet();
                    return Optional.of(DISCORD_ID);
                }
        );
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        resolver.resolve(first);
        resolver.resolve(second);
        resolver.resolve(third);
        assertEquals(2, resolver.size());

        resolver.resolve(first);
        assertEquals(4, lookups.get());
        assertEquals(2, resolver.size());

        resolver.clear();
        assertEquals(0, resolver.size());
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis;

        private MutableClock(long initialMillis) {
            this.millis = new AtomicLong(initialMillis);
        }

        void advanceMillis(long delta) {
            millis.addAndGet(delta);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }
}

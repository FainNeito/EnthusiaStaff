package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Deterministic offline saturation and lifecycle tests: no network or live chat. */
class AiMirrorRegistrationQueueTest {
    private static final int QUEUE_CAPACITY = 64;
    private static final int MAX_ACCEPTED = QUEUE_CAPACITY + 1;
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;

    @Test
    void saturationNeverEnqueuesBeyondTheBoundOrBlocksChat() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch allAcceptedFinished = new CountDownLatch(MAX_ACCEPTED);
        try (AiMirrorRegistrationPublisher publisher = new AiMirrorRegistrationPublisher(
                configuration(), request -> {
                    if (posts.incrementAndGet() == 1) {
                        firstStarted.countDown();
                        if (!releaseFirst.await(8, TimeUnit.SECONDS)) {
                            throw new IOException("synthetic worker barrier timed out");
                        }
                    }
                    allAcceptedFinished.countDown();
                    return 200;
                })) {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(firstStarted.await(3, TimeUnit.SECONDS));

            // One worker is occupied; only 64 more callbacks may be queued.
            for (int i = 0; i < 200; i++) {
                publisher.published(receipt());
            }
            releaseFirst.countDown();
            assertTrue(allAcceptedFinished.await(8, TimeUnit.SECONDS));
            assertEquals(MAX_ACCEPTED, posts.get());
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void pauseDiscardsQueuedCallbacksAndResumeOnlyAcceptsFreshOnes() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch freshDelivered = new CountDownLatch(1);
        try (AiMirrorRegistrationPublisher publisher = new AiMirrorRegistrationPublisher(
                configuration(), request -> {
                    int call = posts.incrementAndGet();
                    if (call == 1) {
                        firstStarted.countDown();
                        if (!releaseFirst.await(8, TimeUnit.SECONDS)) {
                            throw new IOException("synthetic worker barrier timed out");
                        }
                    } else {
                        freshDelivered.countDown();
                    }
                    return 200;
                })) {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(firstStarted.await(3, TimeUnit.SECONDS));
            for (int i = 0; i < 30; i++) {
                publisher.published(receipt());
            }
            publisher.pause();
            publisher.published(receipt()); // Not accepted while paused.
            releaseFirst.countDown();
            // Previously enqueued work must not survive the pause.
            assertTrue(!freshDelivered.await(200, TimeUnit.MILLISECONDS));
            assertEquals(1, posts.get());

            publisher.resume();
            publisher.published(receipt());
            assertTrue(freshDelivered.await(3, TimeUnit.SECONDS));
            assertEquals(2, posts.get());
            publisher.close();
            publisher.published(receipt());
            assertEquals(2, posts.get());
        } finally {
            releaseFirst.countDown();
        }
    }

    private static AiMirrorPublicationConfig configuration() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD, Set.of(CHANNEL),
                "discord_general");
    }

    private static DiscordChatPublication receipt() {
        return new DiscordChatPublication(
                GUILD, CHANNEL, "1234567890123456787",
                "rosechat:synthetic:queue", "queued synthetic message");
    }
}

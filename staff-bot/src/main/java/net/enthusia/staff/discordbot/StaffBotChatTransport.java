package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import net.enthusia.staff.protocol.ChatBridgeInboundMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.PersistentChannelClient;
import net.enthusia.staff.protocol.TlsContextLoader;

/** Outbound-connecting authenticated StaffBot peer for ephemeral chat frames. */
final class StaffBotChatTransport implements StaffBotChatLifecycle, DiscordChatIngress {
    private static final System.Logger LOGGER = System.getLogger(StaffBotChatTransport.class.getName());

    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    private final StaffBotChatIngress ingress;
    private final StaffBotRenderedChatIngress renderedIngress;
    private final Map<Long, StaffBotChatBridgeConfiguration.Route> ingressRoutes;
    private final PersistentChannelClient client;
    private final ThreadPoolExecutor discordSender;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean acceptingDiscord = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();

    private StaffBotChatTransport(
            StaffBotChatIngress ingress,
            StaffBotRenderedChatIngress renderedIngress,
            Map<Long, StaffBotChatBridgeConfiguration.Route> ingressRoutes,
            PersistentChannelClient client,
            int queueCapacity
    ) {
        this.ingress = Objects.requireNonNull(ingress, "ingress");
        this.renderedIngress = Objects.requireNonNull(renderedIngress, "renderedIngress");
        this.ingressRoutes = Map.copyOf(Objects.requireNonNull(ingressRoutes, "ingressRoutes"));
        this.client = Objects.requireNonNull(client, "client");
        this.discordSender = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-StaffBot-Discord-Ingress");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    static StaffBotChatTransport create(
            StaffBotChatBridgeConfiguration configuration,
            DiscordChatEgress egress,
            DiscordRenderedChatEgress renderedEgress
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(egress, "egress");
        Objects.requireNonNull(renderedEgress, "renderedEgress");

        StaffBotChatIngress ingress = new StaffBotChatIngress(
                configuration,
                Clock.systemUTC(),
                egress
        );
        StaffBotRenderedChatIngress renderedIngress = new StaffBotRenderedChatIngress(
                configuration,
                Clock.systemUTC(),
                renderedEgress
        );
        try {
            SSLContext tls = tlsContext(configuration);
            PersistentChannelClient client = new PersistentChannelClient(
                    new PersistentChannelClient.Configuration(
                            StaffBotChatBridgeConfiguration.PEER_ID,
                            configuration.host(),
                            configuration.port(),
                            configuration.clientKey(),
                            StaffBotChatBridgeConfiguration.PROXY_ID,
                            configuration.proxyKey(),
                            tls
                    ),
                    Clock.systemUTC(),
                    envelope -> ingress.accept(envelope) || renderedIngress.accept(envelope),
                    StaffBotChatTransport::connectionState
            );
            return new StaffBotChatTransport(
                    ingress,
                    renderedIngress,
                    configuration.ingressRoutes(),
                    client,
                    configuration.queueCapacity()
            );
        } catch (RuntimeException failure) {
            renderedIngress.close();
            ingress.close();
            throw failure;
        }
    }

    @Override
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("StaffBot chat transport is closed");
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            client.start();
        } catch (RuntimeException failure) {
            started.set(false);
            throw failure;
        }
    }

    @Override
    public void resume() {
        if (!closed.get()) {
            generation.incrementAndGet();
            acceptingDiscord.set(true);
            ingress.resume();
            renderedIngress.resume();
        }
    }

    @Override
    public void pause() {
        acceptingDiscord.set(false);
        generation.incrementAndGet();
        discordSender.getQueue().clear();
        ingress.pause();
        renderedIngress.pause();
    }

    @Override
    public boolean offer(ChatBridgeInboundMessage message) {
        Objects.requireNonNull(message, "message");
        if (!acceptingDiscord.get() || !started.get() || closed.get() || message.isExpired(System.currentTimeMillis())) {
            return false;
        }
        StaffBotChatBridgeConfiguration.Route expected = ingressRoutes.get(message.sourceDiscordChannelId());
        if (expected == null
                || !expected.sourceServerId().equals(message.targetServerId())
                || !expected.logicalChannelId().equals(message.logicalChannelId())
                || !client.connected()) {
            return false;
        }
        String payload;
        try {
            payload = ChatBridgeMessages.encodeInbound(message);
        } catch (IllegalArgumentException failure) {
            return false;
        }
        long expectedGeneration = generation.get();
        try {
            discordSender.execute(() -> sendDiscordInbound(expectedGeneration, message, payload));
            return true;
        } catch (RejectedExecutionException failure) {
            return false;
        }
    }

    private void sendDiscordInbound(
            long expectedGeneration,
            ChatBridgeInboundMessage message,
            String payload
    ) {
        if (!acceptingDiscord.get()
                || generation.get() != expectedGeneration
                || !client.connected()
                || message.isExpired(System.currentTimeMillis())) {
            return;
        }
        try {
            client.send(message.eventId(), ChatBridgeMessages.INBOUND, payload, ACK_TIMEOUT).join();
        } catch (RuntimeException ignored) {
            // Best-effort chat drops on transport failure; serial ACK wait preserves queue backpressure.
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ingress.pause();
        renderedIngress.pause();
        acceptingDiscord.set(false);
        generation.incrementAndGet();
        discordSender.shutdownNow();
        client.close();
        renderedIngress.close();
        ingress.close();
    }

    private static SSLContext tlsContext(StaffBotChatBridgeConfiguration configuration) {
        char[] password = configuration.trustStorePassword();
        try {
            return TlsContextLoader.client(configuration.trustStore(), password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void connectionState(String state) {
        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(System.Logger.Level.INFO, "staff_bot_chat_transport state={0}", state);
        }
    }
}

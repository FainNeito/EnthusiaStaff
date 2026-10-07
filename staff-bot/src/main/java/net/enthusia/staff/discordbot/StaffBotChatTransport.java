package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import net.enthusia.staff.protocol.PersistentChannelClient;
import net.enthusia.staff.protocol.TlsContextLoader;

/** Outbound-connecting authenticated StaffBot peer for ephemeral chat frames. */
final class StaffBotChatTransport implements StaffBotChatLifecycle {
    private static final System.Logger LOGGER = System.getLogger(StaffBotChatTransport.class.getName());

    private final StaffBotChatIngress ingress;
    private final PersistentChannelClient client;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private StaffBotChatTransport(
            StaffBotChatIngress ingress,
            PersistentChannelClient client
    ) {
        this.ingress = Objects.requireNonNull(ingress, "ingress");
        this.client = Objects.requireNonNull(client, "client");
    }

    static StaffBotChatTransport create(
            StaffBotChatBridgeConfiguration configuration,
            DiscordChatEgress egress
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(egress, "egress");

        StaffBotChatIngress ingress = new StaffBotChatIngress(
                configuration,
                Clock.systemUTC(),
                egress
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
                    ingress::accept,
                    StaffBotChatTransport::connectionState
            );
            return new StaffBotChatTransport(ingress, client);
        } catch (RuntimeException failure) {
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
            ingress.resume();
        }
    }

    @Override
    public void pause() {
        ingress.pause();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ingress.pause();
        client.close();
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

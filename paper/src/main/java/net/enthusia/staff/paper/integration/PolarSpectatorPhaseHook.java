package net.enthusia.staff.paper.integration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import top.polar.api.PolarApi;
import top.polar.api.PolarApiAccessor;
import top.polar.api.event.listener.RegisteredListener;
import top.polar.api.event.listener.repository.EventListenerRepository;
import top.polar.api.exception.PolarNotLoadedException;
import top.polar.api.loader.LoaderApi;
import top.polar.api.user.event.MitigationEvent;

/**
 * Polar-loaded half of the Spectator compatibility integration.
 *
 * <p>Polar requires its enable callback to be registered during plugin load. The callback then
 * registers through Polar's own event repository. No Bukkit player state is read on Polar's event
 * thread; eligibility is a concurrent UUID lookup maintained by the non-Polar compatibility half.</p>
 */
public final class PolarSpectatorPhaseHook implements Runnable {
    private static final AtomicReference<PolarSpectatorPhaseHook> ACTIVE = new AtomicReference<>();

    private final Logger logger;
    private boolean closed;
    private EventListenerRepository events;
    private RegisteredListener<MitigationEvent> registration;

    private PolarSpectatorPhaseHook(JavaPlugin plugin) {
        logger = Objects.requireNonNull(plugin, "plugin").getLogger();
    }

    public static void registerEnableCallback(JavaPlugin plugin) {
        PolarSpectatorPhaseHook hook = new PolarSpectatorPhaseHook(plugin);
        PolarSpectatorPhaseHook previous = ACTIVE.getAndSet(hook);
        if (previous != null) {
            previous.unregister();
        }
        LoaderApi.registerEnableCallback(hook);
    }

    @Override
    public synchronized void run() {
        if (closed || registration != null) {
            return;
        }
        try {
            PolarApi api = PolarApiAccessor.access().get();
            if (api == null) {
                throw new IllegalStateException("Polar API became unavailable during enable callback");
            }
            events = api.events().repository();
            registration = events.registerListener(MitigationEvent.class, this::onMitigation);
            logger.info(
                    "Polar compatibility active: PHASE mitigation is exempted only for explicit staff in Spectator"
            );
        } catch (PolarNotLoadedException | RuntimeException failure) {
            logger.log(Level.WARNING, "Polar Spectator phase compatibility could not start", failure);
        }
    }

    private void onMitigation(MitigationEvent event) {
        if (event.cancelled()) {
            return;
        }
        boolean eligible = PolarSpectatorPhaseCompatibility.eligible(event.user().uuid());
        if (PolarSpectatorPhasePolicy.shouldCancelMitigation(event.check().type().name(), eligible)) {
            event.cancelled(true);
        }
    }

    public static void close() {
        PolarSpectatorPhaseHook hook = ACTIVE.getAndSet(null);
        if (hook != null) {
            hook.unregister();
        }
    }

    private synchronized void unregister() {
        if (closed) {
            return;
        }
        closed = true;
        EventListenerRepository currentEvents = events;
        RegisteredListener<MitigationEvent> currentRegistration = registration;
        if (currentEvents != null && currentRegistration != null) {
            currentEvents.unregisterListener(currentRegistration);
        }
    }
}

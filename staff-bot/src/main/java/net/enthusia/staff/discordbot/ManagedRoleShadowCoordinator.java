package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Runs bounded, read-only parity scans for provider-neutral managed-role claims.
 *
 * <p>No Discord mutation exists in this class. A zero-drift summary is therefore evidence for the
 * migration window only; it is never authorization to remove DiscordSRV or enable enforcement.</p>
 */
final class ManagedRoleShadowCoordinator implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(ManagedRoleShadowCoordinator.class.getName());
    private static final int MAX_DRIFT_LOGS = 20;
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(20);

    enum State {
        MATCH,
        DRIFT,
        ROLE_MISSING,
        ROLE_AMBIGUOUS,
        DELETE_MATCH,
        DELETE_DRIFT,
        DELETE_UNRESOLVED
    }

    record ClaimResult(
            String resourceId,
            State state,
            int desiredMembers,
            int observedMembers,
            int missingMembers,
            int extraMembers,
            int unlinkedMinecraftAccounts
    ) {
        ClaimResult {
            if (resourceId == null || resourceId.isBlank()
                    || state == null
                    || desiredMembers < 0
                    || observedMembers < 0
                    || missingMembers < 0
                    || extraMembers < 0
                    || unlinkedMinecraftAccounts < 0) {
                throw new IllegalArgumentException("managed-role shadow result is invalid");
            }
        }

        boolean matches() {
            return state == State.MATCH || state == State.DELETE_MATCH;
        }
    }

    private final ManagedRoleShadowService service;
    private final StaffBotWorkerPool workers;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Guild> guild = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean cycleInFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object cycleMonitor = new Object();

    ManagedRoleShadowCoordinator(ManagedRoleShadowService service, StaffBotWorkerPool workers) {
        if (service == null || workers == null) {
            throw new IllegalArgumentException("managed-role shadow coordinator dependencies must be present");
        }
        this.service = service;
        this.workers = workers;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("staff-bot-managed-role-shadow").factory());
    }

    void enable(Guild activeGuild) {
        if (activeGuild == null || closed.get()) {
            throw new IllegalArgumentException("managed-role shadow guild must be present");
        }
        guild.set(activeGuild);
        scheduleOnce();
        requestCycle();
    }

    void disable() {
        guild.set(null);
    }

    private void scheduleOnce() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        long delay = service.configuration().interval().toSeconds();
        scheduler.scheduleWithFixedDelay(this::requestCycle, delay, delay, TimeUnit.SECONDS);
    }

    private void requestCycle() {
        if (closed.get() || guild.get() == null || !cycleInFlight.compareAndSet(false, true)) {
            return;
        }
        if (!workers.tryExecute(this::runCycle)) {
            completeCycle();
        }
    }

    private void runCycle() {
        try {
            Guild active = guild.get();
            if (active == null || closed.get()) {
                return;
            }
            ManagedRoleShadowService.Snapshot snapshot = service.snapshot();
            List<ManagedRoleShadowService.ResolvedClaim> claims = service.resolve(snapshot);
            List<Member> members = loadMembers(active);
            if (guild.get() != active || closed.get()) {
                return;
            }
            logSummary(claims, members, active, snapshot.truncated());
        } catch (RuntimeException exception) {
            logFailure(exception);
        } finally {
            completeCycle();
        }
    }

    private static List<Member> loadMembers(Guild guild) {
        try {
            return List.copyOf(guild.loadMembers().get());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("managed-role shadow member load failed", exception);
        }
    }

    private static void logSummary(
            List<ManagedRoleShadowService.ResolvedClaim> claims,
            List<Member> members,
            Guild guild,
            boolean truncated
    ) {
        int matches = 0;
        int drifts = 0;
        int missingMembers = 0;
        int extraMembers = 0;
        int unlinked = 0;
        List<ClaimResult> driftDetails = new ArrayList<>();

        for (ManagedRoleShadowService.ResolvedClaim claim : claims) {
            ClaimResult result = compare(claim, members, guild);
            missingMembers += result.missingMembers();
            extraMembers += result.extraMembers();
            unlinked += result.unlinkedMinecraftAccounts();
            if (result.matches()) {
                matches++;
            } else {
                drifts++;
                if (driftDetails.size() < MAX_DRIFT_LOGS) {
                    driftDetails.add(result);
                }
            }
        }

        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "managed_role_shadow_summary complete={0} claims={1} matches={2} drift={3} "
                            + "missing_members={4} extra_members={5} unlinked_minecraft={6}",
                    !truncated,
                    claims.size(),
                    matches,
                    drifts,
                    missingMembers,
                    extraMembers,
                    unlinked
            );
        }
        if (truncated && LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "managed_role_shadow_incomplete reason=claim_limit_exceeded");
        }
        for (ClaimResult result : driftDetails) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "managed_role_shadow_drift resource={0} state={1} desired={2} observed={3} "
                                + "missing={4} extra={5} unlinked_minecraft={6}",
                        safeResource(result.resourceId()),
                        result.state(),
                        result.desiredMembers(),
                        result.observedMembers(),
                        result.missingMembers(),
                        result.extraMembers(),
                        result.unlinkedMinecraftAccounts()
                );
            }
        }
    }

    static ClaimResult compare(
            ManagedRoleShadowService.ResolvedClaim resolved,
            List<Member> members,
            Guild guild
    ) {
        ManagedRoleShadowService.Claim claim = resolved.claim();
        if (claim.delete() && claim.displayName().isEmpty()) {
            return result(resolved, State.DELETE_UNRESOLVED, Set.of(), Set.of());
        }

        List<Role> roles = guild.getRoles().stream()
                .filter(role -> role.getName().equals(claim.displayName()))
                .filter(role -> !role.isPublicRole() && !role.isManaged())
                .toList();

        if (roles.size() > 1) {
            return result(resolved, State.ROLE_AMBIGUOUS, resolved.desiredDiscordUserIds(), Set.of());
        }
        if (roles.isEmpty()) {
            State state = claim.delete() ? State.DELETE_MATCH : State.ROLE_MISSING;
            return result(resolved, state, resolved.desiredDiscordUserIds(), Set.of());
        }
        if (claim.delete()) {
            Set<String> observed = holders(members, roles.getFirst());
            return result(resolved, State.DELETE_DRIFT, Set.of(), observed);
        }

        Set<String> desired = resolved.desiredDiscordUserIds();
        Set<String> observed = holders(members, roles.getFirst());
        State state = desired.equals(observed) ? State.MATCH : State.DRIFT;
        return result(resolved, state, desired, observed);
    }

    private static ClaimResult result(
            ManagedRoleShadowService.ResolvedClaim resolved,
            State state,
            Set<String> desired,
            Set<String> observed
    ) {
        Set<String> missing = new LinkedHashSet<>(desired);
        missing.removeAll(observed);
        Set<String> extra = new LinkedHashSet<>(observed);
        extra.removeAll(desired);
        return new ClaimResult(
                resolved.claim().resourceId(),
                state,
                desired.size(),
                observed.size(),
                missing.size(),
                extra.size(),
                resolved.unlinkedMinecraftAccounts()
        );
    }

    private static Set<String> holders(List<Member> members, Role role) {
        Set<String> holders = new LinkedHashSet<>();
        String roleId = role.getId();
        for (Member member : members) {
            boolean present = member.getRoles().stream().anyMatch(candidate -> roleId.equals(candidate.getId()));
            if (present) {
                holders.add(member.getId());
            }
        }
        return Set.copyOf(holders);
    }

    private static String safeResource(String resourceId) {
        return resourceId.length() <= 12 ? resourceId : resourceId.substring(0, 12);
    }

    private static void logFailure(RuntimeException exception) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "managed_role_shadow_cycle_failed type={0}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void completeCycle() {
        cycleInFlight.set(false);
        synchronized (cycleMonitor) {
            cycleMonitor.notifyAll();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        disable();
        scheduler.shutdownNow();
        long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
        synchronized (cycleMonitor) {
            while (cycleInFlight.get()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException(
                            "managed-role shadow cycle did not quiesce before shutdown timeout");
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(cycleMonitor, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("managed-role shadow shutdown was interrupted", exception);
                }
            }
        }
    }
}

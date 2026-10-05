package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.*;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.staff.VanishRecord;
import org.junit.jupiter.api.Test;

// Arrays deliberately exercise each argument position; proxies use the actual API class loader.
@SuppressWarnings({"PMD.AvoidInstantiatingObjectsInLoops", "PMD.JeeClassLoader"})
class VelocityPlayerSuggestionsTest {
    private static final String ALPHA_NAME = "Alpha";
    private static final String VIEW = "enthusiastaff.alts.view";
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID ALPHA = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    @Test void knownOfflinePlayersAreSuggestedAsynchronouslyAndHiddenStaffAreExcluded() {
        Fixture fixture = new Fixture();
        var result = fixture.suggestions.suggest(fixture.viewer, "a", VIEW);
        assertFalse(result.isDone());
        assertEquals(0, fixture.reads.get());
        fixture.tasks.removeFirst().run();
        assertEquals(List.of(ALPHA_NAME), result.join());
        assertEquals(1, fixture.reads.get());
    }

    @Test void unauthorizedAndRevokedRequestsDoNotReadOrExposeNames() {
        Fixture fixture = new Fixture();
        var result = fixture.suggestions.suggest(fixture.viewer, "a", VIEW);
        fixture.permissions.clear();
        fixture.tasks.removeFirst().run();
        assertEquals(List.of(), result.join());
        assertEquals(0, fixture.reads.get());
        assertEquals(List.of(), fixture.suggestions.suggest(fixture.viewer, "a", VIEW).join());
        assertTrue(fixture.tasks.isEmpty());
    }

    @Test void saturatedRequestsAreBoundedAndProviderFailureReleasesCapacity() {
        Fixture fixture = new Fixture();
        List<CompletableFuture<List<String>>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) { requests.add(fixture.suggestions.suggest(fixture.viewer, "", VIEW)); }
        assertEquals(List.of(), fixture.suggestions.suggest(fixture.viewer, "", VIEW).join());
        fixture.failure = true;
        fixture.tasks.removeFirst().run();
        assertEquals(List.of(), requests.getFirst().join());
        var next = fixture.suggestions.suggest(fixture.viewer, "", VIEW);
        assertFalse(next.isDone());
        assertEquals(8, fixture.tasks.size());
        fixture.tasks.forEach(Runnable::run);
        assertEquals(List.of(), next.join());
    }

    @Test void rejectedExecutorAndMissingStorageFailClosed() {
        Fixture fixture = new Fixture();
        var rejected = new VelocityPlayerSuggestions(fixture.proxy, () -> fixture.directory,
                () -> fixture.vanish, () -> task -> { throw new RejectedExecutionException(); });
        for (int i = 0; i < 12; i++) {
            assertEquals(List.of(), rejected.suggest(fixture.viewer, "", VIEW).join());
        }
        var missing = new VelocityPlayerSuggestions(fixture.proxy, () -> null, () -> null, () -> Runnable::run);
        assertEquals(List.of(), missing.suggest(fixture.viewer, "", VIEW).join());
    }

    @Test void bothAltTargetsCompleteButReasonsAndUnauthorizedReopenDoNot() {
        for (String action : List.of("link", "approve", "household", "notrelated", "unlink", "reopen")) {
            assertTrue(VelocityPlayerSuggestions.targetPosition(new String[]{action, ""}));
            assertTrue(VelocityPlayerSuggestions.targetPosition(new String[]{action, ALPHA_NAME, ""}));
            assertFalse(VelocityPlayerSuggestions.targetPosition(new String[]{action, ALPHA_NAME, "Beta", ""}));
        }
        assertFalse(VelocityPlayerSuggestions.targetPosition(new String[]{"invalid", ""}));
        assertEquals(List.of("approve"), VelocityPlayerSuggestions.operations("Ap", false));
        assertEquals(List.of(), VelocityPlayerSuggestions.operations("re", false));
        assertEquals(List.of("reopen"), VelocityPlayerSuggestions.operations("re", true));
        assertTrue(VelocityPlayerSuggestions.validPrefix("*Bedrock"));
        assertTrue(VelocityPlayerSuggestions.validPrefix(""));
        assertFalse(VelocityPlayerSuggestions.validPrefix("two words"));
        assertFalse(VelocityPlayerSuggestions.validPrefix("a".repeat(33)));
    }

    private static final class Fixture {
        private final List<Runnable> tasks = new ArrayList<>();
        private final AtomicInteger reads = new AtomicInteger();
        private final Set<String> permissions = new HashSet<>(Set.of(VIEW, "enthusiastaff.rank.helper"));
        private boolean failure;
        private final Player viewer = player(VIEWER, "Helper", permissions);
        private final Player hidden = player(ADMIN, "Admin", Set.of("enthusiastaff.rank.admin"));
        private final ProxyServer proxy = stub(ProxyServer.class, (name, args) -> switch (name) {
            case "getAllPlayers" -> List.of(viewer, hidden);
            case "getPlayer" -> ADMIN.equals(args[0]) ? Optional.of(hidden) : Optional.empty();
            default -> null;
        });
        private final PlayerDirectory directory = stub(PlayerDirectory.class, (name, args) -> {
            if (!name.equals("search")) { return null; }
            reads.incrementAndGet();
            if (failure) { throw new IllegalStateException("provider unavailable"); }
            assertEquals(50, args[1]);
            return List.of(new PlayerIdentity(ALPHA, Optional.of(ALPHA_NAME), PlayerPlatform.JAVA,
                    Instant.EPOCH, Instant.EPOCH));
        });
        private final VanishStore vanish = stub(VanishStore.class, (name, args) -> List.of(
                new VanishRecord(ADMIN, StaffRank.ADMIN, Instant.EPOCH, 1)));
        private final VelocityPlayerSuggestions suggestions = new VelocityPlayerSuggestions(
                proxy, () -> directory, () -> vanish, () -> tasks::add);
    }

    private static Player player(UUID id, String username, Set<String> permissions) {
        return stub(Player.class, (name, args) -> switch (name) {
            case "getUniqueId" -> id;
            case "getUsername" -> username;
            case "hasPermission" -> permissions.contains(args[0]);
            default -> null;
        });
    }

    private static <T> T stub(Class<T> type, java.util.function.BiFunction<String, Object[], Object> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> calls.apply(method.getName(), args)));
    }
}

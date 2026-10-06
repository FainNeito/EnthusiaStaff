package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;

final class ManagedRoleShadowCoordinatorTest {
    @Test
    void exactRoleIdDisambiguatesDuplicateDisplayNames() {
        Role selected = role("1552390213500928122", "Playtime XII");
        Role duplicate = role("1552390213500928999", "Playtime XII");
        Member desired = member("2000000000000000001", List.of(selected));
        Guild guild = guild(List.of(selected, duplicate));

        ManagedRoleShadowCoordinator.ClaimResult result = ManagedRoleShadowCoordinator.compare(
                resolved(Optional.of(selected.getId()), Set.of(desired.getId()), false),
                List.of(desired),
                guild
        );

        assertEquals(ManagedRoleShadowCoordinator.State.MATCH, result.state());
        assertEquals(1, result.desiredMembers());
        assertEquals(1, result.observedMembers());
    }

    @Test
    void nameOnlyClaimRejectsAmbiguousDuplicateRoles() {
        Role first = role("1552390213500928122", "Guild Alpha");
        Role second = role("1552390213500928999", "Guild Alpha");

        ManagedRoleShadowCoordinator.ClaimResult result = ManagedRoleShadowCoordinator.compare(
                resolved(Optional.empty(), Set.of(), false),
                List.of(),
                guild(List.of(first, second))
        );

        assertEquals(ManagedRoleShadowCoordinator.State.ROLE_AMBIGUOUS, result.state());
    }

    @Test
    void deleteWithExactRoleIdMatchesWhenRoleIsAlreadyGone() {
        ManagedRoleShadowCoordinator.ClaimResult result = ManagedRoleShadowCoordinator.compare(
                resolved(Optional.of("1552390213500928122"), Set.of(), true),
                List.of(),
                guild(List.of())
        );

        assertEquals(ManagedRoleShadowCoordinator.State.DELETE_MATCH, result.state());
    }

    private static ManagedRoleShadowService.ResolvedClaim resolved(
            Optional<String> roleId,
            Set<String> desiredDiscordUsers,
            boolean delete
    ) {
        ManagedRoleShadowService.Claim claim = new ManagedRoleShadowService.Claim(
                "managed-role:test",
                "resource",
                "playtime-numerals",
                "tier:xii",
                "Playtime XII",
                roleId,
                Set.of(),
                delete
        );
        return new ManagedRoleShadowService.ResolvedClaim(claim, desiredDiscordUsers, 0);
    }

    private static Guild guild(List<Role> roles) {
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class<?>[] {Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRoles" -> roles;
                    case "getRoleById" -> roles.stream()
                            .filter(role -> role.getId().equals(args[0]))
                            .findFirst()
                            .orElse(null);
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Role role(String id, String name) {
        return (Role) Proxy.newProxyInstance(
                Role.class.getClassLoader(),
                new Class<?>[] {Role.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "isPublicRole", "isManaged" -> false;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Member member(String id, List<Role> roles) {
        return (Member) Proxy.newProxyInstance(
                Member.class.getClassLoader(),
                new Class<?>[] {Member.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getRoles" -> roles;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        throw new IllegalArgumentException("Unsupported primitive type: " + type);
    }
}

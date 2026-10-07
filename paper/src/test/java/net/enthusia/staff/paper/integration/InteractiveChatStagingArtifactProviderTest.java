package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class InteractiveChatStagingArtifactProviderTest {

    private static final Pattern ITEM = Pattern.compile("\\[item\\]");

    @Test
    void matcherUsesFirstUnescapedPlaceholder() {
        assertEquals(
                7,
                InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        ITEM,
                        "prefix [item] suffix"
                )
        );
    }

    @Test
    void matcherTreatsSingleBackslashAsEscapeButDoubleBackslashAsLiteralSlash() {
        assertEquals(
                -1,
                InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        ITEM,
                        "prefix \\[item] suffix"
                )
        );
        assertEquals(
                9,
                InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        ITEM,
                        "prefix \\\\[item] suffix"
                )
        );
    }

    @Test
    void matcherSkipsEscapedOccurrenceAndFindsLaterRealPlaceholder() {
        assertEquals(
                19,
                InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        ITEM,
                        "first \\[item] then [item]"
                )
        );
    }

    @Test
    void detachedRendererUuidIsStableAndCannotResolveAsTheLivePlayerIdentity() {
        UUID playerId = UUID.fromString("12345678-1234-5678-9234-567812345678");

        UUID first = InteractiveChatStagingArtifactProvider.detachedSnapshotUuid(playerId);
        UUID second = InteractiveChatStagingArtifactProvider.detachedSnapshotUuid(playerId);

        assertEquals(first, second);
        assertNotEquals(playerId, first);
    }

    @Test
    void placeholderPermissionsMatchInteractiveChatContracts() {
        assertEquals(
                "interactivechat.module.item",
                InteractiveChatStagingArtifactProvider.Kind.ITEM.permission()
        );
        assertEquals(
                "interactivechat.module.inventory",
                InteractiveChatStagingArtifactProvider.Kind.INVENTORY.permission()
        );
        assertEquals(
                "interactivechat.module.enderchest",
                InteractiveChatStagingArtifactProvider.Kind.ENDER_CHEST.permission()
        );
    }

    @Test
    void renderJobRejectsNegativePositionsAndSnapshotCopiesJobs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InteractiveChatStagingArtifactProvider.RenderJob(
                        InteractiveChatStagingArtifactProvider.Kind.ITEM,
                        -1,
                        null,
                        null
                )
        );

        var job = new InteractiveChatStagingArtifactProvider.RenderJob(
                InteractiveChatStagingArtifactProvider.Kind.ITEM,
                3,
                null,
                null
        );
        var source = new java.util.ArrayList<>(List.of(job));
        var snapshot = new InteractiveChatStagingArtifactProvider.Snapshot(
                new Object(),
                source
        );
        source.clear();
        assertEquals(1, snapshot.jobs().size());
    }
}

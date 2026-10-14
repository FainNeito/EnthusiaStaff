package net.enthusia.staff.api.chat;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Optional Bukkit service implemented by a rich-chat companion plugin.
 *
 * <p>The service is intentionally provider-neutral. A provider must snapshot/render asynchronously
 * as needed and complete promptly. Failure or an empty result simply leaves the styled text path
 * intact.</p>
 */
@FunctionalInterface
public interface RichChatArtifactProvider {
    CompletionStage<List<RichChatArtifact>> render(RichChatArtifactRequest request);
}

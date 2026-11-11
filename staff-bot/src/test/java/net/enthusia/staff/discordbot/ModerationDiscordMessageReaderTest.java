package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

class ModerationDiscordMessageReaderTest {
    private static final String SEARCH_NEEDLE = "needle";
    @Test
    void messageReadsRequireActorAndBotChannelAccess() {
        assertFalse(ModerationDiscordMessageReader.hasReadPermissions(false, false, true, true));
        assertFalse(ModerationDiscordMessageReader.hasReadPermissions(true, false, true, true));
        assertFalse(ModerationDiscordMessageReader.hasReadPermissions(true, true, false, true));
        assertFalse(ModerationDiscordMessageReader.hasReadPermissions(true, true, true, false));
        assertTrue(ModerationDiscordMessageReader.hasReadPermissions(true, true, true, true));
    }

    @Test
    void invocationChannelIsUsedForChannelBrowseAndChannelBoundUserLaunches() {
        ModerationReadTarget browse = ModerationReadTarget.parse("channel:1541286004298752091");
        ModerationReadTarget contextual = ModerationReadTarget.parse("discord-channel:1541286004298752091:222");
        ModerationReadTarget legacy = ModerationReadTarget.parse("discord:222");
        ModerationReadTarget exactMessage = ModerationReadTarget.parse("message:1541286004298752091:333:222");

        assertEquals(OptionalLong.of(1541286004298752091L), ModerationDiscordMessageReader.initialChannel(browse));
        assertEquals(OptionalLong.of(1541286004298752091L), ModerationDiscordMessageReader.initialChannel(contextual));
        assertEquals(OptionalLong.empty(), ModerationDiscordMessageReader.initialChannel(legacy));
        assertEquals(OptionalLong.empty(), ModerationDiscordMessageReader.initialChannel(exactMessage));
    }

    @Test
    void textAuthorAndDateFiltersUseServerSideHistorySearch() {
        assertTrue(ModerationDiscordMessageReader.searchRequested(query(Optional.of("older phrase"), Optional.empty(), Optional.empty())));
        assertTrue(ModerationDiscordMessageReader.searchRequested(query(Optional.empty(), Optional.of("222"), Optional.empty())));
        assertTrue(ModerationDiscordMessageReader.searchRequested(new ModerationReadApiModel.MessageQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("alice"), Optional.empty(), 50)));
        assertTrue(ModerationDiscordMessageReader.searchRequested(query(Optional.empty(), Optional.empty(), Optional.of("2026-09-01"))));
        assertFalse(ModerationDiscordMessageReader.searchRequested(query(Optional.of("   "), Optional.empty(), Optional.empty())));
        assertFalse(ModerationDiscordMessageReader.searchRequested(query(Optional.empty(), Optional.empty(), Optional.empty())));
    }

    private static ModerationReadApiModel.MessageQuery query(
            Optional<String> text,
            Optional<String> author,
            Optional<String> date
    ) {
        return new ModerationReadApiModel.MessageQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), text, author, date, 50);
    }

    @Test
    void authorNameFiltersMatchHistoricalDiscordNames() {
        ModerationReadApiModel.MessageQuery query = new ModerationReadApiModel.MessageQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("alice"), Optional.empty(), 50);
        List<Message> source = List.of(
                message("matching", 222L, "Alice Example", SEARCH_NEEDLE, "2026-09-01T10:00:00Z"),
                message("other", 333L, "Bob Example", SEARCH_NEEDLE, "2026-09-01T09:00:00Z"));

        List<Message> result = ModerationDiscordMessageReader.filterAndLimit(source, query, 50);

        assertEquals(List.of("matching"), result.stream().map(Message::getId).toList());
    }

    @Test
    void recentQueriesApplyAuthorTextDateAndLimitBeforeMapping() {
        ModerationReadApiModel.MessageQuery query = new ModerationReadApiModel.MessageQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(SEARCH_NEEDLE), Optional.of("222"),
                Optional.of("2026-09-01"), 1);
        List<Message> source = List.of(
                message("first", 222L, "needle one", "2026-09-01T10:00:00Z"),
                message("second", 222L, "needle two", "2026-09-01T09:00:00Z"),
                message("wrong-author", 333L, SEARCH_NEEDLE, "2026-09-01T08:00:00Z"),
                message("wrong-text", 222L, "other", "2026-09-01T07:00:00Z"),
                message("wrong-date", 222L, SEARCH_NEEDLE, "2026-08-31T23:00:00Z"));

        List<Message> result = ModerationDiscordMessageReader.filterAndLimit(source, query, 1);

        assertEquals(1, result.size());
        assertEquals("first", result.getFirst().getId());
    }

    private static Message message(String id, long authorId, String content, String createdAt) {
        return message(id, authorId, "Tester", content, createdAt);
    }

    private static Message message(String id, long authorId, String authorName, String content, String createdAt) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        User author = (User) Proxy.newProxyInstance(
                loader,
                new Class<?>[] {User.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getIdLong" -> authorId;
                    case "getName" -> authorName;
                    case "getGlobalName" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Message) Proxy.newProxyInstance(
                loader,
                new Class<?>[] {Message.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getAuthor" -> author;
                    case "getContentRaw" -> content;
                    case "getTimeCreated" -> OffsetDateTime.parse(createdAt);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}

package net.enthusia.staff.discordbot;

/** Optional StaffBot chat transport lifecycle kept independent from core Discord readiness. */
interface StaffBotChatLifecycle extends AutoCloseable {
    void start();

    void resume();

    void pause();

    @Override
    void close();
}

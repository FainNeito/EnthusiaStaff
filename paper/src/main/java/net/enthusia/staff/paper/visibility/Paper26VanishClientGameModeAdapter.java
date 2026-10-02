package net.enthusia.staff.paper.visibility;

import io.papermc.paper.ServerBuildInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

final class Paper26VanishClientGameModeAdapter implements VanishClientGameModeAdapter {
    static final String SUPPORTED_MINECRAFT_VERSION = "26.2";
    static final int SUPPORTED_PAPER_BUILD = 129;
    private final Logger logger;
    private final Method getHandle;
    private final Field connection;
    private final Constructor<?> gameEventPacket;
    private final Object changeGameMode;
    private final Method sendPacket;
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final String unavailableReason;

    private Paper26VanishClientGameModeAdapter(Logger logger, ReflectionAccess access) {
        this.logger = logger;
        this.getHandle = access.getHandle();
        this.connection = access.connection();
        this.gameEventPacket = access.gameEventPacket();
        this.changeGameMode = access.changeGameMode();
        this.sendPacket = access.sendPacket();
        this.unavailableReason = "";
    }

    private Paper26VanishClientGameModeAdapter(Logger logger, String unavailableReason) {
        this.logger = logger;
        this.getHandle = null;
        this.connection = null;
        this.gameEventPacket = null;
        this.changeGameMode = null;
        this.sendPacket = null;
        this.unavailableReason = unavailableReason;
        this.healthy.set(false);
    }

    static VanishClientGameModeAdapter install(Logger logger) {
        ServerBuildInfo info = ServerBuildInfo.buildInfo();
        String incompatibility = incompatibility(info);
        if (incompatibility != null) {
            logger.severe("Vanish no-clip client adapter disabled: " + incompatibility);
            return new Paper26VanishClientGameModeAdapter(logger, incompatibility);
        }
        try {
            return new Paper26VanishClientGameModeAdapter(logger, reflectionAccess());
        } catch (ReflectiveOperationException | LinkageError exception) {
            String reason = "Paper 26.2 build 129 internals do not match the pinned no-clip adapter";
            logger.log(Level.SEVERE, reason, exception);
            return new Paper26VanishClientGameModeAdapter(logger, reason);
        }
    }

    static boolean supportsRuntime(boolean paperBrand, String minecraftVersion, OptionalInt buildNumber) {
        return paperBrand
                && SUPPORTED_MINECRAFT_VERSION.equals(minecraftVersion)
                && buildNumber.isPresent()
                && buildNumber.getAsInt() == SUPPORTED_PAPER_BUILD;
    }

    private static String incompatibility(ServerBuildInfo info) {
        boolean paperBrand = ServerBuildInfo.BRAND_PAPER_ID.equals(info.brandId());
        if (supportsRuntime(paperBrand, info.minecraftVersionId(), info.buildNumber())) {
            return null;
        }
        return "requires exact Paper " + SUPPORTED_MINECRAFT_VERSION
                + " build " + SUPPORTED_PAPER_BUILD + ", found "
                + info.asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE);
    }

    private static ReflectionAccess reflectionAccess() throws ReflectiveOperationException {
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        Class<?> listener = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> gameEvent = Class.forName("net.minecraft.network.protocol.game.ClientboundGameEventPacket");
        Field change = gameEvent.getField("CHANGE_GAME_MODE");
        Object changeValue = change.get(null);
        Constructor<?> constructor = gameEvent.getConstructor(change.getType(), float.class);
        return new ReflectionAccess(craftPlayer.getMethod("getHandle"), serverPlayer.getField("connection"),
                constructor, changeValue, listener.getMethod("send", packet));
    }

    @Override public boolean available() { return healthy.get(); }

    @Override
    public String unavailableReason() {
        if (available()) return "";
        return unavailableReason.isBlank() ? "client game-mode presentation failed at runtime" : unavailableReason;
    }

    @Override
    public boolean present(Player player, GameMode gameMode) {
        if (!available()) return false;
        try {
            Object handle = getHandle.invoke(player);
            Object listener = connection.get(handle);
            if (listener == null) return false;
            Object packet = gameEventPacket.newInstance(changeGameMode, gameModeId(gameMode));
            sendPacket.invoke(listener, packet);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableAfterFailure(exception);
            return false;
        }
    }

    private void disableAfterFailure(Exception exception) {
        if (healthy.compareAndSet(true, false)) {
            logger.log(Level.SEVERE, "Vanish no-clip client presentation failed; adapter is now fail-closed", exception);
        }
    }

    private static float gameModeId(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> 0.0F;
            case CREATIVE -> 1.0F;
            case ADVENTURE -> 2.0F;
            case SPECTATOR -> 3.0F;
        };
    }

    private record ReflectionAccess(Method getHandle, Field connection, Constructor<?> gameEventPacket,
                                    Object changeGameMode, Method sendPacket) {
    }
}

package dev.rew1nd.sableschematicapi.api.honeywelder;

import dev.rew1nd.sableschematicapi.survival.honeywelder.HoneyWelderServerSessions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server lifecycle bridge used by downstream consumers. */
public final class HoneyWelderOperations {
    private HoneyWelderOperations() {
    }

    public static boolean isActive(final ServerPlayer player, final ResourceLocation consumer) {
        return HoneyWelderServerSessions.isActive(player, consumer);
    }

    public static boolean isActive(final ServerPlayer player,
                                   final ResourceLocation consumer,
                                   final ResourceLocation action) {
        return HoneyWelderServerSessions.isActive(player, consumer, action);
    }

    public static boolean finish(final ServerPlayer player,
                                 final ResourceLocation consumer,
                                 final int fuelUsed) {
        return HoneyWelderServerSessions.finish(player, consumer, fuelUsed);
    }

    public static void cancel(final ServerPlayer player,
                              final HoneyWelderCancelReason reason) {
        HoneyWelderServerSessions.cancel(player, reason);
    }
}

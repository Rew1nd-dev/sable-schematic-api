package dev.rew1nd.sableschematicapi.api.honeywelder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Common/server half of a honey-welder implementation.
 *
 * <p>Implementations own their operation semantics. Sable owns the selected
 * consumer, one-active-session rule and final fuel mutation.</p>
 */
public interface HoneyWelderConsumer {
    ResourceLocation id();

    Component label();

    /**
     * Whether Sable should apply its zero-fuel gate before invoking
     * {@link #start(ServerPlayer, ResourceLocation, int, boolean)}.
     */
    default boolean requiresFuel(final ResourceLocation action) {
        return true;
    }

    HoneyWelderStartResult start(ServerPlayer player,
                                 ResourceLocation action,
                                 int currentFuel,
                                 boolean creative);

    default void cancel(final ServerPlayer player, final HoneyWelderCancelReason reason) {
    }
}

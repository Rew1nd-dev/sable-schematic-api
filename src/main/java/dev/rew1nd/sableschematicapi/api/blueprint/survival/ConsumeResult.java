package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.network.chat.Component;

/**
 * Result of consuming a material quote.
 */
public record ConsumeResult(boolean successful, Component message) {
    public static ConsumeResult success() {
        return new ConsumeResult(true, Component.empty());
    }

    public static ConsumeResult failure(final Component message) {
        return new ConsumeResult(false, message);
    }
}

package dev.rew1nd.sableschematicapi.sublevel;

import net.minecraft.network.chat.Component;

public record SubLevelOperationResult(boolean success, String message, int affectedSubLevels) {
    public static SubLevelOperationResult success(final String message, final int affectedSubLevels) {
        return new SubLevelOperationResult(true, message, affectedSubLevels);
    }

    public static SubLevelOperationResult failure(final String message) {
        return new SubLevelOperationResult(false, message, 0);
    }

    public Component asComponent() {
        return Component.literal(this.message);
    }
}

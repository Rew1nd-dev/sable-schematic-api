package dev.rew1nd.sableschematicapi.blueprint.tool;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public record BlueprintToolResult(boolean success,
                                  String message,
                                  int affectedSubLevels,
                                  @Nullable BlueprintToolSummary summary) {
    public static BlueprintToolResult success(final String message,
                                              final int affectedSubLevels,
                                              @Nullable final BlueprintToolSummary summary) {
        return new BlueprintToolResult(true, message, affectedSubLevels, summary);
    }

    public static BlueprintToolResult failure(final String message) {
        return new BlueprintToolResult(false, message, 0, null);
    }

    public Component asComponent() {
        return Component.literal(this.message);
    }
}

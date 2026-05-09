package dev.rew1nd.sableschematicapi.blueprint.tool;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticReport;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public record BlueprintToolResult(boolean success,
                                  String message,
                                  int affectedSubLevels,
                                  @Nullable BlueprintToolSummary summary,
                                  BlueprintDiagnosticReport diagnostics) {
    public BlueprintToolResult {
        diagnostics = diagnostics == null ? BlueprintDiagnosticReport.empty() : diagnostics;
    }

    public static BlueprintToolResult success(final String message,
                                              final int affectedSubLevels,
                                              @Nullable final BlueprintToolSummary summary) {
        return success(message, affectedSubLevels, summary, BlueprintDiagnosticReport.empty());
    }

    public static BlueprintToolResult success(final String message,
                                              final int affectedSubLevels,
                                              @Nullable final BlueprintToolSummary summary,
                                              final BlueprintDiagnosticReport diagnostics) {
        return new BlueprintToolResult(true, message, affectedSubLevels, summary, diagnostics);
    }

    public static BlueprintToolResult failure(final String message) {
        return failure(message, BlueprintDiagnosticReport.empty());
    }

    public static BlueprintToolResult failure(final String message, final BlueprintDiagnosticReport diagnostics) {
        return new BlueprintToolResult(false, message, 0, null, diagnostics);
    }

    public Component asComponent() {
        return Component.literal(this.message);
    }
}

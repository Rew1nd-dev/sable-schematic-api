package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * One recoverable issue encountered while decoding or placing a blueprint.
 *
 * @param severity      issue severity
 * @param stage         lifecycle stage that produced the issue
 * @param category      issue category
 * @param subLevelId    blueprint-local sub-level id, when known
 * @param localPos      blueprint-local block position, when known
 * @param storagePos    placed storage position, when known
 * @param subject       optional block/entity/palette/event identifier
 * @param playerMessage short message safe to show to players
 * @param debugMessage  detailed message for server logs
 * @param cause         original exception, when available
 */
public record BlueprintDiagnostic(BlueprintDiagnosticSeverity severity,
                                  BlueprintDiagnosticStage stage,
                                  BlueprintDiagnosticCategory category,
                                  @Nullable Integer subLevelId,
                                  @Nullable BlockPos localPos,
                                  @Nullable BlockPos storagePos,
                                  @Nullable String subject,
                                  String playerMessage,
                                  String debugMessage,
                                  @Nullable Throwable cause) {
    public BlueprintDiagnostic {
        if (playerMessage == null || playerMessage.isBlank()) {
            playerMessage = "Blueprint issue.";
        }
        if (debugMessage == null || debugMessage.isBlank()) {
            debugMessage = playerMessage;
        }
        localPos = localPos == null ? null : localPos.immutable();
        storagePos = storagePos == null ? null : storagePos.immutable();
    }

    public String locationDescription() {
        final StringBuilder builder = new StringBuilder();
        if (this.subLevelId != null) {
            builder.append("sub-level ").append(this.subLevelId);
        }
        if (this.localPos != null) {
            appendSeparator(builder);
            builder.append("local ").append(formatPos(this.localPos));
        }
        if (this.storagePos != null) {
            appendSeparator(builder);
            builder.append("storage ").append(formatPos(this.storagePos));
        }
        if (this.subject != null && !this.subject.isBlank()) {
            appendSeparator(builder);
            builder.append(this.subject);
        }
        return builder.toString();
    }

    public String describeForPlayer() {
        final String location = this.locationDescription();
        return location.isBlank() ? this.playerMessage : this.playerMessage + " (" + location + ")";
    }

    public String describeForLog() {
        final String location = this.locationDescription();
        return location.isBlank()
                ? this.debugMessage
                : this.debugMessage + " [" + location + "]";
    }

    private static void appendSeparator(final StringBuilder builder) {
        if (!builder.isEmpty()) {
            builder.append(", ");
        }
    }

    private static String formatPos(final BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}

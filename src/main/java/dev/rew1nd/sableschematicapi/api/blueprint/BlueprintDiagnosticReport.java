package dev.rew1nd.sableschematicapi.api.blueprint;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated recoverable diagnostics from blueprint decode and placement.
 */
public final class BlueprintDiagnosticReport {
    private static final int DEFAULT_DETAIL_LIMIT = 5;
    private static final int LOG_DETAIL_LIMIT = 50;
    private static final BlueprintDiagnosticReport EMPTY = new BlueprintDiagnosticReport(List.of());

    private final List<BlueprintDiagnostic> diagnostics;
    private final Map<BlueprintDiagnosticCategory, Integer> counts;

    private BlueprintDiagnosticReport(final List<BlueprintDiagnostic> diagnostics) {
        this.diagnostics = List.copyOf(diagnostics);
        this.counts = countByCategory(this.diagnostics);
    }

    public static BlueprintDiagnosticReport empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return this.diagnostics.isEmpty();
    }

    public int size() {
        return this.diagnostics.size();
    }

    public List<BlueprintDiagnostic> diagnostics() {
        return this.diagnostics;
    }

    public int count(final BlueprintDiagnosticCategory category) {
        return this.counts.getOrDefault(category, 0);
    }

    public BlueprintDiagnosticReport merge(final BlueprintDiagnosticReport other) {
        if (this.isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }

        final List<BlueprintDiagnostic> merged = new ObjectArrayList<>(this.diagnostics.size() + other.diagnostics.size());
        merged.addAll(this.diagnostics);
        merged.addAll(other.diagnostics);
        return new BlueprintDiagnosticReport(merged);
    }

    public String summaryText() {
        if (this.isEmpty()) {
            return "No blueprint warnings.";
        }

        final int skippedBlocks = this.count(BlueprintDiagnosticCategory.SKIPPED_BLOCK)
                + this.count(BlueprintDiagnosticCategory.BLOCK_PLACE_FAILED)
                + this.count(BlueprintDiagnosticCategory.INVALID_PALETTE_REFERENCE);
        final int skippedBlockEntities = this.count(BlueprintDiagnosticCategory.BLOCK_ENTITY_LOAD_FAILED)
                + this.count(BlueprintDiagnosticCategory.INVALID_BLOCK_ENTITY_REFERENCE);
        final int skippedEntities = this.count(BlueprintDiagnosticCategory.ENTITY_SKIPPED)
                + this.count(BlueprintDiagnosticCategory.ENTITY_PLACE_FAILED);
        final int other = Math.max(0, this.size() - skippedBlocks - skippedBlockEntities - skippedEntities);

        final List<String> parts = new ObjectArrayList<>();
        if (skippedBlocks > 0) {
            parts.add(skippedBlocks + " block issue(s)");
        }
        if (skippedBlockEntities > 0) {
            parts.add(skippedBlockEntities + " block entity issue(s)");
        }
        if (skippedEntities > 0) {
            parts.add(skippedEntities + " entity issue(s)");
        }
        if (other > 0) {
            parts.add(other + " other issue(s)");
        }

        return "Blueprint completed with " + this.size() + " warning(s): " + String.join(", ", parts) + ".";
    }

    public Component summaryComponent() {
        return Component.literal(this.summaryText());
    }

    public List<Component> detailComponents() {
        return this.detailComponents(DEFAULT_DETAIL_LIMIT);
    }

    public List<Component> detailComponents(final int limit) {
        if (this.isEmpty() || limit <= 0) {
            return List.of();
        }

        final List<Component> components = new ObjectArrayList<>();
        final int shown = Math.min(limit, this.diagnostics.size());
        for (int i = 0; i < shown; i++) {
            components.add(Component.literal("- " + this.diagnostics.get(i).describeForPlayer()));
        }
        if (shown < this.diagnostics.size()) {
            components.add(Component.literal("- ... and " + (this.diagnostics.size() - shown) + " more issue(s); see server log."));
        }
        return components;
    }

    public void logSummary(final Logger logger, final String context) {
        if (this.isEmpty()) {
            return;
        }

        logger.warn("{}: {}", context, this.summaryText());
        final int shown = Math.min(LOG_DETAIL_LIMIT, this.diagnostics.size());
        for (int i = 0; i < shown; i++) {
            final BlueprintDiagnostic diagnostic = this.diagnostics.get(i);
            if (diagnostic.cause() == null) {
                logger.warn("{}: {}", context, diagnostic.describeForLog());
            } else {
                logger.warn("{}: {}", context, diagnostic.describeForLog(), diagnostic.cause());
            }
        }
        if (shown < this.diagnostics.size()) {
            logger.warn("{}: omitted {} additional blueprint issue(s).", context, this.diagnostics.size() - shown);
        }
    }

    private static Map<BlueprintDiagnosticCategory, Integer> countByCategory(final List<BlueprintDiagnostic> diagnostics) {
        final EnumMap<BlueprintDiagnosticCategory, Integer> counts = new EnumMap<>(BlueprintDiagnosticCategory.class);
        for (final BlueprintDiagnostic diagnostic : diagnostics) {
            counts.merge(diagnostic.category(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    public static final class Builder {
        private final List<BlueprintDiagnostic> diagnostics = new ObjectArrayList<>();

        public void add(final BlueprintDiagnostic diagnostic) {
            this.diagnostics.add(diagnostic);
        }

        public void addAll(final BlueprintDiagnosticReport report) {
            this.diagnostics.addAll(report.diagnostics());
        }

        public void warn(final BlueprintDiagnosticStage stage,
                         final BlueprintDiagnosticCategory category,
                         @Nullable final Integer subLevelId,
                         @Nullable final BlockPos localPos,
                         @Nullable final BlockPos storagePos,
                         @Nullable final String subject,
                         final String playerMessage,
                         final String debugMessage,
                         @Nullable final Throwable cause) {
            this.add(new BlueprintDiagnostic(
                    BlueprintDiagnosticSeverity.WARNING,
                    stage,
                    category,
                    subLevelId,
                    localPos,
                    storagePos,
                    subject,
                    playerMessage,
                    debugMessage,
                    cause
            ));
        }

        public BlueprintDiagnosticReport build() {
            return this.diagnostics.isEmpty() ? EMPTY : new BlueprintDiagnosticReport(this.diagnostics);
        }
    }
}

package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Material quote produced by survival blueprint rules.
 */
public record CostQuote(List<CostLine> lines, CostTiming timing, CostSeverity severity) {
    public CostQuote {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(severity, "severity");
    }

    public static CostQuote empty(final CostTiming timing) {
        return new CostQuote(List.of(), timing, CostSeverity.NORMAL);
    }

    public static CostQuote of(final CostLine line, final CostTiming timing) {
        return new CostQuote(List.of(line), timing, CostSeverity.NORMAL);
    }

    public boolean isEmpty() {
        return this.lines.isEmpty();
    }

    public CostQuote withTiming(final CostTiming timing) {
        if (this.timing == timing) {
            return this;
        }
        return new CostQuote(this.lines, timing, this.severity);
    }

    public CostQuote merge(final CostQuote other) {
        if (other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }

        final List<CostLine> merged = new ArrayList<>(this.lines);
        merged.addAll(other.lines);
        return new CostQuote(merged, this.timing, max(this.severity, other.severity));
    }

    /**
     * Returns a new quote with duplicate cost lines (same item + components) merged into
     * a single line with summed counts.
     */
    public CostQuote compact() {
        if (this.lines.size() <= 1) {
            return this;
        }

        final java.util.List<ItemStack> mergedStacks = new ArrayList<>();
        final java.util.List<CostLine> mergedLines = new ArrayList<>();

        for (final CostLine line : this.lines) {
            final ItemStack stack = line.stack();
            if (stack.isEmpty()) {
                continue;
            }

            int found = -1;
            for (int i = 0; i < mergedStacks.size(); i++) {
                if (ItemStack.isSameItemSameComponents(mergedStacks.get(i), stack)) {
                    found = i;
                    break;
                }
            }

            if (found >= 0) {
                final ItemStack existing = mergedStacks.get(found);
                existing.setCount(existing.getCount() + stack.getCount());
                // Replace the stale CostLine with one carrying the updated total count
                final CostLine merged = mergedLines.get(found);
                mergedLines.set(found, new CostLine(merged.ruleId(), existing.copy(), merged.description()));
            } else {
                mergedStacks.add(stack.copy());
                mergedLines.add(line);
            }
        }

        return new CostQuote(mergedLines, this.timing, this.severity);
    }

    private static CostSeverity max(final CostSeverity left, final CostSeverity right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}

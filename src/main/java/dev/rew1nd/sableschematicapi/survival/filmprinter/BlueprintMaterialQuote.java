package dev.rew1nd.sableschematicapi.survival.filmprinter;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable result of a complete film-printer preflight quote. */
public record BlueprintMaterialQuote(List<CostLine> lines,
                                     long workUnits,
                                     Optional<BlueprintQuoteIssue> issue) {
    public BlueprintMaterialQuote {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (workUnits < 0L) {
            throw new IllegalArgumentException("workUnits must be non-negative");
        }
        issue = issue == null ? Optional.empty() : issue;
    }

    public static BlueprintMaterialQuote admitted(final List<CostLine> lines, final long workUnits) {
        return new BlueprintMaterialQuote(lines, workUnits, Optional.empty());
    }

    public static BlueprintMaterialQuote rejected(final BlueprintQuoteIssue issue) {
        return new BlueprintMaterialQuote(List.of(), 0L, Optional.of(issue));
    }

    public boolean admitted() {
        return this.issue.isEmpty();
    }

    public int requiredCount(final ItemStack template) {
        for (final CostLine line : this.lines) {
            if (ItemStack.isSameItemSameComponents(template, line.stack())) {
                return line.stack().getCount();
            }
        }
        return 0;
    }
}

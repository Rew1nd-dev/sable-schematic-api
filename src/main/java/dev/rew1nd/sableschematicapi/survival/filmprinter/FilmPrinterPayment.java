package dev.rew1nd.sableschematicapi.survival.filmprinter;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.survival.BlueprintCannonMaterialSource;
import dev.rew1nd.sableschematicapi.survival.BlueprintCannonMaterialSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Atomic-at-the-task-boundary payment attempt backed by cannon material sources. */
public final class FilmPrinterPayment {
    private FilmPrinterPayment() {
    }

    public static boolean canAfford(final ServerLevel level,
                                    final BlockPos printerPos,
                                    final BlueprintMaterialQuote quote) {
        final List<BlueprintCannonMaterialSource> sources =
                BlueprintCannonMaterialSources.sources(level, printerPos);
        for (final CostLine line : quote.lines()) {
            if (!canAfford(line.stack(), sources)) {
                return false;
            }
        }
        return true;
    }

    public static Result consume(final ServerLevel level,
                                 final BlockPos printerPos,
                                 final BlueprintMaterialQuote quote) {
        final List<BlueprintCannonMaterialSource> sources =
                BlueprintCannonMaterialSources.sources(level, printerPos);
        for (final CostLine line : quote.lines()) {
            if (!canAfford(line.stack(), sources)) {
                return Result.notStarted();
            }
        }

        final List<ItemStack> removed = new ArrayList<>();
        for (final CostLine line : quote.lines()) {
            final ItemStack required = line.stack();
            final BlueprintCannonMaterialSource unlimited = firstUnlimited(required, sources);
            if (unlimited != null) {
                if (unlimited.consume(required, required.getCount()) < required.getCount()) {
                    return Result.partial(new PaymentReceipt(removed));
                }
                continue;
            }

            int remaining = required.getCount();
            for (final BlueprintCannonMaterialSource source : sources) {
                if (remaining <= 0) {
                    break;
                }
                final int consumed = Math.max(0, Math.min(remaining, source.consume(required, remaining)));
                if (consumed > 0) {
                    final ItemStack entry = required.copyWithCount(consumed);
                    PaymentReceipt.merge(removed, entry);
                    remaining -= consumed;
                }
            }
            if (remaining > 0) {
                return Result.partial(new PaymentReceipt(removed));
            }
        }
        return Result.success(new PaymentReceipt(removed));
    }

    private static boolean canAfford(final ItemStack required,
                                     final List<BlueprintCannonMaterialSource> sources) {
        if (required.isEmpty()) {
            return true;
        }
        int available = 0;
        for (final BlueprintCannonMaterialSource source : sources) {
            if (source.unlimited()) {
                return true;
            }
            final long sum = (long) available + Math.max(0, source.available(required));
            available = sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
            if (available >= required.getCount()) {
                return true;
            }
        }
        return false;
    }

    private static BlueprintCannonMaterialSource firstUnlimited(
            final ItemStack required,
            final List<BlueprintCannonMaterialSource> sources) {
        for (final BlueprintCannonMaterialSource source : sources) {
            if (source.unlimited() && source.available(required) >= required.getCount()) {
                return source;
            }
        }
        return null;
    }

    public record Result(Status status, PaymentReceipt receipt) {
        public static Result success(final PaymentReceipt receipt) {
            return new Result(Status.SUCCESS, receipt);
        }

        public static Result partial(final PaymentReceipt receipt) {
            return new Result(Status.PARTIAL_FAILURE, receipt);
        }

        public static Result notStarted() {
            return new Result(Status.NOT_AFFORDABLE, PaymentReceipt.EMPTY);
        }
    }

    public enum Status {
        SUCCESS,
        NOT_AFFORDABLE,
        PARTIAL_FAILURE
    }
}

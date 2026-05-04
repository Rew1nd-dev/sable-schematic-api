package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostRule;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostRuleCacheMode;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostRulePredicate;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildBlockPayload;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostTiming;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadDecision;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadMode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Process-global registry for survival block material cost rules.
 */
public final class BlueprintBlockCostRules {
    public static final int DEFAULT_PRIORITY = 0;

    private static final Map<Block, RuleEntry> BLOCK_CACHE = new Object2ObjectOpenHashMap<>();
    private static final Map<BlockState, RuleEntry> STATE_CACHE = new Object2ObjectOpenHashMap<>();
    private static final BlueprintBlockCostRule DEFAULT_RULE = new DefaultBlockItemCostRule();
    private static volatile List<RuleEntry> rules = List.of();
    private static long nextOrder;

    private BlueprintBlockCostRules() {
    }

    public static void register(final Block block, final BlueprintBlockCostRule rule) {
        register(
                rule.id(),
                DEFAULT_PRIORITY,
                payload -> payload.state().is(block),
                rule,
                BlueprintBlockCostRuleCacheMode.BLOCK
        );
    }

    public static void register(final Block block,
                                final int priority,
                                final BlueprintBlockCostRule rule) {
        register(
                rule.id(),
                priority,
                payload -> payload.state().is(block),
                rule,
                BlueprintBlockCostRuleCacheMode.BLOCK
        );
    }

    public static void registerForState(final ResourceLocation id,
                                        final int priority,
                                        final Predicate<BlockState> predicate,
                                        final BlueprintBlockCostRule rule,
                                        final BlueprintBlockCostRuleCacheMode cacheMode) {
        register(
                id,
                priority,
                payload -> predicate.test(payload.state()),
                rule,
                cacheMode
        );
    }

    public static synchronized void register(final ResourceLocation id,
                                             final int priority,
                                             final BlueprintBlockCostRulePredicate predicate,
                                             final BlueprintBlockCostRule rule,
                                             final BlueprintBlockCostRuleCacheMode cacheMode) {
        final RuleEntry entry = new RuleEntry(
                Objects.requireNonNull(id, "id"),
                priority,
                nextOrder++,
                Objects.requireNonNull(predicate, "predicate"),
                Objects.requireNonNull(rule, "rule"),
                Objects.requireNonNull(cacheMode, "cacheMode")
        );
        final List<RuleEntry> updated = new ArrayList<>(rules);
        updated.add(entry);
        updated.sort(RuleEntry.ORDERING);
        rules = List.copyOf(updated);
        BLOCK_CACHE.clear();
        STATE_CACHE.clear();
    }

    public static CostQuote quotePlacement(final BlueprintBuildBlockPayload payload,
                                           final BlueprintBlockCostContext context) {
        final RuleEntry entry = selectRule(payload);
        if (entry == null) {
            return DEFAULT_RULE.quotePlacement(payload, context);
        }
        return entry.rule().quotePlacement(payload, context);
    }

    public static CostQuote quoteNbtLoad(final BlueprintBuildBlockPayload payload,
                                         final BlueprintBlockCostContext context) {
        final RuleEntry entry = selectRule(payload);
        if (entry == null) {
            return DEFAULT_RULE.quoteNbtLoad(payload, context);
        }
        return entry.rule().quoteNbtLoad(payload, context);
    }

    public static CostQuote quoteDefaultPlacement(final BlueprintBuildBlockPayload payload,
                                                  final BlueprintBlockCostContext context) {
        return DEFAULT_RULE.quotePlacement(payload, context);
    }

    /**
     * Queries the placement material cost for a raw {@link BlockState}
     * without requiring a full {@link BlueprintBuildBlockPayload}.
     *
     * <p>Used by post-process cost strategies (e.g. contraption block iteration)
     * that have access to block states but not the full blueprint payload.</p>
     */
    public static CostQuote quotePlacementForState(final BlockState state,
                                                   final BlueprintBlockCostContext context) {
        final var payload = new BlueprintBuildBlockPayload(
                new BlueprintBlockRef(-1, BlockPos.ZERO),
                null,
                null,
                state,
                null,
                new BlueprintNbtLoadDecision(BlueprintNbtLoadMode.NONE, null)
        );
        return quotePlacement(payload, context);
    }

    private static RuleEntry selectRule(final BlueprintBuildBlockPayload payload) {
        final BlockState state = payload.state();
        final RuleEntry stateCached = STATE_CACHE.get(state);
        if (stateCached != null && cachedEntryStillWins(payload, stateCached)) {
            return stateCached;
        }

        final RuleEntry blockCached = BLOCK_CACHE.get(state.getBlock());
        if (blockCached != null && cachedEntryStillWins(payload, blockCached)) {
            return blockCached;
        }

        for (final RuleEntry entry : rules) {
            if (entry.matches(payload)) {
                cache(payload, entry);
                return entry;
            }
        }

        return null;
    }

    private static boolean cachedEntryStillWins(final BlueprintBuildBlockPayload payload,
                                                final RuleEntry cached) {
        if (!cached.matches(payload)) {
            return false;
        }

        for (final RuleEntry entry : rules) {
            if (entry == cached) {
                return true;
            }
            if (entry.matches(payload)) {
                cache(payload, entry);
                return false;
            }
        }

        return false;
    }

    private static void cache(final BlueprintBuildBlockPayload payload, final RuleEntry entry) {
        switch (entry.cacheMode()) {
            case BLOCK -> BLOCK_CACHE.put(payload.state().getBlock(), entry);
            case STATE -> STATE_CACHE.put(payload.state(), entry);
            case NONE -> {
            }
        }
    }

    private static final class DefaultBlockItemCostRule implements BlueprintBlockCostRule {
        @Override
        public ResourceLocation id() {
            return SableSchematicApi.id("default_block_item");
        }

        @Override
        public CostQuote quotePlacement(final BlueprintBuildBlockPayload payload,
                                        final BlueprintBlockCostContext context) {
            final Item item = payload.state().getBlock().asItem();
            if (item == Items.AIR) {
                return CostQuote.empty(CostTiming.IMMEDIATE_BLOCK);
            }

            return CostQuote.of(
                    new CostLine(this.id(), new ItemStack(item), Component.translatable(item.getDescriptionId())),
                    CostTiming.IMMEDIATE_BLOCK
            );
        }

        @Override
        public CostQuote quoteNbtLoad(final BlueprintBuildBlockPayload payload,
                                      final BlueprintBlockCostContext context) {
            return CostQuote.empty(CostTiming.COMMIT);
        }
    }

    private record RuleEntry(ResourceLocation id,
                             int priority,
                             long order,
                             BlueprintBlockCostRulePredicate predicate,
                             BlueprintBlockCostRule rule,
                             BlueprintBlockCostRuleCacheMode cacheMode) {
        private static final Comparator<RuleEntry> ORDERING = Comparator
                .comparingInt(RuleEntry::priority)
                .reversed()
                .thenComparingLong(RuleEntry::order);

        private boolean matches(final BlueprintBuildBlockPayload payload) {
            return this.predicate.matches(payload);
        }
    }
}

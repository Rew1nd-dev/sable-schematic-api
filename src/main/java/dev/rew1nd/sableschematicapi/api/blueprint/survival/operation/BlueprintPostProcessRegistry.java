package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Process-global registry for typed post-process operations.
 *
 * <p>Each operation domain registers three components:
 * <ul>
 *   <li>A {@link BlueprintPostProcessOperationParser} keyed by sidecar id.</li>
 *   <li>A {@link BlueprintPostProcessMapper} keyed by operation type.</li>
 *   <li>A {@link BlueprintPostProcessCostStrategy} keyed by operation type (optional).</li>
 * </ul>
 */
public final class BlueprintPostProcessRegistry {
    private static final Map<ResourceLocation, BlueprintPostProcessOperationParser> PARSERS =
            new Object2ObjectOpenHashMap<>();
    private static final Map<ResourceLocation, BlueprintPostProcessMapper<?>> MAPPERS =
            new Object2ObjectOpenHashMap<>();
    private static final Map<ResourceLocation, BlueprintPostProcessCostStrategy<?>> COSTS =
            new Object2ObjectOpenHashMap<>();

    private BlueprintPostProcessRegistry() {
    }

    public static void registerParser(final ResourceLocation sidecarId, final BlueprintPostProcessOperationParser parser) {
        PARSERS.put(sidecarId, parser);
    }

    public static void registerMapper(final ResourceLocation type, final BlueprintPostProcessMapper<?> mapper) {
        MAPPERS.put(type, mapper);
    }

    public static void registerCost(final ResourceLocation type, final BlueprintPostProcessCostStrategy<?> cost) {
        COSTS.put(type, cost);
    }

    public static @Nullable BlueprintPostProcessOperationParser parser(final ResourceLocation sidecarId) {
        return PARSERS.get(sidecarId);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlueprintPostProcessOperation> @Nullable BlueprintPostProcessMapper<T> mapper(
            final ResourceLocation type) {
        return (BlueprintPostProcessMapper<T>) MAPPERS.get(type);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlueprintPostProcessOperation> @Nullable BlueprintPostProcessCostStrategy<T> cost(
            final ResourceLocation type) {
        return (BlueprintPostProcessCostStrategy<T>) COSTS.get(type);
    }

    /**
     * Returns all registered sidecar ids, for iterating over in the commit pipeline.
     */
    public static Set<ResourceLocation> registeredSidecarIds() {
        return Set.copyOf(PARSERS.keySet());
    }
}

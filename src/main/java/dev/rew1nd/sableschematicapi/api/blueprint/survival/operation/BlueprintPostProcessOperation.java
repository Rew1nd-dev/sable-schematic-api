package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import net.minecraft.resources.ResourceLocation;

/**
 * A single post-process task that must be executed during the commit phase
 * of survival blueprint placement.
 *
 * <p>Examples: reconnect Super Glue regions, place contraption entities,
 * restore rope strands, re-link swivel bearings.</p>
 */
public interface BlueprintPostProcessOperation {
    /**
     * Stable type identifier used to look up the mapper and cost strategy.
     */
    ResourceLocation type();

    /**
     * Stable key that uniquely identifies this operation within one build session.
     * Used for progress snapshots so a restarted build can skip already-applied operations.
     */
    String stableKey();
}

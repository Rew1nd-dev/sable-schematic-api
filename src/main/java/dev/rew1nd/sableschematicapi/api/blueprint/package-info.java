/**
 * Public extension API for Sable blueprint export and placement.
 *
 * <h2>Coordinate spaces</h2>
 * <ul>
 *     <li><strong>Source storage position</strong>: the block or entity position in the original Sable sub-level storage.</li>
 *     <li><strong>Blueprint-local position</strong>: a stable position saved in the blueprint, relative to the saved sub-level payload.</li>
 *     <li><strong>Placed storage position</strong>: the new storage position allocated when the blueprint is loaded.</li>
 * </ul>
 *
 * <p>Compatibility code should store long-lived references as {@link BlueprintBlockRef}
 * or {@link BlueprintSubLevelRef}, then remap them through {@link BlueprintPlaceSession}
 * during placement. Avoid saving raw world UUIDs or storage positions unless the target is
 * intentionally outside the copied blueprint.</p>
 *
 * <h2>Extension model</h2>
 * <ul>
 *     <li>{@link SableBlueprintBlockMapper} handles per-block or per-block-entity NBT.</li>
 *     <li>{@link SableBlueprintEntityMapper} handles per-entity NBT and skip rules.</li>
 *     <li>{@link SableBlueprintEvent} handles global sidecar data for multi-block, multi-entity, or manager-owned state.</li>
 * </ul>
 *
 * <p>This package is intentionally free of UI, item, client-only, and LDLib2 dependencies so
 * it can remain a small server-safe API surface.</p>
 */
package dev.rew1nd.sableschematicapi.api.blueprint;


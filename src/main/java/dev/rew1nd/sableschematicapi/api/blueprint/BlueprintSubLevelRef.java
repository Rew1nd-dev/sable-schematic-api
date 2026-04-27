package dev.rew1nd.sableschematicapi.api.blueprint;

import java.util.UUID;

/**
 * Stable reference to a sub-level inside a Sable blueprint.
 *
 * @param subLevelId blueprint-local sub-level id
 * @param sourceUuid source sub-level UUID at save time
 */
public record BlueprintSubLevelRef(int subLevelId, UUID sourceUuid) {
}

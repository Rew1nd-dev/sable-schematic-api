package dev.rew1nd.sableschematicapi.api.blueprint;

import java.util.UUID;

/**
 * Stable reference to a sub-level inside a Sable blueprint.
 *
 * <p>The source UUID is stored so placement can map it to the newly allocated
 * placed sub-level UUID. The blueprint-local id identifies the sub-level payload
 * inside the saved blueprint.</p>
 *
 * @param subLevelId blueprint-local sub-level id
 * @param sourceUuid source sub-level UUID at save time
 */
public record BlueprintSubLevelRef(int subLevelId, UUID sourceUuid) {
}

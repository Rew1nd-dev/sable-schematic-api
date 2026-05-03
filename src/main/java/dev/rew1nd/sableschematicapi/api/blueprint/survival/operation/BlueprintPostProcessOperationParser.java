package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Parses post-process operations from blueprint data.
 *
 * <p>Each parser is registered under a {@link #sidecarId()} that matches the key
 * used inside the blueprint's {@code global_extra_data}. The parser receives the
 * sidecar NBT, the placement session, and the full blueprint — it may use any
 * combination of these to produce its operation list.</p>
 */
public interface BlueprintPostProcessOperationParser {
    /**
     * The sidecar key this parser reads from.
     * May be a virtual id for parsers that do not store data in {@code global_extra_data}
     * (e.g. entity-based parsers that read from {@link SableBlueprint#subLevels()}).
     */
    ResourceLocation sidecarId();

    /**
     * Produces the list of operations for this parser's domain.
     *
     * @param session     placement session with sub-level and block mappings
     * @param sidecarData the NBT stored under {@link #sidecarId()} in global_extra_data,
     *                    or an empty tag if no data was saved
     * @param blueprint   the full blueprint being placed
     * @return list of operations (empty if nothing to do)
     */
    List<? extends BlueprintPostProcessOperation> parse(
            BlueprintPlaceSession session,
            CompoundTag sidecarData,
            SableBlueprint blueprint
    );
}

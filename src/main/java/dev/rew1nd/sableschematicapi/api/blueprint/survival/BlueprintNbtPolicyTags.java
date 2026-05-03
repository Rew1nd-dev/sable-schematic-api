package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags used by the survival blueprint NBT admission policy.
 */
public final class BlueprintNbtPolicyTags {
    public static final TagKey<Block> BUILD_DENIED = block("blueprint/build_denied");
    public static final TagKey<Block> NO_NBT_LOAD = block("blueprint/no_nbt_load");
    public static final TagKey<Block> SANITIZED_NBT_LOAD = block("blueprint/sanitized_nbt_load");
    public static final TagKey<Block> FULL_NBT_LOAD = block("blueprint/full_nbt_load");

    private BlueprintNbtPolicyTags() {
    }

    private static TagKey<Block> block(final String path) {
        return TagKey.create(Registries.BLOCK, SableSchematicApi.id(path));
    }
}

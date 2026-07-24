package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;

/**
 * Small blueprint summary suitable for item tooltips, UI, and build admission.
 */
public record BlueprintSummary(int subLevels, int blocks, int blockEntityTags, int entities) {
    public static final Codec<BlueprintSummary> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("sub_levels").forGetter(BlueprintSummary::subLevels),
            Codec.INT.fieldOf("blocks").forGetter(BlueprintSummary::blocks),
            Codec.INT.fieldOf("block_entity_tags").forGetter(BlueprintSummary::blockEntityTags),
            Codec.INT.fieldOf("entities").forGetter(BlueprintSummary::entities)
    ).apply(instance, BlueprintSummary::new));

    public static BlueprintSummary of(final SableBlueprint blueprint) {
        return new BlueprintSummary(
                blueprint.subLevels().size(),
                blueprint.blockCount(),
                blueprint.blockEntityCount(),
                blueprint.entityCount()
        );
    }
}

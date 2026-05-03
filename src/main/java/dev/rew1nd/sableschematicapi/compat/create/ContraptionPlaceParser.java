package dev.rew1nd.sableschematicapi.compat.create;

import com.simibubi.create.AllEntityTypes;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperationParser;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses contraption entities from blueprint entity data.
 *
 * <p>Uses a virtual sidecar id because contraption data is stored in
 * {@link SableBlueprint#subLevels()} entities, not in {@code global_extra_data}.</p>
 */
public final class ContraptionPlaceParser implements BlueprintPostProcessOperationParser {

    public static final ResourceLocation SIDECAR_ID =
            SableSchematicApi.id("create/contraption");

    private static final Set<EntityType<?>> CONTRAPTION_TYPES;

    static {
        final Set<EntityType<?>> set = new HashSet<>();
        set.add(AllEntityTypes.ORIENTED_CONTRAPTION.get());
        set.add(AllEntityTypes.CONTROLLED_CONTRAPTION.get());
        set.add(AllEntityTypes.GANTRY_CONTRAPTION.get());
        set.add(AllEntityTypes.CARRIAGE_CONTRAPTION.get());
        CONTRAPTION_TYPES = Collections.unmodifiableSet(set);
    }

    @Override
    public ResourceLocation sidecarId() {
        return SIDECAR_ID;
    }

    @Override
    public List<ContraptionPlaceOperation> parse(final BlueprintPlaceSession session,
                                                  final CompoundTag sidecarData,
                                                  final SableBlueprint blueprint) {
        final List<ContraptionPlaceOperation> operations = new ArrayList<>();

        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            int entityIndex = 0;
            for (final SableBlueprint.EntityData entityData : entry.entities()) {
                final EntityType<?> type = EntityType.byString(
                        entityData.tag().getString("id")).orElse(null);

                if (type != null && CONTRAPTION_TYPES.contains(type)) {
                    operations.add(new ContraptionPlaceOperation(
                            entry.id(),
                            entityIndex,
                            EntityType.getKey(type).toString(),
                            entityData.tag().copy()
                    ));
                }
                entityIndex++;
            }
        }

        return operations;
    }
}

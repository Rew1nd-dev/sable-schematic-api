package dev.rew1nd.sableschematicapi.compat.simulated;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintBlockMapper;
import dev.rew1nd.sableschematicapi.compat.BlueprintRefTags;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

final class SpringBlueprintMapper implements SableBlueprintBlockMapper {
    private static final String GOAL = "Goal";
    private static final String GOAL_SUB_LEVEL = "GoalSubLevel";

    private static final String BLUEPRINT_GOAL_REF = "sable_schematic_api:spring_goal_ref";
    private static final String BLUEPRINT_GOAL_HAS_SUB_LEVEL = "sable_schematic_api:spring_goal_has_sub_level";

    @Override
    public @Nullable CompoundTag save(final BlueprintBlockSaveContext context, @Nullable final CompoundTag defaultTag) {
        if (defaultTag == null || !(context.blockEntity() instanceof SpringBlockEntity)) {
            return defaultTag;
        }

        final CompoundTag tag = defaultTag.copy();
        clearBlueprintGoal(tag);

        if (!tag.contains(GOAL, Tag.TAG_LONG)) {
            clearNativeGoal(tag);
            return tag;
        }

        final BlockPos partnerPos = BlockPos.of(tag.getLong(GOAL));
        final Optional<BlueprintBlockRef> partnerRef = context.blockRef(partnerPos);
        if (partnerRef.isEmpty() || !(context.level().getBlockEntity(partnerPos) instanceof SpringBlockEntity)) {
            clearNativeGoal(tag);
            return tag;
        }

        if (tag.hasUUID(GOAL_SUB_LEVEL) && context.subLevelRef(tag.getUUID(GOAL_SUB_LEVEL)).isEmpty()) {
            clearNativeGoal(tag);
            return tag;
        }

        final boolean hasGoalSubLevel = tag.hasUUID(GOAL_SUB_LEVEL);
        clearNativeGoal(tag);
        tag.put(BLUEPRINT_GOAL_REF, BlueprintRefTags.write(partnerRef.get()));
        tag.putBoolean(BLUEPRINT_GOAL_HAS_SUB_LEVEL, hasGoalSubLevel);
        return tag;
    }

    @Override
    public void beforeLoadBlockEntity(final BlueprintBlockPlaceContext context, final CompoundTag tag) {
        final Optional<BlueprintBlockRef> partnerRef = BlueprintRefTags.read(tag, BLUEPRINT_GOAL_REF);
        final boolean hasGoalSubLevel = tag.getBoolean(BLUEPRINT_GOAL_HAS_SUB_LEVEL);

        clearBlueprintGoal(tag);
        clearNativeGoal(tag);

        if (partnerRef.isEmpty()) {
            return;
        }

        final @Nullable BlockPos partnerPos = context.mapBlock(partnerRef.get());
        if (partnerPos == null) {
            return;
        }

        tag.putLong(GOAL, partnerPos.asLong());
        if (hasGoalSubLevel) {
            final @Nullable ServerSubLevel partnerSubLevel = context.session().placedSubLevel(partnerRef.get().subLevelId());
            if (partnerSubLevel == null) {
                clearNativeGoal(tag);
                return;
            }

            tag.putUUID(GOAL_SUB_LEVEL, partnerSubLevel.getUniqueId());
        }
    }

    @Override
    public void afterLoadBlockEntity(final BlueprintBlockPlaceContext context, final BlockEntity blockEntity, final CompoundTag loadedTag) {
        if (!(blockEntity instanceof final SpringBlockEntity spring)) {
            return;
        }

        if (!loadedTag.contains(GOAL, Tag.TAG_LONG)) {
            spring.setPartnerPos(null, null);
            spring.setChanged();
            return;
        }

        final BlockPos partnerPos = BlockPos.of(loadedTag.getLong(GOAL));
        final @Nullable UUID partnerSubLevel = loadedTag.hasUUID(GOAL_SUB_LEVEL) ? loadedTag.getUUID(GOAL_SUB_LEVEL) : null;
        spring.setPartnerPos(partnerPos, partnerSubLevel);
        spring.setChanged();
    }

    private static void clearNativeGoal(final CompoundTag tag) {
        tag.remove(GOAL);
        tag.remove(GOAL_SUB_LEVEL);
    }

    private static void clearBlueprintGoal(final CompoundTag tag) {
        tag.remove(BLUEPRINT_GOAL_REF);
        tag.remove(BLUEPRINT_GOAL_HAS_SUB_LEVEL);
    }
}

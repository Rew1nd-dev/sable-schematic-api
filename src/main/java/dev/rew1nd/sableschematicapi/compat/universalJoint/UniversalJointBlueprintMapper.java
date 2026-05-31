package dev.rew1nd.sableschematicapi.compat.universalJoint;

import com.enxv.aerouniversaljoint.content.UniversalJointBlockEntity;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintBlockMapper;
import dev.rew1nd.sableschematicapi.compat.BlueprintRefTags;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class UniversalJointBlueprintMapper implements SableBlueprintBlockMapper {
    private static final String LINKED_POS = "LinkedPos";
    private static final String LINKED_SUB_LEVEL = "LinkedSubLevel";
    private static final String LINKED_REST_LENGTH = "LinkedRestLength";
    private static final String LINKED_VARIANT = "LinkedVariant";
    private static final String TRANSMISSION_AXIS_ALIGNED = "TransmissionAxisAligned";
    private static final String LINK_STRAIN_EFFECT = "LinkStrainEffect";
    private static final String LINK_STRAINED = "LinkStrained";

    private static final String BLUEPRINT_LINK_REF = "sable_schematic_api:universal_joint_link_ref";

    @Override
    public @Nullable CompoundTag save(final BlueprintBlockSaveContext context, @Nullable final CompoundTag defaultTag) {
        if (defaultTag == null || !(context.blockEntity() instanceof final UniversalJointBlockEntity joint)) {
            return defaultTag;
        }

        final CompoundTag tag = defaultTag.copy();
        clearBlueprintLink(tag);
        clearTransientLinkState(tag);

        final @Nullable BlockPos linkedPos = joint.getLinkedPos();
        if (linkedPos == null) {
            clearNativeLinkState(tag);
            return tag;
        }

        final Optional<BlueprintBlockRef> linkedRef = context.blockRef(linkedPos);
        if (linkedRef.isEmpty()) {
            clearNativeLinkState(tag);
            return tag;
        }

        final @Nullable UUID linkedSubLevelId = joint.getLinkedSubLevelId();
        if (linkedSubLevelId != null && context.subLevelRef(linkedSubLevelId).isEmpty()) {
            clearNativeLinkState(tag);
            return tag;
        }

        final BlockEntity linkedBlockEntity = context.level().getBlockEntity(linkedPos);
        if (!(linkedBlockEntity instanceof final UniversalJointBlockEntity linkedJoint)
                || !joint.references(linkedJoint)
                || !linkedJoint.references(joint)) {
            clearNativeLinkState(tag);
            return tag;
        }

        tag.put(BLUEPRINT_LINK_REF, BlueprintRefTags.write(linkedRef.get()));
        clearNativeLinkReference(tag);
        return tag;
    }

    @Override
    public void beforeLoadBlockEntity(final BlueprintBlockPlaceContext context, final CompoundTag tag) {
        final Optional<BlueprintBlockRef> linkedRef = BlueprintRefTags.read(tag, BLUEPRINT_LINK_REF);
        clearBlueprintLink(tag);
        clearNativeLinkReference(tag);
        clearTransientLinkState(tag);

        if (linkedRef.isEmpty()) {
            clearNativeLinkState(tag);
            return;
        }

        final @Nullable BlockPos linkedPos = context.mapBlock(linkedRef.get());
        final @Nullable ServerSubLevel linkedSubLevel = context.session().placedSubLevel(linkedRef.get().subLevelId());
        if (linkedPos == null || linkedSubLevel == null) {
            clearNativeLinkState(tag);
            return;
        }

        tag.put(LINKED_POS, NbtUtils.writeBlockPos(linkedPos));
        tag.putUUID(LINKED_SUB_LEVEL, linkedSubLevel.getUniqueId());
    }

    @Override
    public void afterLoadBlockEntity(final BlueprintBlockPlaceContext context,
                                     final BlockEntity blockEntity,
                                     final CompoundTag loadedTag) {
        if (!(blockEntity instanceof final UniversalJointBlockEntity joint)) {
            return;
        }

        final @Nullable BlockPos linkedPos = readLinkedPos(loadedTag);
        if (linkedPos == null) {
            return;
        }

        final @Nullable UUID linkedSubLevelId = loadedTag.hasUUID(LINKED_SUB_LEVEL)
                ? loadedTag.getUUID(LINKED_SUB_LEVEL)
                : null;
        final BlockEntity linkedBlockEntity = context.level().getBlockEntity(linkedPos);
        if (!(linkedBlockEntity instanceof final UniversalJointBlockEntity linkedJoint)
                || !references(joint, linkedPos, linkedSubLevelId)
                || !references(linkedJoint, context.storagePos(), context.placedSubLevelUuid())) {
            joint.detachLinkWithoutDrop();
            return;
        }

        joint.updateReferenceTo(linkedPos, linkedSubLevelId);
        linkedJoint.updateReferenceTo(context.storagePos(), context.placedSubLevelUuid());
    }

    private static boolean references(final UniversalJointBlockEntity joint,
                                      final BlockPos pos,
                                      @Nullable final UUID subLevelId) {
        return pos.equals(joint.getLinkedPos()) && Objects.equals(subLevelId, joint.getLinkedSubLevelId());
    }

    private static @Nullable BlockPos readLinkedPos(final CompoundTag tag) {
        if (!tag.contains(LINKED_POS, Tag.TAG_INT_ARRAY)) {
            return null;
        }

        return NbtUtils.readBlockPos(tag, LINKED_POS).orElse(null);
    }

    private static void clearNativeLinkReference(final CompoundTag tag) {
        tag.remove(LINKED_POS);
        tag.remove(LINKED_SUB_LEVEL);
    }

    private static void clearNativeLinkState(final CompoundTag tag) {
        clearNativeLinkReference(tag);
        tag.remove(LINKED_REST_LENGTH);
        tag.remove(LINKED_VARIANT);
        tag.remove(TRANSMISSION_AXIS_ALIGNED);
        clearTransientLinkState(tag);
    }

    private static void clearTransientLinkState(final CompoundTag tag) {
        tag.remove(LINK_STRAIN_EFFECT);
        tag.remove(LINK_STRAINED);
    }

    private static void clearBlueprintLink(final CompoundTag tag) {
        tag.remove(BLUEPRINT_LINK_REF);
    }
}

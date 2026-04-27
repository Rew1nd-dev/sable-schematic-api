package dev.rew1nd.sableschematicapi.compat.simulated;

import com.mojang.serialization.DataResult;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintBlockMapper;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlock;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RopeStrandBlueprintMapper implements SableBlueprintBlockMapper {
    private static final String OWN_STRAND = "OwnStrand";
    private static final String HAS_ROPE_ATTACHED = "HasRopeAttached";
    private static final String STRAND = "Strand";

    @Override
    public @Nullable CompoundTag save(final BlueprintBlockSaveContext context, @Nullable final CompoundTag defaultTag) {
        if (defaultTag == null || context.blockEntity() == null) {
            return defaultTag;
        }

        final CompoundTag tag = defaultTag.copy();
        clearRopeState(tag);
        return tag;
    }

    @Override
    public void beforeLoadBlockEntity(final BlueprintBlockPlaceContext context, final CompoundTag tag) {
        final BlueprintBlockRef selfRef = new BlueprintBlockRef(context.blueprintSubLevelId(), context.localPos());
        final Optional<CompoundTag> rope = RopeStrandBlueprintEvent.findRopeFor(context.session(), selfRef);
        if (rope.isEmpty()) {
            return;
        }

        final @Nullable PlacedRope placedRope = buildPlacedRope(context, rope.get());
        if (placedRope == null) {
            clearRopeState(tag);
            return;
        }

        final boolean owner = RopeStrandBlueprintEvent.isOwner(rope.get(), selfRef);
        tag.putBoolean(OWN_STRAND, owner);
        tag.putUUID(HAS_ROPE_ATTACHED, placedRope.uuid());
        if (owner) {
            final @Nullable CompoundTag strandTag = encodeStrand(placedRope.strand());
            if (strandTag == null) {
                clearRopeState(tag);
                return;
            }
            tag.put(STRAND, strandTag);
        } else {
            tag.remove(STRAND);
        }
    }

    @Override
    public void afterLoadBlockEntity(final BlueprintBlockPlaceContext context, final BlockEntity blockEntity, final CompoundTag loadedTag) {
        blockEntity.setChanged();
    }

    private static @Nullable PlacedRope buildPlacedRope(final BlueprintBlockPlaceContext context, final CompoundTag rope) {
        if (!rope.hasUUID(RopeStrandBlueprintEvent.SOURCE_ROPE_UUID)) {
            return null;
        }

        final UUID uuid = context.allocateMappedUuid(rope.getUUID(RopeStrandBlueprintEvent.SOURCE_ROPE_UUID));
        final List<RopeAttachment> attachments = buildAttachments(context, rope);
        if (attachments.size() < 2) {
            return null;
        }

        final List<Vector3d> points = buildPoints(context, rope, attachments);
        if (points.size() < 2) {
            return null;
        }

        final ServerRopeStrand strand = new ServerRopeStrand(uuid, points);
        strand.updateFirstSegmentExtension(rope.getDouble(RopeStrandBlueprintEvent.EXTENSION_GOAL));
        for (final RopeAttachment attachment : attachments) {
            strand.addAttachment(context.level(), attachment.point(), attachment);
        }

        return new PlacedRope(uuid, strand);
    }

    private static List<RopeAttachment> buildAttachments(final BlueprintBlockPlaceContext context, final CompoundTag rope) {
        final List<RopeAttachment> attachments = new ArrayList<>();
        final ListTag attachmentTags = rope.getList(RopeStrandBlueprintEvent.ATTACHMENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < attachmentTags.size(); i++) {
            final CompoundTag attachmentTag = attachmentTags.getCompound(i);
            final @Nullable RopeAttachmentPoint point = readAttachmentPoint(attachmentTag);
            final @Nullable BlockPos pos = BlueprintRefTags.read(attachmentTag, RopeStrandBlueprintEvent.ATTACHMENT_REF)
                    .map(context::mapBlock)
                    .orElse(null);
            final @Nullable UUID subLevelUuid = mapAttachmentSubLevel(context, attachmentTag);
            if (point == null || pos == null) {
                return List.of();
            }

            if (attachmentTag.hasUUID(RopeStrandBlueprintEvent.ATTACHMENT_SUB_LEVEL_UUID) && subLevelUuid == null) {
                return List.of();
            }

            attachments.add(new RopeAttachment(point, subLevelUuid, pos));
        }

        return attachments;
    }

    private static @Nullable RopeAttachmentPoint readAttachmentPoint(final CompoundTag tag) {
        try {
            return RopeAttachmentPoint.valueOf(tag.getString(RopeStrandBlueprintEvent.ATTACHMENT_POINT));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @Nullable UUID mapAttachmentSubLevel(final BlueprintBlockPlaceContext context, final CompoundTag tag) {
        if (!tag.hasUUID(RopeStrandBlueprintEvent.ATTACHMENT_SUB_LEVEL_UUID)) {
            return null;
        }

        return context.mapSubLevel(tag.getUUID(RopeStrandBlueprintEvent.ATTACHMENT_SUB_LEVEL_UUID));
    }

    private static List<Vector3d> buildPoints(final BlueprintBlockPlaceContext context,
                                              final CompoundTag rope,
                                              final List<RopeAttachment> attachments) {
        final Optional<Vector3d> sourceOwnerAnchor = BlueprintRefTags.readVector(rope, RopeStrandBlueprintEvent.SOURCE_OWNER_ANCHOR);
        final @Nullable BlockPos placedOwnerPos = BlueprintRefTags.read(rope, RopeStrandBlueprintEvent.OWNER_REF)
                .map(context::mapBlock)
                .orElse(null);
        if (sourceOwnerAnchor.isEmpty() || placedOwnerPos == null) {
            return straightLinePoints(context.level(), attachments);
        }

        final Vector3d placedOwnerAnchor = projectedAttachmentPoint(context.level(), placedOwnerPos);
        final Vector3d delta = new Vector3d(placedOwnerAnchor).sub(sourceOwnerAnchor.get());
        final ListTag pointTags = rope.getList(RopeStrandBlueprintEvent.POINTS, Tag.TAG_COMPOUND);
        final List<Vector3d> points = new ArrayList<>();
        for (int i = 0; i < pointTags.size(); i++) {
            points.add(BlueprintRefTags.readVector(pointTags.getCompound(i)).add(delta));
        }

        return points.size() >= 2 ? points : straightLinePoints(context.level(), attachments);
    }

    private static List<Vector3d> straightLinePoints(final ServerLevel level, final List<RopeAttachment> attachments) {
        RopeAttachment start = null;
        RopeAttachment end = null;
        for (final RopeAttachment attachment : attachments) {
            if (attachment.point() == RopeAttachmentPoint.START) {
                start = attachment;
            } else if (attachment.point() == RopeAttachmentPoint.END) {
                end = attachment;
            }
        }

        if (start == null || end == null) {
            return List.of();
        }

        return List.of(
                projectedAttachmentPoint(level, start.blockAttachment()),
                projectedAttachmentPoint(level, end.blockAttachment())
        );
    }

    private static Vector3d projectedAttachmentPoint(final ServerLevel level, final BlockPos pos) {
        final Vec3 projected = Sable.HELPER.projectOutOfSubLevel(level, attachmentPoint(level, pos));
        return new Vector3d(projected.x, projected.y, projected.z);
    }

    private static Vec3 attachmentPoint(final ServerLevel level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RopeConnectorBlock && state.hasProperty(RopeConnectorBlock.FACING)) {
            final Direction facing = state.getValue(RopeConnectorBlock.FACING);
            return pos.getCenter().add(
                    facing.getStepX() * -0.1875,
                    facing.getStepY() * -0.1875,
                    facing.getStepZ() * -0.1875
            );
        }

        return pos.getCenter();
    }

    private static @Nullable CompoundTag encodeStrand(final ServerRopeStrand strand) {
        final DataResult<Tag> encoded = ServerRopeStrand.CODEC.encodeStart(NbtOps.INSTANCE, strand);
        final Tag tag = encoded.result().orElse(null);
        return tag instanceof final CompoundTag compound ? compound : null;
    }

    private static void clearRopeState(final CompoundTag tag) {
        tag.remove(OWN_STRAND);
        tag.remove(HAS_ROPE_ATTACHED);
        tag.remove(STRAND);
    }

    private record PlacedRope(UUID uuid, ServerRopeStrand strand) {
    }
}

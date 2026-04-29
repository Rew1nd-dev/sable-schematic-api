package dev.rew1nd.sableschematicapi.compat.simulated;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.rew1nd.sableschematicapi.compat.BlueprintRefTags;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Optional;
import java.util.UUID;

final class RopeStrandBlueprintEvent implements SableBlueprintEvent {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SableSchematicApi.MODID, "simulated/rope_strand");
    static final String ROPES = "ropes";
    static final String OWNER_REF = "owner_ref";
    static final String SOURCE_ROPE_UUID = "source_rope_uuid";
    static final String SOURCE_OWNER_ANCHOR = "source_owner_anchor";
    static final String POINTS = "points";
    static final String EXTENSION_GOAL = "extension_goal";
    static final String ATTACHMENTS = "attachments";
    static final String ATTACHMENT_POINT = "point";
    static final String ATTACHMENT_REF = "block_ref";
    static final String ATTACHMENT_SUB_LEVEL_UUID = "sub_level_uuid";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onSaveAfterBlocks(final BlueprintSaveSession session, final CompoundTag data) {
        final ListTag ropes = new ListTag();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (final SubLevelSaveFrame frame : session.frames()) {
            final BoundingBox3i bounds = frame.storageBounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        mutablePos.set(x, y, z);
                        final BlockEntity blockEntity = session.level().getBlockEntity(mutablePos);
                        if (blockEntity instanceof final RopeStrandHolderBlockEntity holder) {
                            saveOwnedRope(session, blockEntity, holder).ifPresent(ropes::add);
                        }
                    }
                }
            }
        }

        if (!ropes.isEmpty()) {
            data.put(ROPES, ropes);
        }
    }

    @Override
    public void onPlaceAfterBlockEntities(final BlueprintPlaceSession session, final CompoundTag data) {
        final ListTag ropes = data.getList(ROPES, Tag.TAG_COMPOUND);
        for (int i = 0; i < ropes.size(); i++) {
            registerPlacedRope(session, ropes.getCompound(i));
        }
    }

    static Optional<CompoundTag> findRopeFor(final BlueprintPlaceSession session, final BlueprintBlockRef ref) {
        final CompoundTag data = session.globalExtraData().getCompound(ID.toString());
        final ListTag ropes = data.getList(ROPES, Tag.TAG_COMPOUND);
        for (int i = 0; i < ropes.size(); i++) {
            final CompoundTag rope = ropes.getCompound(i);
            if (isOwner(rope, ref) || hasAttachment(rope, ref)) {
                return Optional.of(rope);
            }
        }

        return Optional.empty();
    }

    static boolean isOwner(final CompoundTag rope, final BlueprintBlockRef ref) {
        return BlueprintRefTags.read(rope, OWNER_REF)
                .filter(ref::equals)
                .isPresent();
    }

    private static boolean hasAttachment(final CompoundTag rope, final BlueprintBlockRef ref) {
        final ListTag attachments = rope.getList(ATTACHMENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < attachments.size(); i++) {
            final CompoundTag attachment = attachments.getCompound(i);
            if (BlueprintRefTags.read(attachment, ATTACHMENT_REF).filter(ref::equals).isPresent()) {
                return true;
            }
        }

        return false;
    }

    private static Optional<CompoundTag> saveOwnedRope(final BlueprintSaveSession session,
                                                       final BlockEntity ownerBlockEntity,
                                                       final RopeStrandHolderBlockEntity holder) {
        final RopeStrandHolderBehavior behavior = holder.getBehavior();
        if (behavior == null || !behavior.ownsRope()) {
            return Optional.empty();
        }

        final ServerRopeStrand strand = behavior.getOwnedStrand();
        if (strand == null) {
            return Optional.empty();
        }

        final Optional<BlueprintBlockRef> ownerRef = session.blockRef(ownerBlockEntity.getBlockPos());
        if (ownerRef.isEmpty()) {
            return Optional.empty();
        }

        final ListTag attachments = new ListTag();
        boolean ownerHasAttachment = false;
        for (final RopeAttachment attachment : strand.getAttachments()) {
            final Optional<CompoundTag> attachmentTag = saveAttachment(session, attachment);
            if (attachmentTag.isEmpty()) {
                return Optional.empty();
            }

            final CompoundTag tag = attachmentTag.get();
            ownerHasAttachment |= BlueprintRefTags.read(tag, ATTACHMENT_REF)
                    .filter(ownerRef.get()::equals)
                    .isPresent();
            attachments.add(tag);
        }

        if (!ownerHasAttachment || attachments.size() < 2) {
            return Optional.empty();
        }

        final ListTag points = new ListTag();
        for (final Vector3dc point : strand.getPoints()) {
            points.add(BlueprintRefTags.writeVector(point));
        }
        if (points.size() < 2) {
            return Optional.empty();
        }

        final @Nullable Vec3 ownerAnchor = behavior.getAttachmentPoint();
        if (ownerAnchor == null) {
            return Optional.empty();
        }

        final Vec3 projectedOwnerAnchor = Sable.HELPER.projectOutOfSubLevel(session.level(), ownerAnchor);
        final CompoundTag tag = new CompoundTag();
        tag.put(OWNER_REF, BlueprintRefTags.write(ownerRef.get()));
        tag.putUUID(SOURCE_ROPE_UUID, strand.getUUID());
        tag.put(SOURCE_OWNER_ANCHOR, BlueprintRefTags.writeVector(new Vector3d(
                projectedOwnerAnchor.x,
                projectedOwnerAnchor.y,
                projectedOwnerAnchor.z
        )));
        tag.put(POINTS, points);
        tag.putDouble(EXTENSION_GOAL, strand.getExtension());
        tag.put(ATTACHMENTS, attachments);
        return Optional.of(tag);
    }

    private static Optional<CompoundTag> saveAttachment(final BlueprintSaveSession session, final RopeAttachment attachment) {
        final Optional<BlueprintBlockRef> ref = session.blockRef(attachment.blockAttachment());
        if (ref.isEmpty()) {
            return Optional.empty();
        }

        final @Nullable UUID subLevelUuid = attachment.subLevelID();
        if (subLevelUuid != null && session.subLevelRef(subLevelUuid).isEmpty()) {
            return Optional.empty();
        }

        if (!(session.level().getBlockEntity(attachment.blockAttachment()) instanceof RopeStrandHolderBlockEntity)) {
            return Optional.empty();
        }

        final CompoundTag tag = new CompoundTag();
        tag.putString(ATTACHMENT_POINT, attachment.point().name());
        tag.put(ATTACHMENT_REF, BlueprintRefTags.write(ref.get()));
        if (subLevelUuid != null) {
            tag.putUUID(ATTACHMENT_SUB_LEVEL_UUID, subLevelUuid);
        }
        return Optional.of(tag);
    }

    private static void registerPlacedRope(final BlueprintPlaceSession session, final CompoundTag tag) {
        final @Nullable BlockPos ownerPos = BlueprintRefTags.read(tag, OWNER_REF)
                .map(session::mapBlock)
                .orElse(null);
        if (ownerPos == null) {
            return;
        }

        final ServerLevel level = session.level();
        final BlockEntity blockEntity = level.getBlockEntity(ownerPos);
        if (!(blockEntity instanceof final RopeStrandHolderBlockEntity holder)) {
            return;
        }

        final RopeStrandHolderBehavior behavior = holder.getBehavior();
        if (behavior == null) {
            return;
        }

        final ServerRopeStrand strand = behavior.getOwnedStrand();
        if (strand == null) {
            return;
        }

        ServerLevelRopeManager.getOrCreate(level).addStrand(strand);
        strand.reattachConstraints(level);
        blockEntity.setChanged();
    }
}

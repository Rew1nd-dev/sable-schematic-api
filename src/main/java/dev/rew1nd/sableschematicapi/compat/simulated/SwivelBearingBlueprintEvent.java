package dev.rew1nd.sableschematicapi.compat.simulated;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.rew1nd.sableschematicapi.compat.BlueprintRefTags;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

final class SwivelBearingBlueprintEvent implements SableBlueprintEvent {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SableSchematicApi.MODID, "simulated/swivel_bearing");
    private static final String LINKS = "links";
    private static final String BEARING_REF = "bearing_ref";
    private static final String PLATE_REF = "plate_ref";
    private static final String ATTACHED_SUB_LEVEL_UUID = "attached_sub_level_uuid";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onSaveAfterBlocks(final BlueprintSaveSession session, final CompoundTag data) {
        final ListTag links = new ListTag();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (final SubLevelSaveFrame frame : session.frames()) {
            final BoundingBox3i bounds = frame.storageBounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        mutablePos.set(x, y, z);
                        final BlockEntity blockEntity = session.level().getBlockEntity(mutablePos);
                        if (blockEntity instanceof final SwivelBearingBlockEntity bearing) {
                            saveBearingLink(session, bearing).ifPresent(links::add);
                        }
                    }
                }
            }
        }

        if (!links.isEmpty()) {
            data.put(LINKS, links);
        }
    }

    @Override
    public void onPlaceAfterBlockEntities(final BlueprintPlaceSession session, final CompoundTag data) {
        final ListTag links = data.getList(LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size(); i++) {
            restoreBearingLink(session, links.getCompound(i));
        }
    }

    private static Optional<CompoundTag> saveBearingLink(final BlueprintSaveSession session, final SwivelBearingBlockEntity bearing) {
        final @Nullable UUID attachedSubLevelUuid = bearing.getSubLevelID();
        final @Nullable BlockPos platePos = bearing.getPlatePos();
        if (attachedSubLevelUuid == null || platePos == null) {
            return Optional.empty();
        }

        if (session.subLevelRef(attachedSubLevelUuid).isEmpty()) {
            return Optional.empty();
        }

        if (!(session.level().getBlockEntity(platePos) instanceof SwivelBearingPlateBlockEntity)) {
            return Optional.empty();
        }

        final Optional<BlueprintBlockRef> bearingRef = session.blockRef(bearing.getBlockPos());
        final Optional<BlueprintBlockRef> plateRef = session.blockRef(platePos);
        if (bearingRef.isEmpty() || plateRef.isEmpty()) {
            return Optional.empty();
        }

        final CompoundTag tag = new CompoundTag();
        tag.put(BEARING_REF, BlueprintRefTags.write(bearingRef.get()));
        tag.put(PLATE_REF, BlueprintRefTags.write(plateRef.get()));
        tag.putUUID(ATTACHED_SUB_LEVEL_UUID, attachedSubLevelUuid);
        return Optional.of(tag);
    }

    private static void restoreBearingLink(final BlueprintPlaceSession session, final CompoundTag tag) {
        final @Nullable BlockPos bearingPos = BlueprintRefTags.read(tag, BEARING_REF)
                .map(session::mapBlock)
                .orElse(null);
        final @Nullable BlockPos platePos = BlueprintRefTags.read(tag, PLATE_REF)
                .map(session::mapBlock)
                .orElse(null);
        final @Nullable UUID attachedSubLevelUuid = tag.hasUUID(ATTACHED_SUB_LEVEL_UUID)
                ? session.mapSubLevel(tag.getUUID(ATTACHED_SUB_LEVEL_UUID))
                : null;

        if (bearingPos == null || platePos == null || attachedSubLevelUuid == null) {
            return;
        }

        final ServerLevel level = session.level();
        final BlockEntity bearingBlockEntity = level.getBlockEntity(bearingPos);
        final BlockEntity plateBlockEntity = level.getBlockEntity(platePos);
        if (!(bearingBlockEntity instanceof final SwivelBearingBlockEntity bearing)
                || !(plateBlockEntity instanceof final SwivelBearingPlateBlockEntity plate)) {
            return;
        }

        bearing.setSubLevelID(attachedSubLevelUuid);
        bearing.setPlatePos(platePos);
        plate.setParent(bearing);
        bearing.associatePlateWithParent();
        bearing.setChanged();
        plate.setChanged();

        level.getServer().execute(() -> reattachIfPossible(level, bearingPos));
    }

    private static void reattachIfPossible(final ServerLevel level, final BlockPos bearingPos) {
        final BlockEntity blockEntity = level.getBlockEntity(bearingPos);
        if (!(blockEntity instanceof final SwivelBearingBlockEntity bearing) || bearing.getSubLevelID() == null) {
            return;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        final SubLevel attached = container.getSubLevel(bearing.getSubLevelID());
        if (attached != null) {
            bearing.reattachConstraint(attached, true);
        }
    }
}

package dev.rew1nd.sableschematicapi.blueprint;

import dev.ryanhcode.sable.Sable;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSavePhase;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SableBlueprintExporter {
    private SableBlueprintExporter() {
    }

    public static SableBlueprint export(final ServerLevel level, final Vec3 origin, final double radius) {
        final BoundingBox3d rootBounds = new BoundingBox3d(
                origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius
        );
        final BlueprintSaveSession session = new BlueprintSaveSession(level, new Vector3d(origin.x, origin.y, origin.z), rootBounds);
        final List<SableBlueprint.SubLevelData> entries = new ObjectArrayList<>();
        final Set<UUID> seen = new ObjectOpenHashSet<>();
        int id = 0;

        session.setPhase(BlueprintSavePhase.SELECT_SUBLEVELS);
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, rootBounds)) {
            if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) {
                continue;
            }

            if (!seen.add(serverSubLevel.getUniqueId())) {
                continue;
            }

            final BoundingBox3ic plotBounds = serverSubLevel.getPlot().getBoundingBox();
            if (plotBounds == BoundingBox3i.EMPTY || plotBounds.volume() <= 0) {
                continue;
            }

            session.setPhase(BlueprintSavePhase.BUILD_FRAMES);
            final BoundingBox3i storageBounds = new BoundingBox3i(plotBounds);
            final BlockPos blocksOrigin = new BlockPos(storageBounds.minX(), storageBounds.minY(), storageBounds.minZ());
            session.addFrame(new SubLevelSaveFrame(
                    id++,
                    serverSubLevel.getUniqueId(),
                    serverSubLevel,
                    storageBounds,
                    blocksOrigin,
                    serverSubLevel.logicalPose()
            ));
        }

        session.setPhase(BlueprintSavePhase.BEFORE_BLOCKS);
        SableBlueprintEventRegistry.saveBeforeBlocks(session);

        session.setPhase(BlueprintSavePhase.SAVE_BLOCKS);
        for (final SubLevelSaveFrame frame : session.frames()) {
            entries.add(saveSubLevel(session, frame, origin));
        }

        session.setPhase(BlueprintSavePhase.AFTER_BLOCKS);
        SableBlueprintEventRegistry.saveAfterBlocks(session);

        session.setPhase(BlueprintSavePhase.SAVE_ENTITIES);

        session.setPhase(BlueprintSavePhase.AFTER_SAVE);
        SableBlueprintEventRegistry.saveAfterEntities(session);

        session.setPhase(BlueprintSavePhase.FINALIZE);
        return new SableBlueprint(new Vector3d(origin.x, origin.y, origin.z), rootBounds, entries, session.globalExtraData());
    }

    private static SableBlueprint.SubLevelData saveSubLevel(final BlueprintSaveSession session, final SubLevelSaveFrame frame, final Vec3 origin) {
        final LevelAccelerator accelerator = new LevelAccelerator(session.level());
        final List<BlockState> blockPalette = new ObjectArrayList<>();
        final Object2IntOpenHashMap<BlockState> paletteIds = new Object2IntOpenHashMap<>();
        final List<SableBlueprint.BlockData> blocks = new ObjectArrayList<>();
        final List<CompoundTag> blockEntities = new ObjectArrayList<>();
        final BoundingBox3i storageBounds = frame.storageBounds();
        final BoundingBox3i localBounds = new BoundingBox3i(
                0,
                0,
                0,
                storageBounds.maxX() - storageBounds.minX(),
                storageBounds.maxY() - storageBounds.minY(),
                storageBounds.maxZ() - storageBounds.minZ()
        );
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        paletteIds.defaultReturnValue(-1);

        for (int x = storageBounds.minX(); x <= storageBounds.maxX(); x++) {
            for (int y = storageBounds.minY(); y <= storageBounds.maxY(); y++) {
                for (int z = storageBounds.minZ(); z <= storageBounds.maxZ(); z++) {
                    mutablePos.set(x, y, z);

                    final BlockState state = accelerator.getBlockState(mutablePos);
                    if (state.isAir()) {
                        continue;
                    }

                    int paletteId = paletteIds.getInt(state);
                    if (paletteId < 0) {
                        paletteId = blockPalette.size();
                        blockPalette.add(state);
                        paletteIds.put(state, paletteId);
                    }

                    final BlockPos storagePos = mutablePos.immutable();
                    final BlockPos localPos = frame.toLocal(storagePos);
                    final BlockEntity blockEntity = session.level().getBlockEntity(storagePos);
                    final BlueprintBlockSaveContext context = new BlueprintBlockSaveContext(
                            session,
                            frame,
                            storagePos,
                            localPos,
                            state,
                            blockEntity
                    );
                    CompoundTag blockEntityTag = context.saveDefaultBlockEntityTag();
                    blockEntityTag = SableBlueprintMapperRegistry.save(context, blockEntityTag);

                    int blockEntityDataId = SableBlueprint.BlockData.NO_BLOCK_ENTITY_DATA;
                    if (blockEntityTag != null) {
                        blockEntityTag.putInt("x", localPos.getX());
                        blockEntityTag.putInt("y", localPos.getY());
                        blockEntityTag.putInt("z", localPos.getZ());
                        blockEntityDataId = blockEntities.size();
                        blockEntities.add(blockEntityTag.copy());
                    }

                    blocks.add(new SableBlueprint.BlockData(localPos, paletteId, blockEntityDataId));
                }
            }
        }

        return new SableBlueprint.SubLevelData(
                frame.blueprintId(),
                frame.sourceUuid(),
                portableRelativePose(frame.subLevel(), origin),
                localBounds,
                BlockPos.ZERO,
                blockPalette,
                blocks,
                blockEntities,
                List.of(),
                new CompoundTag(),
                frame.subLevel().getName()
        );
    }

    private static Pose3d portableRelativePose(final ServerSubLevel subLevel, final Vec3 origin) {
        final Pose3d relativePose = new Pose3d(subLevel.logicalPose());
        final Vector3dc centerOfMass = subLevel.getMassTracker() != null ? subLevel.getSelfMassTracker().getCenterOfMass() : null;

        if (centerOfMass != null) {
            relativePose.position().set(subLevel.logicalPose().transformPosition(new Vector3d(centerOfMass)));
        }

        relativePose.position().sub(origin.x, origin.y, origin.z);
        relativePose.rotationPoint().set(0.0, 0.0, 0.0);
        return relativePose;
    }
}

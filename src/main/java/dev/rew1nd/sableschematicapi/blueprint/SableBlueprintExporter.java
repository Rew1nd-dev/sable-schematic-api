package dev.rew1nd.sableschematicapi.blueprint;

import dev.ryanhcode.sable.Sable;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintEntitySaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSavePhase;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SableBlueprintExporter {
    private static final double ENTITY_SEARCH_INFLATE = 2.0;

    private SableBlueprintExporter() {
    }

    public static SableBlueprint export(final ServerLevel level, final Vec3 origin, final double radius) {
        final BoundingBox3d rootBounds = new BoundingBox3d(
                origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius
        );
        return export(level, origin, rootBounds);
    }

    public static SableBlueprint export(final ServerLevel level, final Vec3 start, final Vec3 end) {
        final BlockPos startBlock = BlockPos.containing(start);
        final BlockPos endBlock = BlockPos.containing(end);
        final BoundingBox3d rootBounds = new BoundingBox3d(
                Math.min(startBlock.getX(), endBlock.getX()),
                Math.min(startBlock.getY(), endBlock.getY()),
                Math.min(startBlock.getZ(), endBlock.getZ()),
                Math.max(startBlock.getX(), endBlock.getX()) + 1.0,
                Math.max(startBlock.getY(), endBlock.getY()) + 1.0,
                Math.max(startBlock.getZ(), endBlock.getZ()) + 1.0
        );
        return export(level, start, rootBounds);
    }

    public static SableBlueprint export(final ServerLevel level, final Vec3 origin, final BoundingBox3d rootBounds) {
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
        final List<SubLevelSaveFrame> frames = session.frames();
        final BlueprintCoordinateFrame coordinateFrame = coordinateFrame(frames, origin);
        for (final SubLevelSaveFrame frame : frames) {
            entries.add(saveSubLevel(session, frame, coordinateFrame.origin()));
        }

        session.setPhase(BlueprintSavePhase.AFTER_BLOCKS);
        SableBlueprintEventRegistry.saveAfterBlocks(session);

        session.setPhase(BlueprintSavePhase.SAVE_ENTITIES);
        for (int i = 0; i < entries.size(); i++) {
            entries.set(i, withEntities(entries.get(i), saveSubLevelEntities(session, frames.get(i))));
        }

        session.setPhase(BlueprintSavePhase.AFTER_SAVE);
        SableBlueprintEventRegistry.saveAfterEntities(session);

        session.setPhase(BlueprintSavePhase.FINALIZE);
        return SableBlueprint.withCanonicalBounds(coordinateFrame.origin(), coordinateFrame.canonicalBounds(), entries, session.globalExtraData());
    }

    private static SableBlueprint.SubLevelData saveSubLevel(final BlueprintSaveSession session,
                                                            final SubLevelSaveFrame frame,
                                                            final Vector3dc origin) {
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

                    session.recordSavedBlock(
                            frame,
                            storagePos,
                            localPos,
                            state,
                            blockEntity,
                            blockEntityDataId,
                            blockEntityTag
                    );
                    blocks.add(new SableBlueprint.BlockData(localPos, paletteId, blockEntityDataId));
                }
            }
        }

        return new SableBlueprint.SubLevelData(
                frame.blueprintId(),
                frame.sourceUuid(),
                portableRelativePose(frame.subLevel(), frame.sourcePose(), origin),
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

    private static List<SableBlueprint.EntityData> saveSubLevelEntities(final BlueprintSaveSession session,
                                                                        final SubLevelSaveFrame frame) {
        final List<SableBlueprint.EntityData> entities = new ObjectArrayList<>();
        final Set<UUID> seenEntities = new ObjectOpenHashSet<>();
        final BoundingBox3i bounds = frame.storageBounds();
        final AABB aabb = new AABB(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX() + 1.0,
                bounds.maxY() + 1.0,
                bounds.maxZ() + 1.0
        );

        for (final Entity entity : session.level().getEntitiesOfClass(Entity.class, aabb.inflate(ENTITY_SEARCH_INFLATE), SableBlueprintExporter::shouldSaveEntity)) {
            saveEntity(session, frame, entities, seenEntities, entity, true);
        }

        for (final KinematicContraption contraption : frame.subLevel().getPlot().getContraptions()) {
            if (!contraption.sable$isValid() || !(contraption instanceof Entity entity)) {
                continue;
            }

            saveEntity(session, frame, entities, seenEntities, entity, false);
        }

        return entities;
    }

    private static void saveEntity(final BlueprintSaveSession session,
                                   final SubLevelSaveFrame frame,
                                   final List<SableBlueprint.EntityData> entities,
                                   final Set<UUID> seenEntities,
                                   final Entity entity,
                                   final boolean requireContainingSubLevel) {
        if (!shouldSaveEntity(entity)) {
            return;
        }

        if (requireContainingSubLevel && Sable.HELPER.getContaining(entity) != frame.subLevel()) {
            return;
        }

        if (!seenEntities.add(entity.getUUID())) {
            return;
        }

        final BlockPos origin = frame.blocksOrigin();
        final Vector3d localPos = new Vector3d(
                entity.getX() - origin.getX(),
                entity.getY() - origin.getY(),
                entity.getZ() - origin.getZ()
        );
        final BlueprintEntitySaveContext context = new BlueprintEntitySaveContext(session, frame, entity, localPos);
        CompoundTag entityTag = new CompoundTag();
        if (!entity.save(entityTag)) {
            return;
        }

        entityTag = SableBlueprintMapperRegistry.saveEntity(context, entityTag);
        if (entityTag == null || entityTag.isEmpty()) {
            return;
        }

        entities.add(new SableBlueprint.EntityData(localPos, entityTag));
    }

    private static boolean shouldSaveEntity(final Entity entity) {
        return !(entity instanceof Player) && !entity.isPassenger() && !entity.isRemoved();
    }

    private static SableBlueprint.SubLevelData withEntities(final SableBlueprint.SubLevelData entry,
                                                            final List<SableBlueprint.EntityData> entities) {
        return new SableBlueprint.SubLevelData(
                entry.id(),
                entry.sourceUuid(),
                entry.relativePose(),
                entry.localBounds(),
                entry.blocksOrigin(),
                entry.blockPalette(),
                entry.blocks(),
                entry.blockEntities(),
                entities,
                entry.extraData(),
                entry.name()
        );
    }

    private static BlueprintCoordinateFrame coordinateFrame(final List<SubLevelSaveFrame> frames, final Vec3 fallbackOrigin) {
        if (frames.isEmpty()) {
            return new BlueprintCoordinateFrame(
                    new Vector3d(fallbackOrigin.x, fallbackOrigin.y, fallbackOrigin.z),
                    new BoundingBox3d(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            );
        }

        final SubLevelSaveFrame basis = selectBasis(frames);
        final Pose3d basisAnchorPose = portableAnchorPose(basis.subLevel(), basis.sourcePose());
        return new BlueprintCoordinateFrame(
                basisAnchorPose.position(),
                canonicalBounds(frames, basisAnchorPose)
        );
    }

    private static SubLevelSaveFrame selectBasis(final List<SubLevelSaveFrame> frames) {
        SubLevelSaveFrame best = frames.getFirst();
        long bestVolume = storageVolume(best.storageBounds());
        for (int i = 1; i < frames.size(); i++) {
            final SubLevelSaveFrame candidate = frames.get(i);
            final long volume = storageVolume(candidate.storageBounds());
            if (volume > bestVolume) {
                best = candidate;
                bestVolume = volume;
            }
        }
        return best;
    }

    private static long storageVolume(final BoundingBox3i bounds) {
        final int width = bounds.maxX() - bounds.minX() + 1;
        final int height = bounds.maxY() - bounds.minY() + 1;
        final int depth = bounds.maxZ() - bounds.minZ() + 1;
        return (long) Math.max(width, 0) * Math.max(height, 0) * Math.max(depth, 0);
    }

    private static BoundingBox3d canonicalBounds(final List<SubLevelSaveFrame> frames, final Pose3d basisAnchorPose) {
        final BoundsBuilder builder = new BoundsBuilder();
        for (final SubLevelSaveFrame frame : frames) {
            includeStorageBounds(builder, frame, basisAnchorPose);
        }

        if (!builder.hasBounds()) {
            builder.include(new Vector3d());
            builder.include(new Vector3d(1.0, 1.0, 1.0));
        }
        return builder.build();
    }

    private static void includeStorageBounds(final BoundsBuilder builder,
                                             final SubLevelSaveFrame frame,
                                             final Pose3d basisAnchorPose) {
        final BoundingBox3i bounds = frame.storageBounds();
        if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            return;
        }

        final double[] xs = new double[]{bounds.minX(), bounds.maxX() + 1.0};
        final double[] ys = new double[]{bounds.minY(), bounds.maxY() + 1.0};
        final double[] zs = new double[]{bounds.minZ(), bounds.maxZ() + 1.0};
        for (final double x : xs) {
            for (final double y : ys) {
                for (final double z : zs) {
                    final Vector3d point = new Vector3d(x, y, z);
                    frame.sourcePose().transformPosition(point);
                    basisAnchorPose.transformPositionInverse(point);
                    builder.include(point);
                }
            }
        }
    }

    private static Pose3d portableRelativePose(final ServerSubLevel subLevel,
                                               final Pose3d sourcePose,
                                               final Vector3dc origin) {
        final Pose3d relativePose = portableAnchorPose(subLevel, sourcePose);
        relativePose.position().sub(origin.x(), origin.y(), origin.z());
        return relativePose;
    }

    private static Pose3d portableAnchorPose(final ServerSubLevel subLevel, final Pose3d sourcePose) {
        final Pose3d pose = new Pose3d(sourcePose);
        final Vector3dc centerOfMass = subLevel.getMassTracker() != null ? subLevel.getSelfMassTracker().getCenterOfMass() : null;

        if (centerOfMass != null) {
            pose.position().set(sourcePose.transformPosition(new Vector3d(centerOfMass)));
        }

        pose.rotationPoint().set(0.0, 0.0, 0.0);
        return pose;
    }

    private record BlueprintCoordinateFrame(Vector3d origin, BoundingBox3d canonicalBounds) {
        private BlueprintCoordinateFrame {
            origin = new Vector3d(origin);
            canonicalBounds = new BoundingBox3d(canonicalBounds);
        }

        @Override
        public Vector3d origin() {
            return new Vector3d(this.origin);
        }

        @Override
        public BoundingBox3d canonicalBounds() {
            return new BoundingBox3d(this.canonicalBounds);
        }
    }

    private static final class BoundsBuilder {
        private boolean hasBounds;
        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;

        private boolean hasBounds() {
            return this.hasBounds;
        }

        private void include(final Vector3dc point) {
            if (!this.hasBounds) {
                this.minX = point.x();
                this.minY = point.y();
                this.minZ = point.z();
                this.maxX = point.x();
                this.maxY = point.y();
                this.maxZ = point.z();
                this.hasBounds = true;
                return;
            }

            this.minX = Math.min(this.minX, point.x());
            this.minY = Math.min(this.minY, point.y());
            this.minZ = Math.min(this.minZ, point.z());
            this.maxX = Math.max(this.maxX, point.x());
            this.maxY = Math.max(this.maxY, point.y());
            this.maxZ = Math.max(this.maxZ, point.z());
        }

        private BoundingBox3d build() {
            return new BoundingBox3d(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }
}

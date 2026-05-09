package dev.rew1nd.sableschematicapi.blueprint;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticCategory;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticReport;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticStage;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintEntityPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlacePhase;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlacedBlock;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;
import dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SableBlueprintPlacer {
    private SableBlueprintPlacer() {
    }

    public static Result place(final ServerLevel level, final SableBlueprint blueprint, final Vec3 origin) {
        return place(level, blueprint, BlueprintPlacementPlan.legacy(blueprint, origin));
    }

    public static Result place(final ServerLevel level, final SableBlueprint blueprint, final BlueprintPlacementPlan placementPlan) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("No Sable sub-level container is available for this level");
        }

        final BlueprintDiagnosticReport.Builder diagnostics = BlueprintDiagnosticReport.builder();
        final BlueprintPlaceSession session = new BlueprintPlaceSession(level, placementPlan.origin(), blueprint.globalExtraData(), diagnostics);
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final List<PlacedBlock> placedBlocks = new ObjectArrayList<>();

        session.setPhase(BlueprintPlacePhase.ALLOCATE_SUBLEVELS);
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final Pose3d pose = placementPlan.pose(entry);
            final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
            final BlockPos placedBlocksOrigin = placedBlocksOrigin(subLevel, entry.localBounds());

            session.mapSubLevel(entry.id(), entry.sourceUuid(), subLevel, placedBlocksOrigin);

            if (entry.name() != null) {
                subLevel.setName(entry.name());
            }
        }

        session.setPhase(BlueprintPlacePhase.BUILD_MAPPINGS);

        session.setPhase(BlueprintPlacePhase.BEFORE_BLOCKS);
        SableBlueprintEventRegistry.placeBeforeBlocks(session);

        session.setPhase(BlueprintPlacePhase.PLACE_BLOCK_STATES);
        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(level, true);
        try {
            for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
                final ServerSubLevel subLevel = requirePlacedSubLevel(session, entry);

                for (final SableBlueprint.BlockData block : entry.blocks()) {
                    final @Nullable BlockState state = placedBlockState(session, entry, block);
                    if (state == null) {
                        continue;
                    }

                    BlockPos storagePos = null;
                    try {
                        storagePos = requireMappedBlock(session, entry, block.localPos());
                        ensureChunk(subLevel, storagePos);

                        final LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(storagePos.getX()), SectionPos.blockToSectionCoord(storagePos.getZ()));
                        final BlockState oldState = chunk.setBlockState(storagePos, state, false);
                        final BlueprintPlacedBlock placedBlock = session.recordPlacedBlock(
                                entry.id(),
                                entry.sourceUuid(),
                                subLevel,
                                block.localPos(),
                                storagePos,
                                state,
                                block.blockEntityDataId()
                        );
                        placedBlocks.add(new PlacedBlock(entry, block, placedBlock, subLevel, storagePos, state, oldState != null ? oldState : Blocks.AIR.defaultBlockState()));
                    } catch (final RuntimeException e) {
                        session.diagnostics().warn(
                                BlueprintDiagnosticStage.PLACE_BLOCK_STATES,
                                BlueprintDiagnosticCategory.BLOCK_PLACE_FAILED,
                                entry.id(),
                                block.localPos(),
                                storagePos,
                                state.getBlock().builtInRegistryHolder().key().location().toString(),
                                "Skipped a blueprint block that could not be placed.",
                                "Failed to place blueprint block.",
                                e
                        );
                    }
                }
            }
        } finally {
            SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(level, false);
        }

        session.setPhase(BlueprintPlacePhase.LOAD_BLOCK_ENTITIES);
        for (final PlacedBlock placedBlock : placedBlocks) {
            if (!placedBlock.block().hasBlockEntityData()) {
                continue;
            }

            loadBlockEntity(session, placedBlock);
        }

        session.setPhase(BlueprintPlacePhase.AFTER_BLOCK_ENTITIES);
        session.runAfterBlockEntityTasks();
        SableBlueprintEventRegistry.placeAfterBlockEntities(session);

        session.setPhase(BlueprintPlacePhase.PLACE_ENTITIES);
        placeEntities(session, blueprint);

        session.setPhase(BlueprintPlacePhase.WORLD_UPDATES);
        notifyPlacedBlocks(session, placedBlocks);

        session.setPhase(BlueprintPlacePhase.AFTER_PLACE);
        SableBlueprintEventRegistry.placeAfterBlocks(session);

        session.setPhase(BlueprintPlacePhase.FINALIZE);
        int placed = 0;
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final ServerSubLevel subLevel = requirePlacedSubLevel(session, entry);

            if (subLevel.getPlot().getBoundingBox() == BoundingBox3i.EMPTY || subLevel.getPlot().getBoundingBox().volume() <= 0) {
                if (!hasPlacedBlocks(entry, placedBlocks)) {
                    container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
                    session.diagnostics().warn(
                            BlueprintDiagnosticStage.FINALIZE,
                            BlueprintDiagnosticCategory.SKIPPED_BLOCK,
                            entry.id(),
                            null,
                            null,
                            "empty sub-level",
                            "Skipped an empty blueprint sub-level.",
                            "Removed placed sub-level " + entry.id() + " because no blocks were placed.",
                            null
                    );
                    continue;
                }
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
                throw new IllegalStateException("Loaded blueprint sub-level " + entry.id() + " has empty plot bounds");
            }

            subLevel.getPlot().updateBoundingBox();
            subLevel.updateMergedMassData(1.0f);
            physicsSystem.getPipeline().onStatsChanged(subLevel);

            final Pose3d pose = placementPlan.pose(entry);
            physicsSystem.getPipeline().teleport(subLevel, pose.position(), pose.orientation());
            subLevel.updateLastPose();
            placed++;
        }

        return new Result(placed, session.subLevelUuidMap(), session.diagnosticReport());
    }

    private static boolean hasPlacedBlocks(final SableBlueprint.SubLevelData entry, final List<PlacedBlock> placedBlocks) {
        for (final PlacedBlock placedBlock : placedBlocks) {
            if (placedBlock.entry().id() == entry.id()) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable BlockState placedBlockState(final BlueprintPlaceSession session,
                                                         final SableBlueprint.SubLevelData entry,
                                                         final SableBlueprint.BlockData block) {
        if (block.paletteId() < 0 || block.paletteId() >= entry.blockPalette().size()) {
            session.diagnostics().warn(
                    BlueprintDiagnosticStage.PLACE_BLOCK_STATES,
                    BlueprintDiagnosticCategory.INVALID_PALETTE_REFERENCE,
                    entry.id(),
                    block.localPos(),
                    null,
                    "palette #" + block.paletteId(),
                    "Skipped a blueprint block with an invalid palette reference.",
                    "Skipped blueprint block because palette id " + block.paletteId() + " is outside palette size " + entry.blockPalette().size() + ".",
                    null
            );
            return null;
        }

        if (!entry.isPaletteAvailable(block.paletteId())) {
            session.diagnostics().warn(
                    BlueprintDiagnosticStage.PLACE_BLOCK_STATES,
                    BlueprintDiagnosticCategory.SKIPPED_BLOCK,
                    entry.id(),
                    block.localPos(),
                    null,
                    "palette #" + block.paletteId(),
                    "Skipped a blueprint block from a missing or invalid block state.",
                    "Skipped blueprint block because palette #" + block.paletteId() + " was unavailable during decode.",
                    null
            );
            return null;
        }

        return entry.blockPalette().get(block.paletteId());
    }

    private static BlockPos placedBlocksOrigin(final ServerSubLevel subLevel, final BoundingBox3i localBounds) {
        final BlockPos center = subLevel.getPlot().getCenterBlock();
        if (localBounds == BoundingBox3i.EMPTY || localBounds.volume() <= 0) {
            return center;
        }

        final int centerX = (localBounds.minX() + localBounds.maxX()) / 2;
        final int centerY = (localBounds.minY() + localBounds.maxY()) / 2;
        final int centerZ = (localBounds.minZ() + localBounds.maxZ()) / 2;
        return new BlockPos(center.getX() - centerX, center.getY() - centerY, center.getZ() - centerZ);
    }

    private static ServerSubLevel requirePlacedSubLevel(final BlueprintPlaceSession session, final SableBlueprint.SubLevelData entry) {
        final ServerSubLevel subLevel = session.placedSubLevel(entry.id());
        if (subLevel == null) {
            throw new IllegalStateException("Blueprint sub-level " + entry.id() + " was not allocated");
        }
        return subLevel;
    }

    private static BlockPos requireMappedBlock(final BlueprintPlaceSession session, final SableBlueprint.SubLevelData entry, final BlockPos localPos) {
        final BlockPos storagePos = session.mapBlock(new BlueprintBlockRef(entry.id(), localPos));
        if (storagePos == null) {
            throw new IllegalStateException("No placed block mapping for sub-level " + entry.id() + " at " + localPos);
        }
        return storagePos;
    }

    private static void ensureChunk(final ServerSubLevel subLevel, final BlockPos storagePos) {
        final ServerLevelPlot plot = subLevel.getPlot();
        final ChunkPos globalChunk = new ChunkPos(storagePos);
        final ChunkPos localChunk = plot.toLocal(globalChunk);
        if (plot.getChunkHolder(localChunk) == null) {
            plot.newEmptyChunk(globalChunk);
        }
    }

    private static void loadBlockEntity(final BlueprintPlaceSession session, final PlacedBlock placedBlock) {
        final SableBlueprint.SubLevelData entry = placedBlock.entry();
        final int tagId = placedBlock.block().blockEntityDataId();
        if (tagId < 0 || tagId >= entry.blockEntities().size()) {
            session.diagnostics().warn(
                    BlueprintDiagnosticStage.LOAD_BLOCK_ENTITIES,
                    BlueprintDiagnosticCategory.INVALID_BLOCK_ENTITY_REFERENCE,
                    entry.id(),
                    placedBlock.block().localPos(),
                    placedBlock.storagePos(),
                    "block entity tag #" + tagId,
                    "Ignored invalid block entity data for a blueprint block.",
                    "Ignored block entity data id " + tagId + " because block entity tag count is " + entry.blockEntities().size() + ".",
                    null
            );
            return;
        }

        final CompoundTag tag = entry.blockEntities().get(tagId).copy();
        final BlockPos storagePos = placedBlock.storagePos();
        final ServerLevel level = session.level();
        final String subject = tag.contains("id") ? tag.getString("id") : "block entity tag #" + tagId;

        tag.putInt("x", storagePos.getX());
        tag.putInt("y", storagePos.getY());
        tag.putInt("z", storagePos.getZ());

        BlockEntity blockEntity;
        try {
            blockEntity = level.getBlockEntity(storagePos);
            if (blockEntity == null) {
                final LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(storagePos.getX()), SectionPos.blockToSectionCoord(storagePos.getZ()));
                blockEntity = BlockEntity.loadStatic(storagePos, placedBlock.state(), tag, level.registryAccess());
                if (blockEntity != null) {
                    chunk.setBlockEntity(blockEntity);
                }
            }
        } catch (final RuntimeException e) {
            recordBlockEntityLoadFailure(session, placedBlock, subject, "Failed to create block entity from blueprint data.", e);
            return;
        }

        if (blockEntity == null) {
            recordBlockEntityLoadFailure(session, placedBlock, subject, "Block entity type was unavailable or did not match the placed block state.", null);
            return;
        }
        session.attachBlockEntity(placedBlock.view(), blockEntity);

        final BlueprintBlockPlaceContext context = new BlueprintBlockPlaceContext(
                session,
                placedBlock.subLevel(),
                entry.id(),
                entry.sourceUuid(),
                placedBlock.block().localPos(),
                storagePos,
                placedBlock.state()
        );

        try {
            SableBlueprintMapperRegistry.beforeLoadBlockEntity(context, blockEntity, tag);
            tag.putInt("x", storagePos.getX());
            tag.putInt("y", storagePos.getY());
            tag.putInt("z", storagePos.getZ());
            blockEntity.loadWithComponents(tag, level.registryAccess());
        } catch (final RuntimeException e) {
            recordBlockEntityLoadFailure(session, placedBlock, subject, "Failed to load block entity NBT.", e);
            return;
        }

        final BlockEntity loadedBlockEntity = blockEntity;
        final CompoundTag loadedTag = tag.copy();
        session.deferAfterBlockEntities(() -> SableBlueprintMapperRegistry.afterLoadBlockEntity(context, loadedBlockEntity, loadedTag));
    }

    private static void recordBlockEntityLoadFailure(final BlueprintPlaceSession session,
                                                     final PlacedBlock placedBlock,
                                                     final String subject,
                                                     final String debugMessage,
                                                     @Nullable final RuntimeException e) {
        session.diagnostics().warn(
                BlueprintDiagnosticStage.LOAD_BLOCK_ENTITIES,
                BlueprintDiagnosticCategory.BLOCK_ENTITY_LOAD_FAILED,
                placedBlock.entry().id(),
                placedBlock.block().localPos(),
                placedBlock.storagePos(),
                subject,
                "Skipped block entity data that could not be loaded.",
                debugMessage,
                e
        );
    }

    private static void placeEntities(final BlueprintPlaceSession session, final SableBlueprint blueprint) {
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final ServerSubLevel subLevel = requirePlacedSubLevel(session, entry);
            if (session.placedBlocks().blocksInSubLevel(entry.id()).isEmpty()) {
                if (!entry.entities().isEmpty()) {
                    session.diagnostics().warn(
                            BlueprintDiagnosticStage.PLACE_ENTITIES,
                            BlueprintDiagnosticCategory.ENTITY_SKIPPED,
                            entry.id(),
                            null,
                            null,
                            "empty sub-level",
                            "Skipped blueprint entities because their sub-level had no placed blocks.",
                            "Skipped " + entry.entities().size() + " entity/entities because sub-level " + entry.id() + " had no placed blocks.",
                            null
                    );
                }
                continue;
            }

            for (final SableBlueprint.EntityData data : entry.entities()) {
                try {
                    placeEntity(session, entry, subLevel, data);
                } catch (final RuntimeException e) {
                    session.diagnostics().warn(
                            BlueprintDiagnosticStage.PLACE_ENTITIES,
                            BlueprintDiagnosticCategory.ENTITY_PLACE_FAILED,
                            entry.id(),
                            null,
                            null,
                            data.tag().getString("id"),
                            "Skipped a blueprint entity that could not be placed.",
                            "Failed to place blueprint entity.",
                            e
                    );
                }
            }
        }
    }

    private static void placeEntity(final BlueprintPlaceSession session,
                                    final SableBlueprint.SubLevelData entry,
                                    final ServerSubLevel subLevel,
                                    final SableBlueprint.EntityData data) {
        CompoundTag tag = data.tag().copy();
        final Vector3d storagePos = placedEntityPosition(session, entry, data.localPos());

        writeEntityPos(tag, storagePos);
        tag.remove("UUID");

        final EntityType<?> type = EntityType.byString(tag.getString("id")).orElse(null);
        if (type == null) {
            session.diagnostics().warn(
                    BlueprintDiagnosticStage.PLACE_ENTITIES,
                    BlueprintDiagnosticCategory.ENTITY_SKIPPED,
                    entry.id(),
                    null,
                    null,
                    tag.getString("id"),
                    "Skipped an unavailable blueprint entity.",
                    "Skipped entity because entity type '" + tag.getString("id") + "' is unavailable.",
                    null
            );
            return;
        }

        final BlueprintEntityPlaceContext context = new BlueprintEntityPlaceContext(
                session,
                subLevel,
                entry.id(),
                entry.sourceUuid(),
                data.localPos(),
                storagePos
        );
        tag = SableBlueprintMapperRegistry.beforeCreateEntity(context, type, tag);
        if (tag == null) {
            return;
        }

        final Entity entity = EntityType.create(tag, session.level()).orElse(null);
        if (entity == null) {
            session.diagnostics().warn(
                    BlueprintDiagnosticStage.PLACE_ENTITIES,
                    BlueprintDiagnosticCategory.ENTITY_PLACE_FAILED,
                    entry.id(),
                    null,
                    null,
                    tag.getString("id"),
                    "Skipped a blueprint entity that could not be created.",
                    "EntityType.create returned empty for blueprint entity.",
                    null
            );
            return;
        }

        ensureChunk(subLevel, BlockPos.containing(storagePos.x, storagePos.y, storagePos.z));
        entity.moveTo(storagePos.x, storagePos.y, storagePos.z, entity.getYRot(), entity.getXRot());
        session.level().addFreshEntityWithPassengers(entity);
        SableBlueprintMapperRegistry.afterCreateEntity(context, entity, tag.copy());
    }

    private static Vector3d placedEntityPosition(final BlueprintPlaceSession session,
                                                 final SableBlueprint.SubLevelData entry,
                                                 final Vector3dc localPos) {
        final BlockPos origin = placedBlocksOrigin(session, entry);
        return new Vector3d(
                origin.getX() + localPos.x(),
                origin.getY() + localPos.y(),
                origin.getZ() + localPos.z()
        );
    }

    private static BlockPos placedBlocksOrigin(final BlueprintPlaceSession session, final SableBlueprint.SubLevelData entry) {
        final BlockPos origin = session.placedBlockOrigin(entry.id());
        if (origin == null) {
            throw new IllegalStateException("No placed block origin for sub-level " + entry.id());
        }
        return origin;
    }

    private static void writeEntityPos(final CompoundTag tag, final Vector3dc pos) {
        final ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(pos.x()));
        posTag.add(DoubleTag.valueOf(pos.y()));
        posTag.add(DoubleTag.valueOf(pos.z()));
        tag.put("Pos", posTag);
    }

    private static void notifyPlacedBlocks(final BlueprintPlaceSession session, final List<PlacedBlock> placedBlocks) {
        final ServerLevel level = session.level();
        for (final PlacedBlock placedBlock : placedBlocks) {
            final BlockPos pos = placedBlock.storagePos();
            try {
                final LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
                SubLevelAssemblyHelper.markAndNotifyBlock(level, pos, chunk, placedBlock.oldState(), placedBlock.state(), 3, 512);
            } catch (final RuntimeException e) {
                session.diagnostics().warn(
                        BlueprintDiagnosticStage.WORLD_UPDATES,
                        BlueprintDiagnosticCategory.WORLD_UPDATE_FAILED,
                        placedBlock.entry().id(),
                        placedBlock.block().localPos(),
                        pos,
                        placedBlock.state().getBlock().builtInRegistryHolder().key().location().toString(),
                        "Skipped a blueprint block world update.",
                        "Failed to notify placed blueprint block.",
                        e
                );
            }
        }
    }

    private record PlacedBlock(SableBlueprint.SubLevelData entry,
                               SableBlueprint.BlockData block,
                               BlueprintPlacedBlock view,
                               ServerSubLevel subLevel,
                               BlockPos storagePos,
                               BlockState state,
                               BlockState oldState) {
    }

    public record Result(int placedSubLevels, Map<UUID, UUID> subLevelUuidMap, BlueprintDiagnosticReport diagnostics) {
        public Result {
            diagnostics = diagnostics == null ? BlueprintDiagnosticReport.empty() : diagnostics;
        }
    }
}

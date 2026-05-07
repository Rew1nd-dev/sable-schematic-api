package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintEntityPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlacePhase;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlacedBlock;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildBlockPayload;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildMaterialBudget;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildPhase;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildStatus;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildStepResult;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildTickBudget;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadDecision;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadMode;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.ConsumeResult;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostTiming;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.AdmissionResult;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessAdmissionContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessCostStrategy;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessMapper;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperation;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperationParser;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessRegistry;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.sublevel.RuntimeSubLevelStaticService;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelOperationResult;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Incremental survival placer for Sable blueprints.
 *
 * <p>The block phase only allocates sub-levels and writes block states. Block entity
 * NBT, entities, sidecar events, world updates, and physics finalization are delayed
 * until the commit phase.</p>
 */
public final class SableBlueprintIncrementalPlacer {
    private static final int CLIENT_BLOCK_UPDATE_FLAGS = 2;
    private static final int FINAL_BLOCK_UPDATE_FLAGS = 3;
    private static final int BLOCK_UPDATE_RECURSION_LIMIT = 512;

    private SableBlueprintIncrementalPlacer() {
    }

    public static BlueprintBuildStepResult step(final ServerLevel level,
                                                final SableBlueprint blueprint,
                                                final BlueprintPlacementPlan placementPlan,
                                                final BlueprintBuildProgress progress,
                                                final BlueprintBuildTickBudget tickBudget,
                                                final BlueprintBuildMaterialBudget materialBudget) {
        try {
            if (progress.phase() == BlueprintBuildPhase.DONE) {
                unlockPlacedSubLevelLocksQuietly(level, progress);
                return BlueprintBuildStepResult.status(BlueprintBuildStatus.DONE, net.minecraft.network.chat.Component.literal("Blueprint build is already complete."));
            }
            if (progress.phase() == BlueprintBuildPhase.FAILED) {
                unlockPlacedSubLevelLocksQuietly(level, progress);
                return BlueprintBuildStepResult.failed("Blueprint build is already failed.");
            }
            ensurePlacedSubLevelLocks(level, progress);
            if (progress.phase() == BlueprintBuildPhase.DECODE || progress.phase() == BlueprintBuildPhase.PLAN) {
                progress.setPhase(BlueprintBuildPhase.ALLOCATE_AND_PLACE_BLOCKS);
            }

            int affectedBlocks = 0;
            if (progress.phase() == BlueprintBuildPhase.ALLOCATE_AND_PLACE_BLOCKS) {
                final BlockPhaseOutcome outcome = placeBlockPhase(level, blueprint, placementPlan, progress, tickBudget, materialBudget);
                affectedBlocks += outcome.affectedBlocks();
                if (outcome.result() != null) {
                    return outcome.result();
                }
                if (progress.phase() == BlueprintBuildPhase.ALLOCATE_AND_PLACE_BLOCKS) {
                    return BlueprintBuildStepResult.continueWith(affectedBlocks);
                }
            }

            if (progress.phase() == BlueprintBuildPhase.PRE_COMMIT_VALIDATE) {
                // Parse, admit, and quote all registered post-process operations
                CostQuote commitQuote = quoteNbtLoadCost(level, blueprint, progress, placementPlan.originVec3());
                final List<BlueprintPostProcessOperation> admittedOps = new ArrayList<>();
                final List<Component> warnings = new ArrayList<>();

                final BlueprintPlaceSession session = new BlueprintPlaceSession(
                        level, placementPlan.origin(), blueprint.globalExtraData());
                final BlueprintPostProcessAdmissionContext admissionCtx =
                        new BlueprintPostProcessAdmissionContext(session, blueprint, level);
                final BlueprintPostProcessCostContext costCtx =
                        new BlueprintPostProcessCostContext(session, blueprint, level);

                for (final ResourceLocation sidecarId :
                        BlueprintPostProcessRegistry.registeredSidecarIds()) {
                    final BlueprintPostProcessOperationParser parser =
                            BlueprintPostProcessRegistry.parser(sidecarId);
                    if (parser == null) {
                        continue;
                    }

                    final CompoundTag sidecarData =
                            blueprint.globalExtraData().getCompound(sidecarId.toString());
                    final  List<? extends BlueprintPostProcessOperation> parsed =
                            parser.parse(session, sidecarData, blueprint);

                    for (final BlueprintPostProcessOperation op : parsed) {
                        if (progress.isOperationApplied(op.stableKey())) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        final BlueprintPostProcessMapper<BlueprintPostProcessOperation> mapper =
                                BlueprintPostProcessRegistry.mapper(op.type());
                        if (mapper != null) {
                            final AdmissionResult admission = mapper.admit(op, admissionCtx);
                            if (!admission.admitted()) {
                                if (admission.reason() != null) {
                                    warnings.add(admission.reason());
                                }
                                continue;
                            }
                        }

                        @SuppressWarnings("unchecked")
                        final BlueprintPostProcessCostStrategy<BlueprintPostProcessOperation> costStrategy =
                                BlueprintPostProcessRegistry.cost(op.type());
                        if (costStrategy != null) {
                            commitQuote = commitQuote.merge(costStrategy.quote(op, costCtx));
                        }

                        admittedOps.add(op);
                    }
                }

                if (!progress.commitCostConsumed()) {
                    if (!materialBudget.canAfford(commitQuote)) {
                        return BlueprintBuildStepResult.waitingForMaterials(commitQuote);
                    }
                    if (!commitQuote.isEmpty()) {
                        final ConsumeResult consumed = materialBudget.consume(commitQuote);
                        if (!consumed.successful()) {
                            return BlueprintBuildStepResult.waitingForMaterials(commitQuote);
                        }
                        progress.setCommitCostConsumed(true);
                    }
                }

                // Store admitted operations so commit() can apply them
                progress.setPostProcessOperations(admittedOps);
                progress.setPhase(BlueprintBuildPhase.COMMIT_RUNTIME_STATE);
            }

            if (progress.phase() == BlueprintBuildPhase.COMMIT_RUNTIME_STATE) {
                commit(level, blueprint, placementPlan, progress);
                unlockPlacedSubLevelLocks(level, progress);
                progress.setPhase(BlueprintBuildPhase.DONE);
                return new BlueprintBuildStepResult(
                        BlueprintBuildStatus.DONE,
                        affectedBlocks,
                        CostQuote.empty(CostTiming.INFORMATIONAL),
                        net.minecraft.network.chat.Component.literal("Blueprint build complete.")
                );
            }

            return BlueprintBuildStepResult.continueWith(affectedBlocks);
        } catch (final RuntimeException e) {
            progress.setPhase(BlueprintBuildPhase.FAILED);
            unlockPlacedSubLevelLocksQuietly(level, progress);
            return BlueprintBuildStepResult.failed(e.getMessage());
        }
    }

    public static BlueprintBuildStepResult skipCurrentBlock(final ServerLevel level,
                                                            final SableBlueprint blueprint,
                                                            final BlueprintPlacementPlan placementPlan,
                                                            final BlueprintBuildProgress progress) {
        try {
            if (progress.phase() == BlueprintBuildPhase.DONE) {
                unlockPlacedSubLevelLocksQuietly(level, progress);
                return BlueprintBuildStepResult.status(BlueprintBuildStatus.DONE, Component.literal("Blueprint build is already complete."));
            }
            if (progress.phase() == BlueprintBuildPhase.FAILED) {
                unlockPlacedSubLevelLocksQuietly(level, progress);
                return BlueprintBuildStepResult.failed("Blueprint build is already failed.");
            }

            ensurePlacedSubLevelLocks(level, progress);
            if (progress.phase() == BlueprintBuildPhase.DECODE || progress.phase() == BlueprintBuildPhase.PLAN) {
                progress.setPhase(BlueprintBuildPhase.ALLOCATE_AND_PLACE_BLOCKS);
            }
            if (progress.phase() != BlueprintBuildPhase.ALLOCATE_AND_PLACE_BLOCKS) {
                return BlueprintBuildStepResult.status(
                        BlueprintBuildStatus.CONTINUE,
                        Component.literal("Only the block placement phase can skip blueprint blocks.")
                );
            }

            final ServerSubLevelContainer container = requireContainer(level);
            final List<SableBlueprint.SubLevelData> subLevels = blueprint.subLevels();
            while (progress.currentSubLevelIndex() < subLevels.size()) {
                final SableBlueprint.SubLevelData entry = subLevels.get(progress.currentSubLevelIndex());
                if (entry.blocks().isEmpty()) {
                    ensureAllocated(container, entry, placementPlan, progress);
                    progress.setCurrentSubLevelIndex(progress.currentSubLevelIndex() + 1);
                    progress.setCurrentBlockIndex(0);
                    continue;
                }
                if (progress.currentBlockIndex() >= entry.blocks().size()) {
                    progress.setCurrentSubLevelIndex(progress.currentSubLevelIndex() + 1);
                    progress.setCurrentBlockIndex(0);
                    continue;
                }

                final SableBlueprint.BlockData block = entry.blocks().get(progress.currentBlockIndex());
                ensureAllocated(container, entry, placementPlan, progress);
                if (!progress.isBlockSkipped(entry.id(), block.localPos())) {
                    progress.markBlockSkipped(entry.id(), block.localPos());
                    progress.setCurrentBlockIndex(progress.currentBlockIndex() + 1);
                    return BlueprintBuildStepResult.status(
                            BlueprintBuildStatus.CONTINUE,
                            Component.literal("Skipped current blueprint block.")
                    );
                }

                progress.setCurrentBlockIndex(progress.currentBlockIndex() + 1);
            }

            progress.setPhase(BlueprintBuildPhase.PRE_COMMIT_VALIDATE);
            return BlueprintBuildStepResult.status(
                    BlueprintBuildStatus.CONTINUE,
                    Component.literal("No remaining blueprint block to skip.")
            );
        } catch (final RuntimeException e) {
            progress.setPhase(BlueprintBuildPhase.FAILED);
            unlockPlacedSubLevelLocksQuietly(level, progress);
            return BlueprintBuildStepResult.failed(e.getMessage());
        }
    }

    private static CostQuote quoteNbtLoadCost(final ServerLevel level,
                                              final SableBlueprint blueprint,
                                              final BlueprintBuildProgress progress,
                                              final Vec3 origin) {
        CostQuote total = CostQuote.empty(CostTiming.COMMIT);
        final BlueprintBlockCostContext context = new BlueprintBlockCostContext(level, origin);

        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            for (final SableBlueprint.BlockData block : entry.blocks()) {
                if (progress.isBlockSkipped(entry.id(), block.localPos())) {
                    continue;
                }
                final BlueprintBuildBlockPayload payload = payload(blueprint, entry, block);
                if (!payload.nbtDecision().loadsNbt()) {
                    continue;
                }
                total = total.merge(BlueprintBlockCostRules.quoteNbtLoad(payload, context));
            }
        }

        return total.compact();
    }

    /**
     * Estimates the material cost for blocks and post-process operations that have not
     * yet been completed. Uses the current {@link BlueprintBuildProgress} cursor to
     * skip work that has already been done.
     *
     * <p>This method does not modify any world state or progress.</p>
     */
    public static CostQuote estimateRemainingCost(final ServerLevel level,
                                                   final SableBlueprint blueprint,
                                                   final BlueprintBuildProgress progress) {
        if (progress.phase() == BlueprintBuildPhase.DONE) {
            return CostQuote.empty(CostTiming.INFORMATIONAL);
        }

        CostQuote total = CostQuote.empty(CostTiming.INFORMATIONAL);
        final BlueprintBlockCostContext blockCtx = new BlueprintBlockCostContext(level, Vec3.ZERO);

        // Block costs from the current cursor onward
        if (progress.phase() == BlueprintBuildPhase.DECODE
                || progress.phase() == BlueprintBuildPhase.PLAN
                || progress.phase() == BlueprintBuildPhase.ALLOCATE_AND_PLACE_BLOCKS) {

            final var subLevels = blueprint.subLevels();
            for (int si = progress.currentSubLevelIndex(); si < subLevels.size(); si++) {
                final var entry = subLevels.get(si);
                final int startBlock = (si == progress.currentSubLevelIndex()) ? progress.currentBlockIndex() : 0;

                for (int bi = startBlock; bi < entry.blocks().size(); bi++) {
                    final var block = entry.blocks().get(bi);
                    if (progress.isBlockSkipped(entry.id(), block.localPos())) {
                        continue;
                    }
                    total = total.merge(BlueprintBlockCostRules.quotePlacement(
                            payload(blueprint, entry, block), blockCtx));
                }
            }
        }

        if (progress.commitCostConsumed()) {
            return total.compact();
        }

        total = total.merge(quoteNbtLoadCost(level, blueprint, progress, Vec3.ZERO));

        // Post-process operation costs (skip already-applied operations)
        final BlueprintPlaceSession session = new BlueprintPlaceSession(
                level, new Vector3d(0, 0, 0), blueprint.globalExtraData());
        final BlueprintPostProcessCostContext costCtx =
                new BlueprintPostProcessCostContext(session, blueprint, level);

        for (final var sidecarId : BlueprintPostProcessRegistry.registeredSidecarIds()) {
            final var parser = BlueprintPostProcessRegistry.parser(sidecarId);
            if (parser == null) {
                continue;
            }

            final CompoundTag sidecarData =
                    blueprint.globalExtraData().getCompound(sidecarId.toString());
            final var parsed = parser.parse(session, sidecarData, blueprint);

            for (final var op : parsed) {
                if (progress.isOperationApplied(op.stableKey())) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                final var mapper = BlueprintPostProcessRegistry.mapper(op.type());
                if (mapper != null) {
                    final var admission = mapper.admit(op,
                            new BlueprintPostProcessAdmissionContext(session, blueprint, level));
                    if (!admission.admitted()) {
                        continue;
                    }
                }

                @SuppressWarnings("unchecked")
                final var costStrategy = BlueprintPostProcessRegistry.cost(op.type());
                if (costStrategy != null) {
                    total = total.merge(costStrategy.quote(op, costCtx));
                }
            }
        }

        return total.compact();
    }

    private static BlockPhaseOutcome placeBlockPhase(final ServerLevel level,
                                                    final SableBlueprint blueprint,
                                                    final BlueprintPlacementPlan placementPlan,
                                                    final BlueprintBuildProgress progress,
                                                    final BlueprintBuildTickBudget tickBudget,
                                                    final BlueprintBuildMaterialBudget materialBudget) {
        final ServerSubLevelContainer container = requireContainer(level);
        final List<SableBlueprint.SubLevelData> subLevels = blueprint.subLevels();
        int affected = 0;

        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(level, true);
        try {
            while (affected < tickBudget.blockLimit() && progress.currentSubLevelIndex() < subLevels.size()) {
                final SableBlueprint.SubLevelData entry = subLevels.get(progress.currentSubLevelIndex());

                // Sub-levels with zero blocks still need allocation for commit phase
                if (entry.blocks().isEmpty()) {
                    ensureAllocated(container, entry, placementPlan, progress);
                    progress.setCurrentSubLevelIndex(progress.currentSubLevelIndex() + 1);
                    progress.setCurrentBlockIndex(0);
                    continue;
                }

                if (progress.currentBlockIndex() >= entry.blocks().size()) {
                    progress.setCurrentSubLevelIndex(progress.currentSubLevelIndex() + 1);
                    progress.setCurrentBlockIndex(0);
                    continue;
                }

                final SableBlueprint.BlockData block = entry.blocks().get(progress.currentBlockIndex());
                if (progress.isBlockSkipped(entry.id(), block.localPos())) {
                    ensureAllocated(container, entry, placementPlan, progress);
                    progress.setCurrentBlockIndex(progress.currentBlockIndex() + 1);
                    continue;
                }

                final BlueprintBuildBlockPayload payload = payload(blueprint, entry, block);
                if (payload.nbtDecision().mode() == BlueprintNbtLoadMode.DENY) {
                    return new BlockPhaseOutcome(BlueprintBuildStepResult.failed("A blueprint block is denied by survival NBT policy."), affected);
                }

                final CostQuote quote = BlueprintBlockCostRules.quotePlacement(payload, new BlueprintBlockCostContext(level, placementPlan.originVec3()));
                if (!materialBudget.canAfford(quote)) {
                    return new BlockPhaseOutcome(BlueprintBuildStepResult.waitingForMaterials(quote), affected);
                }

                // Allocate sub-level only when we are about to place the first block,
                // avoiding empty sub-levels that Sable may garbage-collect between ticks.
                final ServerSubLevel subLevel = ensureAllocated(container, entry, placementPlan, progress);

                final BlockPos storagePos = mappedBlock(progress, entry, block.localPos());
                ensureChunk(subLevel, storagePos);
                final LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(storagePos.getX()), SectionPos.blockToSectionCoord(storagePos.getZ()));
                final BlockState existing = chunk.getBlockState(storagePos);
                if (!existing.equals(payload.state())) {
                    final ConsumeResult consumed = materialBudget.consume(quote);
                    if (!consumed.successful()) {
                        return new BlockPhaseOutcome(BlueprintBuildStepResult.waitingForMaterials(quote), affected);
                    }
                    chunk.setBlockState(storagePos, payload.state(), false);
                    notifyClientBlockChange(level, storagePos, chunk, existing, payload.state());
                }

                progress.setCurrentBlockIndex(progress.currentBlockIndex() + 1);
                affected++;
            }
        } finally {
            SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(level, false);
        }

        if (progress.currentSubLevelIndex() >= subLevels.size()) {
            progress.setPhase(BlueprintBuildPhase.PRE_COMMIT_VALIDATE);
        }

        return new BlockPhaseOutcome(null, affected);
    }

    private static void commit(final ServerLevel level,
                               final SableBlueprint blueprint,
                               final BlueprintPlacementPlan placementPlan,
                               final BlueprintBuildProgress progress) {
        final ServerSubLevelContainer container = requireContainer(level);
        final BlueprintPlaceSession session = new BlueprintPlaceSession(level, placementPlan.origin(), blueprint.globalExtraData());
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final List<PlacedBlock> placedBlocks = new ObjectArrayList<>();

        for (final Map.Entry<java.util.UUID, java.util.UUID> entry : progress.allocatedUuidMap().entrySet()) {
            session.mapAllocatedUuid(entry.getKey(), entry.getValue());
        }

        session.setPhase(BlueprintPlacePhase.ALLOCATE_SUBLEVELS);
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final ServerSubLevel subLevel = requirePlacedSubLevel(container, progress, entry);
            final BlueprintBuildProgress.PlacedSubLevel placed = progress.placedSubLevel(entry.id());
            if (placed == null) {
                throw new IllegalStateException("Blueprint sub-level " + entry.id() + " was not allocated");
            }
            session.mapSubLevel(entry.id(), entry.sourceUuid(), subLevel, placed.blockOrigin());
        }

        session.setPhase(BlueprintPlacePhase.BEFORE_BLOCKS);
        SableBlueprintEventRegistry.placeBeforeBlocks(session);

        session.setPhase(BlueprintPlacePhase.PLACE_BLOCK_STATES);
        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(level, true);
        try {
            for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
                final ServerSubLevel subLevel = requirePlacedSubLevel(container, progress, entry);
                for (final SableBlueprint.BlockData block : entry.blocks()) {
                    if (progress.isBlockSkipped(entry.id(), block.localPos())) {
                        continue;
                    }

                    final BlueprintBuildBlockPayload payload = payload(blueprint, entry, block);
                    if (payload.nbtDecision().mode() == BlueprintNbtLoadMode.DENY) {
                        throw new IllegalStateException("A blueprint block is denied by survival NBT policy.");
                    }

                    final BlockPos storagePos = mappedBlock(progress, entry, block.localPos());
                    ensureChunk(subLevel, storagePos);
                    final LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(storagePos.getX()), SectionPos.blockToSectionCoord(storagePos.getZ()));
                    final BlockState oldState = chunk.getBlockState(storagePos);
                    if (!oldState.equals(payload.state())) {
                        chunk.setBlockState(storagePos, payload.state(), false);
                    }

                    final BlueprintPlacedBlock placedBlock = session.recordPlacedBlock(
                            entry.id(),
                            entry.sourceUuid(),
                            subLevel,
                            block.localPos(),
                            storagePos,
                            payload.state(),
                            block.blockEntityDataId()
                    );
                    placedBlocks.add(new PlacedBlock(entry, block, payload, placedBlock, subLevel, storagePos, oldState));
                }
            }
        } finally {
            SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(level, false);
        }

        session.setPhase(BlueprintPlacePhase.LOAD_BLOCK_ENTITIES);
        for (final PlacedBlock placedBlock : placedBlocks) {
            if (!placedBlock.block().hasBlockEntityData() || !placedBlock.payload().nbtDecision().loadsNbt()) {
                continue;
            }
            loadBlockEntity(session, placedBlock, placedBlock.payload().effectiveBlockEntityTag());
        }

        session.setPhase(BlueprintPlacePhase.AFTER_BLOCK_ENTITIES);
        session.runAfterBlockEntityTasks();
        SableBlueprintEventRegistry.placeAfterBlockEntities(session);

        // Apply typed post-process operations that were admitted during PRE_COMMIT_VALIDATE
        final BlueprintPostProcessContext applyCtx = new BlueprintPostProcessContext(session, level);
        for (final BlueprintPostProcessOperation op : progress.postProcessOperations()) {
            if (progress.isOperationApplied(op.stableKey())) {
                continue;
            }

            @SuppressWarnings("unchecked")
            final BlueprintPostProcessMapper<BlueprintPostProcessOperation> mapper =
                    BlueprintPostProcessRegistry.mapper(op.type());
            if (mapper != null) {
                mapper.apply(op, applyCtx);
            }
            progress.markOperationApplied(op.stableKey());
        }

        session.setPhase(BlueprintPlacePhase.PLACE_ENTITIES);
        placeEntities(session, blueprint, progress);

        session.setPhase(BlueprintPlacePhase.WORLD_UPDATES);
        notifyPlacedBlocks(level, placedBlocks);

        session.setPhase(BlueprintPlacePhase.AFTER_PLACE);
        SableBlueprintEventRegistry.placeAfterBlocks(session);

        session.setPhase(BlueprintPlacePhase.FINALIZE);
        int placed = 0;
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final ServerSubLevel subLevel = requirePlacedSubLevel(container, progress, entry);
            if (subLevel.getPlot().getBoundingBox() == BoundingBox3i.EMPTY || subLevel.getPlot().getBoundingBox().volume() <= 0) {
                if (!hasUnskippedBlocks(entry, progress)) {
                    continue;
                }
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
                throw new IllegalStateException("Loaded blueprint sub-level " + entry.id() + " has empty plot bounds");
            }

            subLevel.getPlot().updateBoundingBox();
            subLevel.updateMergedMassData(1.0f);
            physicsSystem.getPipeline().onStatsChanged(subLevel);

            subLevel.updateLastPose();
            placed++;
        }

        for (final Map.Entry<java.util.UUID, java.util.UUID> entry : session.allocatedUuidMap().entrySet()) {
            progress.recordAllocatedUuid(entry.getKey(), entry.getValue());
        }
    }

    private static boolean hasUnskippedBlocks(final SableBlueprint.SubLevelData entry,
                                              final BlueprintBuildProgress progress) {
        for (final SableBlueprint.BlockData block : entry.blocks()) {
            if (!progress.isBlockSkipped(entry.id(), block.localPos())) {
                return true;
            }
        }
        return false;
    }

    private static BlueprintBuildBlockPayload payload(final SableBlueprint blueprint,
                                                      final SableBlueprint.SubLevelData entry,
                                                      final SableBlueprint.BlockData block) {
        if (block.paletteId() < 0 || block.paletteId() >= entry.blockPalette().size()) {
            throw new IllegalStateException("Invalid blueprint palette id " + block.paletteId());
        }

        final BlockState state = entry.blockPalette().get(block.paletteId());
        final CompoundTag rawTag = rawBlockEntityTag(entry, block);
        final BlueprintNbtLoadDecision decision = BlueprintNbtPolicies.resolve(blueprint, entry, block, state, rawTag);
        return new BlueprintBuildBlockPayload(
                new BlueprintBlockRef(entry.id(), block.localPos()),
                entry,
                block,
                state,
                rawTag,
                decision
        );
    }

    private static @Nullable CompoundTag rawBlockEntityTag(final SableBlueprint.SubLevelData entry,
                                                           final SableBlueprint.BlockData block) {
        if (!block.hasBlockEntityData()) {
            return null;
        }
        if (block.blockEntityDataId() < 0 || block.blockEntityDataId() >= entry.blockEntities().size()) {
            return null;
        }

        return entry.blockEntities().get(block.blockEntityDataId()).copy();
    }

    private static ServerSubLevel ensureAllocated(final ServerSubLevelContainer container,
                                                  final SableBlueprint.SubLevelData entry,
                                                  final BlueprintPlacementPlan placementPlan,
                                                  final BlueprintBuildProgress progress) {
        final BlueprintBuildProgress.PlacedSubLevel placed = progress.placedSubLevel(entry.id());
        if (placed != null) {
            final ServerSubLevel subLevel = container.getSubLevel(placed.placedUuid()) instanceof final ServerSubLevel serverSubLevel ? serverSubLevel : null;
            if (subLevel == null || subLevel.isRemoved()) {
                throw new IllegalStateException("Placed blueprint sub-level " + placed.placedUuid() + " is no longer loaded");
            }
            ensureSubLevelLock(subLevel);
            return subLevel;
        }

        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(placementPlan.pose(entry));
        if (entry.name() != null) {
            subLevel.setName(entry.name());
        }
        progress.recordPlacedSubLevel(entry, subLevel.getUniqueId(), placedBlocksOrigin(subLevel, entry.localBounds()));
        ensureSubLevelLock(subLevel);
        return subLevel;
    }

    private static ServerSubLevel requirePlacedSubLevel(final ServerSubLevelContainer container,
                                                        final BlueprintBuildProgress progress,
                                                        final SableBlueprint.SubLevelData entry) {
        final BlueprintBuildProgress.PlacedSubLevel placed = progress.placedSubLevel(entry.id());
        if (placed == null) {
            throw new IllegalStateException("Blueprint sub-level " + entry.id() + " was not allocated");
        }

        final ServerSubLevel subLevel = container.getSubLevel(placed.placedUuid()) instanceof final ServerSubLevel serverSubLevel ? serverSubLevel : null;
        if (subLevel == null || subLevel.isRemoved()) {
            throw new IllegalStateException("Placed blueprint sub-level " + placed.placedUuid() + " is no longer loaded");
        }
        return subLevel;
    }

    private static void ensurePlacedSubLevelLocks(final ServerLevel level,
                                                  final BlueprintBuildProgress progress) {
        if (progress.placedSubLevels().isEmpty()) {
            return;
        }

        final ServerSubLevelContainer container = requireContainer(level);
        for (final BlueprintBuildProgress.PlacedSubLevel placed : progress.placedSubLevels().values()) {
            final SubLevel subLevel = container.getSubLevel(placed.placedUuid());
            if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                throw new IllegalStateException("Placed blueprint sub-level " + placed.placedUuid() + " is no longer loaded");
            }
            ensureSubLevelLock(serverSubLevel);
        }
    }

    private static void ensureSubLevelLock(final ServerSubLevel subLevel) {
        if(subLevel.getMassTracker().getMass() < 0.1){
            return;
        }
        final SubLevelOperationResult result = RuntimeSubLevelStaticService.ensureStatic(subLevel);
        if (!result.success()) {
            throw new IllegalStateException(result.message());
        }
    }

    private static void unlockPlacedSubLevelLocks(final ServerLevel level,
                                                  final BlueprintBuildProgress progress) {
        if (progress.placedSubLevels().isEmpty()) {
            return;
        }

        final ServerSubLevelContainer container = requireContainer(level);
        for (final BlueprintBuildProgress.PlacedSubLevel placed : progress.placedSubLevels().values()) {
            final SubLevel subLevel = container.getSubLevel(placed.placedUuid());
            if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                continue;
            }

            final SubLevelOperationResult result = RuntimeSubLevelStaticService.ensureNonStatic(serverSubLevel);
            if (!result.success()) {
                throw new IllegalStateException(result.message());
            }
        }
    }

    private static void unlockPlacedSubLevelLocksQuietly(final ServerLevel level,
                                                         final BlueprintBuildProgress progress) {
        try {
            unlockPlacedSubLevelLocks(level, progress);
        } catch (final RuntimeException ignored) {
            // Keep the original build status/message when cleanup itself fails.
        }
    }

    private static ServerSubLevelContainer requireContainer(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("No Sable sub-level container is available for this level");
        }
        return container;
    }

    private static BlockPos mappedBlock(final BlueprintBuildProgress progress,
                                        final SableBlueprint.SubLevelData entry,
                                        final BlockPos localPos) {
        final BlueprintBuildProgress.PlacedSubLevel placed = progress.placedSubLevel(entry.id());
        if (placed == null) {
            throw new IllegalStateException("Blueprint sub-level " + entry.id() + " was not allocated");
        }

        final BlockPos origin = placed.blockOrigin();
        return new BlockPos(
                origin.getX() + localPos.getX(),
                origin.getY() + localPos.getY(),
                origin.getZ() + localPos.getZ()
        );
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

    private static void ensureChunk(final ServerSubLevel subLevel, final BlockPos storagePos) {
        final ServerLevelPlot plot = subLevel.getPlot();
        final ChunkPos globalChunk = new ChunkPos(storagePos);
        final ChunkPos localChunk = plot.toLocal(globalChunk);
        if (plot.getChunkHolder(localChunk) == null) {
            plot.newEmptyChunk(globalChunk);
        }
    }

    private static void loadBlockEntity(final BlueprintPlaceSession session,
                                        final PlacedBlock placedBlock,
                                        @Nullable final CompoundTag effectiveTag) {
        if (effectiveTag == null) {
            return;
        }

        final SableBlueprint.SubLevelData entry = placedBlock.entry();
        final CompoundTag tag = effectiveTag.copy();
        final BlockPos storagePos = placedBlock.storagePos();
        final ServerLevel level = session.level();
        final LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(storagePos.getX()), SectionPos.blockToSectionCoord(storagePos.getZ()));

        tag.putInt("x", storagePos.getX());
        tag.putInt("y", storagePos.getY());
        tag.putInt("z", storagePos.getZ());

        BlockEntity blockEntity = level.getBlockEntity(storagePos);
        if (blockEntity == null) {
            blockEntity = BlockEntity.loadStatic(storagePos, placedBlock.payload().state(), tag, level.registryAccess());
            if (blockEntity != null) {
                chunk.setBlockEntity(blockEntity);
            }
        }

        if (blockEntity == null) {
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
                placedBlock.payload().state()
        );

        SableBlueprintMapperRegistry.beforeLoadBlockEntity(context, blockEntity, tag);
        tag.putInt("x", storagePos.getX());
        tag.putInt("y", storagePos.getY());
        tag.putInt("z", storagePos.getZ());
        blockEntity.loadWithComponents(tag, level.registryAccess());

        final BlockEntity loadedBlockEntity = blockEntity;
        final CompoundTag loadedTag = tag.copy();
        session.deferAfterBlockEntities(() -> SableBlueprintMapperRegistry.afterLoadBlockEntity(context, loadedBlockEntity, loadedTag));
    }

    private static void placeEntities(final BlueprintPlaceSession session,
                                      final SableBlueprint blueprint,
                                      final BlueprintBuildProgress progress) {
        for (final SableBlueprint.SubLevelData entry : blueprint.subLevels()) {
            final ServerSubLevel subLevel = session.placedSubLevel(entry.id());
            if (subLevel == null) {
                throw new IllegalStateException("Blueprint sub-level " + entry.id() + " was not allocated");
            }

            for (final SableBlueprint.EntityData data : entry.entities()) {
                placeEntity(session, entry, subLevel, data);
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
        final BlockPos origin = session.placedBlockOrigin(entry.id());
        if (origin == null) {
            throw new IllegalStateException("No placed block origin for sub-level " + entry.id());
        }

        return new Vector3d(
                origin.getX() + localPos.x(),
                origin.getY() + localPos.y(),
                origin.getZ() + localPos.z()
        );
    }

    private static void writeEntityPos(final CompoundTag tag, final Vector3dc pos) {
        final ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(pos.x()));
        posTag.add(DoubleTag.valueOf(pos.y()));
        posTag.add(DoubleTag.valueOf(pos.z()));
        tag.put("Pos", posTag);
    }

    private static void notifyPlacedBlocks(final ServerLevel level, final List<PlacedBlock> placedBlocks) {
        for (final PlacedBlock placedBlock : placedBlocks) {
            final BlockPos pos = placedBlock.storagePos();
            final LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
            SubLevelAssemblyHelper.markAndNotifyBlock(level, pos, chunk, placedBlock.oldState(), placedBlock.payload().state(), FINAL_BLOCK_UPDATE_FLAGS, BLOCK_UPDATE_RECURSION_LIMIT);
        }
    }

    private static void notifyClientBlockChange(final ServerLevel level,
                                                final BlockPos pos,
                                                final LevelChunk chunk,
                                                final BlockState oldState,
                                                final BlockState newState) {
        SubLevelAssemblyHelper.markAndNotifyBlock(level, pos, chunk, oldState, newState, CLIENT_BLOCK_UPDATE_FLAGS, BLOCK_UPDATE_RECURSION_LIMIT);
    }

    private record BlockPhaseOutcome(@Nullable BlueprintBuildStepResult result, int affectedBlocks) {
    }

    private record PlacedBlock(SableBlueprint.SubLevelData entry,
                               SableBlueprint.BlockData block,
                               BlueprintBuildBlockPayload payload,
                               BlueprintPlacedBlock view,
                               ServerSubLevel subLevel,
                               BlockPos storagePos,
                               BlockState oldState) {
    }
}

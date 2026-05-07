package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildPhase;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperation;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.*;

/**
 * Persistent cursor and mapping state for one incremental survival blueprint build.
 */
public final class BlueprintBuildProgress {
    private static final int VERSION = 1;
    private BlueprintBuildPhase phase = BlueprintBuildPhase.DECODE;
    private int currentSubLevelIndex;
    private int currentBlockIndex;
    private boolean commitCostConsumed;
    private final Map<Integer, PlacedSubLevel> placedSubLevels = new LinkedHashMap<>();
    private final Map<UUID, UUID> allocatedUuidMap = new LinkedHashMap<>();
    private final Set<String> appliedOperationKeys = new LinkedHashSet<>();
    private final Set<SkippedBlock> skippedBlocks = new LinkedHashSet<>();
    private List<BlueprintPostProcessOperation> postProcessOperations = List.of();
    @Nullable
    private PlacementAnchor placementAnchor;

    public BlueprintBuildPhase phase() {
        return this.phase;
    }

    public void setPhase(final BlueprintBuildPhase phase) {
        this.phase = phase;
    }

    public int currentSubLevelIndex() {
        return this.currentSubLevelIndex;
    }

    public void setCurrentSubLevelIndex(final int currentSubLevelIndex) {
        this.currentSubLevelIndex = Math.max(0, currentSubLevelIndex);
    }

    public int currentBlockIndex() {
        return this.currentBlockIndex;
    }

    public void setCurrentBlockIndex(final int currentBlockIndex) {
        this.currentBlockIndex = Math.max(0, currentBlockIndex);
    }

    public boolean commitCostConsumed() {
        return this.commitCostConsumed;
    }

    public void setCommitCostConsumed(final boolean commitCostConsumed) {
        this.commitCostConsumed = commitCostConsumed;
    }

    public void recordPlacedSubLevel(final SableBlueprint.SubLevelData entry,
                                     final UUID placedUuid,
                                     final BlockPos blockOrigin) {
        this.placedSubLevels.put(entry.id(), new PlacedSubLevel(entry.id(), entry.sourceUuid(), placedUuid, blockOrigin));
    }

    public @Nullable PlacedSubLevel placedSubLevel(final int blueprintId) {
        return this.placedSubLevels.get(blueprintId);
    }

    public Map<Integer, PlacedSubLevel> placedSubLevels() {
        return Map.copyOf(this.placedSubLevels);
    }

    public void recordAllocatedUuid(final UUID sourceUuid, final UUID placedUuid) {
        this.allocatedUuidMap.put(sourceUuid, placedUuid);
    }

    public Map<UUID, UUID> allocatedUuidMap() {
        return Map.copyOf(this.allocatedUuidMap);
    }

    public boolean isOperationApplied(final String stableKey) {
        return this.appliedOperationKeys.contains(stableKey);
    }

    public void markOperationApplied(final String stableKey) {
        this.appliedOperationKeys.add(stableKey);
    }

    public Set<String> appliedOperationKeys() {
        return Set.copyOf(this.appliedOperationKeys);
    }

    public void markBlockSkipped(final int subLevelId, final BlockPos localPos) {
        this.skippedBlocks.add(new SkippedBlock(subLevelId, localPos));
    }

    public boolean isBlockSkipped(final int subLevelId, final BlockPos localPos) {
        return this.skippedBlocks.contains(new SkippedBlock(subLevelId, localPos));
    }

    public Set<SkippedBlock> skippedBlocks() {
        return Set.copyOf(this.skippedBlocks);
    }

    public java.util.List<dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperation> postProcessOperations() {
        return java.util.List.copyOf(this.postProcessOperations);
    }

    public void setPostProcessOperations(
            final java.util.List<dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperation> operations) {
        this.postProcessOperations = java.util.List.copyOf(operations);
    }

    public @Nullable PlacementAnchor placementAnchor() {
        return this.placementAnchor;
    }

    public void setPlacementAnchor(final PlacementAnchor placementAnchor) {
        this.placementAnchor = placementAnchor;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        final ListTag subLevels = new ListTag();
        final ListTag uuids = new ListTag();
        final ListTag skippedBlocks = new ListTag();

        tag.putInt("version", VERSION);
        tag.putString("phase", this.phase.name());
        tag.putInt("current_sub_level_index", this.currentSubLevelIndex);
        tag.putInt("current_block_index", this.currentBlockIndex);
        tag.putBoolean("commit_cost_consumed", this.commitCostConsumed);
        if (this.placementAnchor != null) {
            tag.put("placement_anchor", this.placementAnchor.save());
        }

        for (final PlacedSubLevel placed : this.placedSubLevels.values()) {
            final CompoundTag placedTag = new CompoundTag();
            placedTag.putInt("blueprint_id", placed.blueprintId());
            placedTag.putUUID("source_uuid", placed.sourceUuid());
            placedTag.putUUID("placed_uuid", placed.placedUuid());
            placedTag.put("block_origin", SableBlueprint.writeBlockPos(placed.blockOrigin()));
            subLevels.add(placedTag);
        }
        tag.put("placed_sublevels", subLevels);

        for (final Map.Entry<UUID, UUID> entry : this.allocatedUuidMap.entrySet()) {
            final CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("source_uuid", entry.getKey());
            uuidTag.putUUID("placed_uuid", entry.getValue());
            uuids.add(uuidTag);
        }
        tag.put("allocated_uuid_map", uuids);

        final ListTag appliedKeys = new ListTag();
        for (final String key : this.appliedOperationKeys) {
            appliedKeys.add(StringTag.valueOf(key));
        }
        tag.put("applied_operation_keys", appliedKeys);

        for (final SkippedBlock skipped : this.skippedBlocks) {
            final CompoundTag skippedTag = new CompoundTag();
            skippedTag.putInt("sub_level_id", skipped.subLevelId());
            skippedTag.put("local_pos", SableBlueprint.writeBlockPos(skipped.localPos()));
            skippedBlocks.add(skippedTag);
        }
        tag.put("skipped_blocks", skippedBlocks);

        return tag;
    }

    public static BlueprintBuildProgress load(final CompoundTag tag) {
        final BlueprintBuildProgress progress = new BlueprintBuildProgress();
        if (tag.getInt("version") != VERSION) {
            return progress;
        }

        try {
            progress.phase = BlueprintBuildPhase.valueOf(tag.getString("phase"));
        } catch (final IllegalArgumentException ignored) {
            progress.phase = BlueprintBuildPhase.DECODE;
        }
        progress.currentSubLevelIndex = Math.max(0, tag.getInt("current_sub_level_index"));
        progress.currentBlockIndex = Math.max(0, tag.getInt("current_block_index"));
        progress.commitCostConsumed = tag.getBoolean("commit_cost_consumed");
        if (tag.contains("placement_anchor", Tag.TAG_COMPOUND)) {
            progress.placementAnchor = PlacementAnchor.load(tag.getCompound("placement_anchor"));
        }

        final ListTag subLevels = tag.getList("placed_sublevels", Tag.TAG_COMPOUND);
        for (int i = 0; i < subLevels.size(); i++) {
            final CompoundTag placedTag = subLevels.getCompound(i);
            if (!placedTag.hasUUID("source_uuid") || !placedTag.hasUUID("placed_uuid")) {
                continue;
            }

            final int blueprintId = placedTag.getInt("blueprint_id");
            progress.placedSubLevels.put(blueprintId, new PlacedSubLevel(
                    blueprintId,
                    placedTag.getUUID("source_uuid"),
                    placedTag.getUUID("placed_uuid"),
                    SableBlueprint.readBlockPos(placedTag.getCompound("block_origin"))
            ));
        }

        final ListTag uuids = tag.getList("allocated_uuid_map", Tag.TAG_COMPOUND);
        for (int i = 0; i < uuids.size(); i++) {
            final CompoundTag uuidTag = uuids.getCompound(i);
            if (uuidTag.hasUUID("source_uuid") && uuidTag.hasUUID("placed_uuid")) {
                progress.allocatedUuidMap.put(uuidTag.getUUID("source_uuid"), uuidTag.getUUID("placed_uuid"));
            }
        }

        final ListTag appliedKeys = tag.getList("applied_operation_keys", Tag.TAG_STRING);
        for (int i = 0; i < appliedKeys.size(); i++) {
            progress.appliedOperationKeys.add(appliedKeys.getString(i));
        }

        final ListTag skippedBlocks = tag.getList("skipped_blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < skippedBlocks.size(); i++) {
            final CompoundTag skippedTag = skippedBlocks.getCompound(i);
            if (!skippedTag.contains("local_pos", Tag.TAG_COMPOUND)) {
                continue;
            }
            progress.skippedBlocks.add(new SkippedBlock(
                    skippedTag.getInt("sub_level_id"),
                    SableBlueprint.readBlockPos(skippedTag.getCompound("local_pos"))
            ));
        }

        return progress;
    }

    public record PlacedSubLevel(int blueprintId, UUID sourceUuid, UUID placedUuid, BlockPos blockOrigin) {
        public PlacedSubLevel {
            blockOrigin = blockOrigin.immutable();
        }
    }

    public record SkippedBlock(int subLevelId, BlockPos localPos) {
        public SkippedBlock {
            localPos = localPos.immutable();
        }
    }

    public record PlacementAnchor(int basisSubLevelId, Vector3d targetCenter, double padding) {
        public PlacementAnchor {
            targetCenter = new Vector3d(targetCenter);
        }

        @Override
        public Vector3d targetCenter() {
            return new Vector3d(this.targetCenter);
        }

        private CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putInt("basis_sub_level_id", this.basisSubLevelId);
            tag.put("target_center", writeVector(this.targetCenter));
            tag.putDouble("padding", this.padding);
            return tag;
        }

        private static PlacementAnchor load(final CompoundTag tag) {
            return new PlacementAnchor(
                    tag.getInt("basis_sub_level_id"),
                    readVector(tag.getCompound("target_center")),
                    tag.getDouble("padding")
            );
        }

        private static CompoundTag writeVector(final Vector3d vector) {
            final CompoundTag tag = new CompoundTag();
            tag.putDouble("x", vector.x);
            tag.putDouble("y", vector.y);
            tag.putDouble("z", vector.z);
            return tag;
        }

        private static Vector3d readVector(final CompoundTag tag) {
            return new Vector3d(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        }
    }
}

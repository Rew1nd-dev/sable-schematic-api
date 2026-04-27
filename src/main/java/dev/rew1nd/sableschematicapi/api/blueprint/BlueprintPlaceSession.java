package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlueprintPlaceSession {
    private final ServerLevel level;
    private final Vector3d origin;
    private final Int2ObjectOpenHashMap<ServerSubLevel> placedSubLevels = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<BlockPos> placedBlockOrigins = new Int2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<UUID, UUID> subLevelUuidMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<UUID, UUID> allocatedUuidMap = new Object2ObjectOpenHashMap<>();
    private final List<Runnable> afterBlockEntityTasks = new ObjectArrayList<>();
    private final CompoundTag globalExtraData;
    private BlueprintPlacePhase phase = BlueprintPlacePhase.DECODE;

    public BlueprintPlaceSession(final ServerLevel level, final Vector3dc origin, final CompoundTag globalExtraData) {
        this.level = level;
        this.origin = new Vector3d(origin);
        this.globalExtraData = globalExtraData.copy();
    }

    public ServerLevel level() {
        return this.level;
    }

    public Vector3dc origin() {
        return this.origin;
    }

    public BlueprintPlacePhase phase() {
        return this.phase;
    }

    public void setPhase(final BlueprintPlacePhase phase) {
        this.phase = phase;
    }

    public CompoundTag globalExtraData() {
        return this.globalExtraData;
    }

    public void mapSubLevel(final int blueprintId, final UUID sourceUuid, final ServerSubLevel placedSubLevel, final BlockPos placedBlockOrigin) {
        this.placedSubLevels.put(blueprintId, placedSubLevel);
        this.placedBlockOrigins.put(blueprintId, placedBlockOrigin.immutable());
        this.subLevelUuidMap.put(sourceUuid, placedSubLevel.getUniqueId());
    }

    public @Nullable ServerSubLevel placedSubLevel(final int blueprintId) {
        return this.placedSubLevels.get(blueprintId);
    }

    public @Nullable UUID mapSubLevel(final UUID sourceUuid) {
        return this.subLevelUuidMap.get(sourceUuid);
    }

    public @Nullable UUID mapSubLevel(final BlueprintSubLevelRef ref) {
        return this.subLevelUuidMap.get(ref.sourceUuid());
    }

    public @Nullable BlockPos mapBlock(final BlueprintBlockRef ref) {
        final BlockPos origin = this.placedBlockOrigins.get(ref.subLevelId());
        if (origin == null) {
            return null;
        }

        final BlockPos local = ref.localPos();
        return new BlockPos(
                origin.getX() + local.getX(),
                origin.getY() + local.getY(),
                origin.getZ() + local.getZ()
        );
    }

    public UUID allocateMappedUuid(final UUID sourceUuid) {
        return this.allocatedUuidMap.computeIfAbsent(sourceUuid, ignored -> UUID.randomUUID());
    }

    public void deferAfterBlockEntities(final Runnable task) {
        this.afterBlockEntityTasks.add(task);
    }

    public void runAfterBlockEntityTasks() {
        for (final Runnable task : this.afterBlockEntityTasks) {
            task.run();
        }
        this.afterBlockEntityTasks.clear();
    }

    public Map<UUID, UUID> subLevelUuidMap() {
        return Map.copyOf(this.subLevelUuidMap);
    }
}

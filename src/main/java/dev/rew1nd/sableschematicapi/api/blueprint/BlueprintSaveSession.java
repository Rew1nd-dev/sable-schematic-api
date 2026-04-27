package dev.rew1nd.sableschematicapi.api.blueprint;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BlueprintSaveSession {
    private final ServerLevel level;
    private final Vector3d rootOrigin;
    private final BoundingBox3d rootBounds;
    private final List<SubLevelSaveFrame> frames = new ObjectArrayList<>();
    private final Object2IntOpenHashMap<UUID> sourceSubLevelIds = new Object2IntOpenHashMap<>();
    private final CompoundTag globalExtraData = new CompoundTag();
    private BlueprintSavePhase phase = BlueprintSavePhase.SELECT_SUBLEVELS;

    public BlueprintSaveSession(final ServerLevel level, final Vector3dc rootOrigin, final BoundingBox3d rootBounds) {
        this.level = level;
        this.rootOrigin = new Vector3d(rootOrigin);
        this.rootBounds = new BoundingBox3d(rootBounds);
        this.sourceSubLevelIds.defaultReturnValue(-1);
    }

    public void addFrame(final SubLevelSaveFrame frame) {
        this.frames.add(frame);
        this.sourceSubLevelIds.put(frame.sourceUuid(), frame.blueprintId());
    }

    public ServerLevel level() {
        return this.level;
    }

    public BlueprintSavePhase phase() {
        return this.phase;
    }

    public void setPhase(final BlueprintSavePhase phase) {
        this.phase = phase;
    }

    public Vector3dc rootOrigin() {
        return this.rootOrigin;
    }

    public BoundingBox3d rootBounds() {
        return new BoundingBox3d(this.rootBounds);
    }

    public List<SubLevelSaveFrame> frames() {
        return List.copyOf(this.frames);
    }

    public CompoundTag globalExtraData() {
        return this.globalExtraData;
    }

    public Optional<BlueprintBlockRef> blockRef(final BlockPos sourceStoragePos) {
        for (final SubLevelSaveFrame frame : this.frames) {
            if (frame.contains(sourceStoragePos)) {
                return Optional.of(new BlueprintBlockRef(frame.blueprintId(), frame.toLocal(sourceStoragePos)));
            }
        }

        return Optional.empty();
    }

    public Optional<BlueprintSubLevelRef> subLevelRef(final UUID sourceUuid) {
        final int id = this.sourceSubLevelIds.getInt(sourceUuid);
        if (id < 0) {
            return Optional.empty();
        }

        return Optional.of(new BlueprintSubLevelRef(id, sourceUuid));
    }

    public @Nullable Integer blueprintId(final UUID sourceUuid) {
        final int id = this.sourceSubLevelIds.getInt(sourceUuid);
        return id >= 0 ? id : null;
    }
}

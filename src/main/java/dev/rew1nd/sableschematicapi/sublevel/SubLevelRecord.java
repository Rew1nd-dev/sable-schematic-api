package dev.rew1nd.sableschematicapi.sublevel;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SubLevelRecord(ResourceKey<Level> dimension,
                             UUID uuid,
                             @Nullable String name,
                             SubLevelLoadState loadState,
                             Pose3d pose,
                             BoundingBox3d bounds,
                             @Nullable GlobalSavedSubLevelPointer pointer,
                             List<UUID> dependencies) {
    public SubLevelRecord {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(loadState, "loadState");
        pose = new Pose3d(Objects.requireNonNull(pose, "pose"));
        bounds = new BoundingBox3d(Objects.requireNonNull(bounds, "bounds"));
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    @Override
    public Pose3d pose() {
        return new Pose3d(this.pose);
    }

    @Override
    public BoundingBox3d bounds() {
        return new BoundingBox3d(this.bounds);
    }

    public String displayName() {
        return this.name == null || this.name.isBlank() ? this.uuid.toString() : this.name;
    }

    public boolean loaded() {
        return this.loadState == SubLevelLoadState.LOADED;
    }
}

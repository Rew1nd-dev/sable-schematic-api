package dev.rew1nd.sableschematicapi.survival.physicsmanager.client;

import dev.rew1nd.sableschematicapi.survival.physicsmanager.PhysicsManagerAlgorithms;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record PhysicsManagerBodyEntry(UUID uuid,
                                      UUID groupId,
                                      String signature,
                                      String dimension,
                                      String name,
                                      boolean loaded,
                                      boolean staticLocked,
                                      double x,
                                      double y,
                                      double z,
                                      double minX,
                                      double minY,
                                      double minZ,
                                      double maxX,
                                      double maxY,
                                      double maxZ,
                                      double distance) {
    public static PhysicsManagerBodyEntry read(final CompoundTag tag) {
        return new PhysicsManagerBodyEntry(
                tag.getUUID("uuid"),
                tag.getUUID("group_id"),
                tag.getString("signature"),
                tag.getString("dimension"),
                tag.getString("name"),
                "LOADED".equals(tag.getString("load_state")),
                tag.getBoolean("static"),
                tag.getDouble("x"),
                tag.getDouble("y"),
                tag.getDouble("z"),
                tag.getDouble("min_x"),
                tag.getDouble("min_y"),
                tag.getDouble("min_z"),
                tag.getDouble("max_x"),
                tag.getDouble("max_y"),
                tag.getDouble("max_z"),
                tag.getDouble("distance")
        );
    }

    public String displayName() {
        return this.name == null || this.name.isBlank() ? shortId(this.uuid) : this.name;
    }

    public String stateLabel() {
        return this.loaded ? "loaded" : "stored";
    }

    public String distanceLabel() {
        return this.distance < 0.0D ? "—" : "%.1f m".formatted(this.distance);
    }

    public double volume() {
        return PhysicsManagerAlgorithms.volume(
                this.minX,
                this.minY,
                this.minZ,
                this.maxX,
                this.maxY,
                this.maxZ
        );
    }

    public static String shortId(final UUID id) {
        final String value = id == null ? "--------" : id.toString();
        return value.substring(0, Math.min(8, value.length()));
    }
}

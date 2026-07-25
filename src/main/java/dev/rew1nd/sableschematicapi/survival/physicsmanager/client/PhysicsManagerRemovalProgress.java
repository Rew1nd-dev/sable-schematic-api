package dev.rew1nd.sableschematicapi.survival.physicsmanager.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record PhysicsManagerRemovalProgress(boolean active,
                                            Set<UUID> targets,
                                            int total,
                                            int completed,
                                            UUID current,
                                            int removed,
                                            int missing,
                                            int loadFailures,
                                            int failures,
                                            int unavailableDimensions,
                                            String message) {
    public static final PhysicsManagerRemovalProgress IDLE =
            new PhysicsManagerRemovalProgress(false, Set.of(), 0, 0, null, 0, 0, 0, 0, 0, "");

    public static PhysicsManagerRemovalProgress read(final CompoundTag root) {
        if (!root.contains("removal", Tag.TAG_COMPOUND)) {
            return IDLE;
        }
        final CompoundTag tag = root.getCompound("removal");
        final Set<UUID> targets = new LinkedHashSet<>();
        final ListTag list = tag.getList("targets", Tag.TAG_COMPOUND);
        for (final Tag value : list) {
            if (value instanceof final CompoundTag entry && entry.hasUUID("uuid")) {
                targets.add(entry.getUUID("uuid"));
            }
        }
        return new PhysicsManagerRemovalProgress(
                tag.getBoolean("active"),
                Set.copyOf(targets),
                tag.getInt("total"),
                tag.getInt("completed"),
                tag.hasUUID("current") ? tag.getUUID("current") : null,
                tag.getInt("removed"),
                tag.getInt("missing"),
                tag.getInt("load_failures"),
                tag.getInt("failures"),
                tag.getInt("unavailable_dimensions"),
                tag.getString("message")
        );
    }
}

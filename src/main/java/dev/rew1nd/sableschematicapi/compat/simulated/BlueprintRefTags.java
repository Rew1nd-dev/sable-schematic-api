package dev.rew1nd.sableschematicapi.compat.simulated;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Optional;

final class BlueprintRefTags {
    private static final String SUB_LEVEL_ID = "sub_level_id";
    private static final String LOCAL_POS = "local_pos";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";

    private BlueprintRefTags() {
    }

    static CompoundTag write(final BlueprintBlockRef ref) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(SUB_LEVEL_ID, ref.subLevelId());
        tag.put(LOCAL_POS, writeBlockPos(ref.localPos()));
        return tag;
    }

    static Optional<BlueprintBlockRef> read(final CompoundTag tag, final String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        final CompoundTag ref = tag.getCompound(key);
        if (!ref.contains(SUB_LEVEL_ID, Tag.TAG_INT) || !ref.contains(LOCAL_POS, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        return Optional.of(new BlueprintBlockRef(ref.getInt(SUB_LEVEL_ID), readBlockPos(ref.getCompound(LOCAL_POS))));
    }

    static CompoundTag writeVector(final Vector3dc vector) {
        final CompoundTag tag = new CompoundTag();
        tag.putDouble(X, vector.x());
        tag.putDouble(Y, vector.y());
        tag.putDouble(Z, vector.z());
        return tag;
    }

    static Optional<Vector3d> readVector(final CompoundTag tag, final String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        return Optional.of(readVector(tag.getCompound(key)));
    }

    static Vector3d readVector(final CompoundTag tag) {
        return new Vector3d(tag.getDouble(X), tag.getDouble(Y), tag.getDouble(Z));
    }

    private static CompoundTag writeBlockPos(final BlockPos pos) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(X, pos.getX());
        tag.putInt(Y, pos.getY());
        tag.putInt(Z, pos.getZ());
        return tag;
    }

    private static BlockPos readBlockPos(final CompoundTag tag) {
        return new BlockPos(tag.getInt(X), tag.getInt(Y), tag.getInt(Z));
    }
}

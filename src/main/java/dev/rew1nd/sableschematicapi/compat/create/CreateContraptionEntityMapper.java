package dev.rew1nd.sableschematicapi.compat.create;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintEntityPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintEntitySaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEntityMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

final class CreateContraptionEntityMapper implements SableBlueprintEntityMapper {
    private static final String CONTRAPTION = "Contraption";
    private static final String ANCHOR = "Anchor";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";

    @Override
    public @Nullable CompoundTag save(final BlueprintEntitySaveContext context, @Nullable final CompoundTag defaultTag) {
        if (defaultTag == null) {
            return null;
        }

        final CompoundTag tag = defaultTag.copy();
        final @Nullable BlockPos anchor = readAnchor(tag);
        if (anchor == null) {
            return tag;
        }

        final BlockPos origin = context.blocksOrigin();
        writeAnchor(tag, new BlockPos(
                anchor.getX() - origin.getX(),
                anchor.getY() - origin.getY(),
                anchor.getZ() - origin.getZ()
        ));
        return tag;
    }

    @Override
    public @Nullable CompoundTag beforeCreateEntity(final BlueprintEntityPlaceContext context, final CompoundTag tag) {
        final @Nullable BlockPos localAnchor = readAnchor(tag);
        final @Nullable BlockPos origin = context.placedBlocksOrigin();
        if (localAnchor == null || origin == null) {
            return tag;
        }

        final CompoundTag copy = tag.copy();
        writeAnchor(copy, new BlockPos(
                origin.getX() + localAnchor.getX(),
                origin.getY() + localAnchor.getY(),
                origin.getZ() + localAnchor.getZ()
        ));
        return copy;
    }

    private static @Nullable BlockPos readAnchor(final CompoundTag tag) {
        if (!tag.contains(CONTRAPTION, Tag.TAG_COMPOUND)) {
            return null;
        }

        final CompoundTag contraption = tag.getCompound(CONTRAPTION);
        if (!contraption.contains(ANCHOR, Tag.TAG_COMPOUND)) {
            return null;
        }

        final CompoundTag anchor = contraption.getCompound(ANCHOR);
        if (!anchor.contains(X, Tag.TAG_INT) || !anchor.contains(Y, Tag.TAG_INT) || !anchor.contains(Z, Tag.TAG_INT)) {
            return null;
        }

        return new BlockPos(anchor.getInt(X), anchor.getInt(Y), anchor.getInt(Z));
    }

    private static void writeAnchor(final CompoundTag tag, final BlockPos pos) {
        final CompoundTag anchor = tag.getCompound(CONTRAPTION).getCompound(ANCHOR);
        anchor.putInt(X, pos.getX());
        anchor.putInt(Y, pos.getY());
        anchor.putInt(Z, pos.getZ());
    }
}

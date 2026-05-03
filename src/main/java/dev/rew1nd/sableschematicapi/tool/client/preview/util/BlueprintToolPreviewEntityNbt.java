package dev.rew1nd.sableschematicapi.tool.client.preview.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import org.joml.Vector3dc;

public final class BlueprintToolPreviewEntityNbt {
    private BlueprintToolPreviewEntityNbt() {
    }

    public static void writeEntityPos(final CompoundTag tag, final Vector3dc pos) {
        final ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(pos.x()));
        posTag.add(DoubleTag.valueOf(pos.y()));
        posTag.add(DoubleTag.valueOf(pos.z()));
        tag.put("Pos", posTag);
    }
}

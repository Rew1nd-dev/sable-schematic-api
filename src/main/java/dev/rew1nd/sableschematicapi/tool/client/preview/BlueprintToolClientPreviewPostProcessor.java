package dev.rew1nd.sableschematicapi.tool.client.preview;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreviewNbt;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;

public final class BlueprintToolClientPreviewPostProcessor {
    private BlueprintToolClientPreviewPostProcessor() {
    }

    public static byte[] attachClientPreview(final byte[] data) throws IOException {
        final CompoundTag tag = SableBlueprintPreviewNbt.read(data);
        SableBlueprintPreviewNbt.putPreview(tag, null);

        final SableBlueprint blueprint = SableBlueprint.load(tag);
        final SableBlueprintPreview preview = BlueprintToolPreviewRenderer.generate(blueprint);
        SableBlueprintPreviewNbt.putPreview(tag, preview);
        return SableBlueprintPreviewNbt.write(tag);
    }

    public static byte[] stripPreview(final byte[] data) throws IOException {
        return SableBlueprintPreviewNbt.stripPreview(data);
    }
}

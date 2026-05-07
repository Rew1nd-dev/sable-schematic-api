package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreviewNbt;
import dev.rew1nd.sableschematicapi.network.SableSchematicApiPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;

/**
 * Server-side upload handling for blueprint table payloads.
 */
public final class BlueprintTableUploadHandler {
    public static final ResourceLocation UPLOAD_ACTION = SableSchematicApi.id("blueprint_table/upload");
    private static final double MAX_TABLE_DISTANCE_SQUARED = 8.0 * 8.0;

    private BlueprintTableUploadHandler() {
    }

    /**
     * Called on the server to find the nearest blueprint table and upload the payload.
     */
    public static boolean handleUpload(final ServerPlayer player,
                                       final String name,
                                       final byte[] data,
                                       final byte[] hash) {
        if (data.length == 0) {
            return false;
        }

        final byte[] strippedData;
        try {
            strippedData = SableBlueprintPreviewNbt.stripPreview(data);
        } catch (final IOException e) {
            return false;
        }

        if (strippedData.length == 0 || strippedData.length > SableSchematicApiPackets.MAX_BLUEPRINT_BYTES) {
            return false;
        }

        final BlueprintTableBlockEntity table = findNearbyTable(player);
        if (table == null) {
            return false;
        }

        return table.uploadBlueprint(
                player.getName().getString(), name, strippedData, BlueprintPayloads.sha256(strippedData));
    }

    private static BlueprintTableBlockEntity findNearbyTable(final ServerPlayer player) {
        final Level level = player.level();
        final BlockPos playerPos = player.blockPosition();
        final int range = 6;

        for (final BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-range, -range, -range),
                playerPos.offset(range, range, range))) {
            if (playerPos.distSqr(pos) > MAX_TABLE_DISTANCE_SQUARED) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof final BlueprintTableBlockEntity table) {
                return table;
            }
        }
        return null;
    }
}

package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.network.SableSchematicApiPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Client-side file read + server-bound upload for blueprint table.
 */
public final class BlueprintTableUploadHandler {
    public static final ResourceLocation UPLOAD_ACTION = SableSchematicApi.id("blueprint_table/upload");
    private static final double MAX_TABLE_DISTANCE_SQUARED = 8.0 * 8.0;

    private BlueprintTableUploadHandler() {
    }

    /**
     * Called on the client when the player clicks Upload.
     * Reads the local blueprint file and sends it to the server.
     */
    public static void requestUpload(final String fileName) {
        try {
            BlueprintTableClientData.refreshLocalFiles();
            final byte[] data = BlueprintTableClientData.readLocalFile(fileName);
            final byte[] hash = sha256(data);

            final CompoundTag tag = new CompoundTag();
            tag.putString("name", fileName);
            tag.putByteArray("data", data);
            tag.putByteArray("hash", hash);

            PacketDistributor.sendToServer(new dev.rew1nd.sableschematicapi.network.BlueprintToolActionPayload(UPLOAD_ACTION, tag));
        } catch (final IOException e) {
            // Silently fail
        }
    }

    /**
     * Called on the server to find the nearest blueprint table and upload the payload.
     */
    public static boolean handleUpload(final ServerPlayer player,
                                       final String name,
                                       final byte[] data,
                                       final byte[] hash) {
        if (data.length == 0 || data.length > SableSchematicApiPackets.MAX_BLUEPRINT_BYTES) {
            return false;
        }

        final BlueprintTableBlockEntity table = findNearbyTable(player);
        if (table == null) {
            return false;
        }

        return table.uploadBlueprint(
                player.getName().getString(), name, data, hash);
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

    private static byte[] sha256(final byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

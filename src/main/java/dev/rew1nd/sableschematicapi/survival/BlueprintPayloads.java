package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintDecodeResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility codec for blueprint payloads intended to be stored on survival items or block entities.
 */
public final class BlueprintPayloads {
    private BlueprintPayloads() {
    }

    public static Payload encode(final SableBlueprint blueprint) throws IOException {
        final byte[] data = writeCompressed(blueprint);
        return new Payload(data, sha256(data), BlueprintSummary.of(blueprint));
    }

    public static byte[] writeCompressed(final SableBlueprint blueprint) throws IOException {
        try (final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(blueprint.save(), stream);
            return stream.toByteArray();
        }
    }

    public static SableBlueprint readCompressed(final byte[] data) throws IOException {
        return readCompressedWithDiagnostics(data).blueprint();
    }

    public static SableBlueprintDecodeResult readCompressedWithDiagnostics(final byte[] data) throws IOException {
        try (final ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            final CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            if (tag == null) {
                throw new IOException("Blueprint payload is empty.");
            }
            return SableBlueprint.loadWithDiagnostics(tag);
        }
    }

    public static byte[] sha256(final byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String hashString(final byte[] hash) {
        return HexFormat.of().formatHex(hash);
    }

    public record Payload(byte[] compressedBlueprint, byte[] sha256, BlueprintSummary summary) {
        public Payload {
            compressedBlueprint = compressedBlueprint.clone();
            sha256 = sha256.clone();
        }

        @Override
        public byte[] compressedBlueprint() {
            return this.compressedBlueprint.clone();
        }

        @Override
        public byte[] sha256() {
            return this.sha256.clone();
        }
    }
}

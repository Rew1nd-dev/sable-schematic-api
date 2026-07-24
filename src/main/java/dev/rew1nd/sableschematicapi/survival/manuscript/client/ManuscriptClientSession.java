package dev.rew1nd.sableschematicapi.survival.manuscript.client;

import dev.rew1nd.sableschematicapi.network.ManuscriptControlPayload;
import dev.rew1nd.sableschematicapi.network.ManuscriptDownloadChunkPayload;
import dev.rew1nd.sableschematicapi.network.ManuscriptResultPayload;
import dev.rew1nd.sableschematicapi.network.ManuscriptUploadChunkPayload;
import dev.rew1nd.sableschematicapi.survival.BlueprintPayloads;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintAlias;
import dev.rew1nd.sableschematicapi.survival.manuscript.QuarterTurn;
import dev.rew1nd.sableschematicapi.survival.manuscript.client.ui.BlueprintManuscriptUiRenderer;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/** Client-side transient state for one exact-hand manuscript screen. */
public final class ManuscriptClientSession {
    private static final int MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024;

    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static UUID token;
    private static ScreenState state = ScreenState.EMPTY;
    private static RemoteBlueprintPreviewCache.Entry details;
    private static PendingUpload pendingUpload;
    private static PendingDownload pendingDownload;
    private static OverwriteRequest overwriteRequest;
    private static int revision;

    private ManuscriptClientSession() {
    }

    public static void register() {
        RemoteBlueprintPreviewCache.clear();
    }

    public static void begin(final InteractionHand requestedHand) {
        resetSession(false);
        hand = requestedHand == null ? InteractionHand.MAIN_HAND : requestedHand;
        PacketDistributor.sendToServer(new ManuscriptControlPayload(
                "begin",
                null,
                hand == InteractionHand.OFF_HAND ? "off_hand" : "main_hand",
                new CompoundTag()
        ));
    }

    public static void upload(final BlueprintToolLocalFiles.Entry entry) {
        if (token == null || entry == null || state.filled()) {
            return;
        }
        try {
            final byte[] bytes = BlueprintToolLocalFiles.read(entry);
            final String sha256 = BlueprintPayloads.hashString(BlueprintPayloads.sha256(bytes));
            pendingUpload = new PendingUpload(entry.name(), bytes, sha256, null);
            final CompoundTag data = new CompoundTag();
            data.putString("name", entry.name());
            data.putInt("total_bytes", bytes.length);
            data.putString("sha256", sha256);
            send("begin_upload", data);
        } catch (final IOException exception) {
            show("Could not read the selected blueprint.", ChatFormatting.RED);
        }
    }

    public static void requestDetails() {
        if (token == null || state.alias() == null) {
            return;
        }
        final RemoteBlueprintPreviewCache.Entry cached = RemoteBlueprintPreviewCache.latest(state.alias());
        final CompoundTag data = new CompoundTag();
        data.putString("cached_sha256", cached == null ? "" : cached.sha256());
        send("details", data);
    }

    public static void rotateLeft() {
        send("rotate_left", new CompoundTag());
    }

    public static void rotateRight() {
        send("rotate_right", new CompoundTag());
    }

    public static void discard() {
        send("discard", new CompoundTag());
    }

    public static void confirmOverwrite() {
        if (overwriteRequest == null) {
            return;
        }
        final CompoundTag data = new CompoundTag();
        data.putUUID("transfer_id", overwriteRequest.transferId());
        data.putString("expected_target_sha256", overwriteRequest.expectedTargetSha256());
        send("confirm_overwrite", data);
    }

    public static void cancelUpload() {
        send("cancel_upload", new CompoundTag());
    }

    public static void close() {
        if (token != null) {
            send("close", new CompoundTag());
        }
        resetSession(false);
    }

    public static void handleResult(final ManuscriptResultPayload payload) {
        Minecraft.getInstance().execute(() -> handleResultOnClient(payload));
    }

    public static void handleDownloadChunk(final ManuscriptDownloadChunkPayload payload) {
        Minecraft.getInstance().execute(() -> acceptDownloadChunk(payload));
    }

    public static ScreenState state() {
        return state;
    }

    public static @Nullable RemoteBlueprintPreviewCache.Entry details() {
        return details;
    }

    public static @Nullable OverwriteRequest overwriteRequest() {
        return overwriteRequest;
    }

    public static int revision() {
        return revision;
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        resetSession(true);
    }

    private static void handleResultOnClient(final ManuscriptResultPayload payload) {
        if ("begin".equals(payload.action())) {
            if (!payload.success() || payload.token() == null) {
                show("Could not open the blueprint manuscript.", ChatFormatting.RED);
                return;
            }
            token = payload.token();
            state = ScreenState.from(payload.data());
            details = null;
            overwriteRequest = null;
            revision++;
            BlueprintManuscriptUiRenderer.open();
            if (state.filled()) {
                requestDetails();
            }
            return;
        }
        if (token == null || !token.equals(payload.token())) {
            return;
        }
        switch (payload.action()) {
            case "begin_upload" -> {
                if (payload.success() && pendingUpload != null && payload.data().hasUUID("transfer_id")) {
                    final UUID transferId = payload.data().getUUID("transfer_id");
                    pendingUpload = pendingUpload.withTransferId(transferId);
                    sendUploadChunks(pendingUpload);
                }
            }
            case "overwrite_required" -> {
                if (payload.data().hasUUID("transfer_id")) {
                    overwriteRequest = new OverwriteRequest(
                            payload.data().getUUID("transfer_id"),
                            payload.data().getString("expected_target_sha256")
                    );
                    revision++;
                }
            }
            case "upload_complete" -> {
                if (payload.success()) {
                    state = ScreenState.from(payload.data());
                    pendingUpload = null;
                    overwriteRequest = null;
                    revision++;
                    requestDetails();
                }
            }
            case "cancel_upload" -> {
                pendingUpload = null;
                overwriteRequest = null;
                revision++;
            }
            case "rotate" -> {
                if (payload.success()) {
                    state = ScreenState.from(payload.data());
                    revision++;
                }
            }
            case "discard" -> {
                if (payload.success()) {
                    state = ScreenState.EMPTY;
                    details = null;
                    overwriteRequest = null;
                    revision++;
                }
            }
            case "details_not_modified" -> {
                if (state.alias() != null) {
                    details = RemoteBlueprintPreviewCache.get(state.alias(), payload.data().getString("sha256"));
                    revision++;
                }
            }
            default -> {
            }
        }
        if (!payload.success() && !"overwrite_required".equals(payload.action())) {
            show("Blueprint manuscript: " + payload.reason(), ChatFormatting.YELLOW);
        }
    }

    private static void sendUploadChunks(final PendingUpload upload) {
        final int chunkSize = ManuscriptUploadChunkPayload.MAX_CHUNK_BYTES;
        final int chunks = (upload.bytes().length + chunkSize - 1) / chunkSize;
        for (int index = 0; index < chunks; index++) {
            final int start = index * chunkSize;
            final int end = Math.min(upload.bytes().length, start + chunkSize);
            PacketDistributor.sendToServer(new ManuscriptUploadChunkPayload(
                    token,
                    upload.transferId(),
                    index,
                    Arrays.copyOfRange(upload.bytes(), start, end)
            ));
        }
    }

    private static void acceptDownloadChunk(final ManuscriptDownloadChunkPayload payload) {
        if (token == null || !token.equals(payload.token())
                || payload.totalBytes() <= 0 || payload.totalBytes() > MAX_DOWNLOAD_BYTES
                || payload.totalChunks() <= 0 || payload.totalChunks() > 1024) {
            return;
        }
        if (pendingDownload == null || !pendingDownload.transferId().equals(payload.transferId())) {
            if (payload.index() != 0) {
                return;
            }
            pendingDownload = new PendingDownload(
                    payload.transferId(),
                    payload.totalChunks(),
                    payload.totalBytes(),
                    payload.sha256(),
                    new byte[payload.totalBytes()],
                    0,
                    0
            );
        }
        if (payload.index() != pendingDownload.nextIndex()) {
            pendingDownload = null;
            return;
        }
        final byte[] chunk = payload.data();
        if (pendingDownload.offset() + chunk.length > pendingDownload.bytes().length) {
            pendingDownload = null;
            return;
        }
        System.arraycopy(chunk, 0, pendingDownload.bytes(), pendingDownload.offset(), chunk.length);
        pendingDownload = pendingDownload.advance(chunk.length);
        if (pendingDownload.nextIndex() == pendingDownload.totalChunks()
                && pendingDownload.offset() == pendingDownload.totalBytes()) {
            finishDownload(pendingDownload);
            pendingDownload = null;
        }
    }

    private static void finishDownload(final PendingDownload download) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(download.bytes())) {
            final CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.create(MAX_DOWNLOAD_BYTES));
            final ScreenState downloadedState = ScreenState.from(tag);
            if (downloadedState.alias() == null || state.alias() == null
                    || !downloadedState.alias().equals(state.alias())) {
                return;
            }
            details = RemoteBlueprintPreviewCache.put(state.alias(), download.sha256(), tag);
            revision++;
        } catch (final IOException | RuntimeException exception) {
            show("Could not decode the manuscript preview.", ChatFormatting.RED);
        }
    }

    private static void send(final String action, final CompoundTag data) {
        if (token != null) {
            PacketDistributor.sendToServer(new ManuscriptControlPayload(action, token, "", data));
        }
    }

    private static void resetSession(final boolean clearCache) {
        token = null;
        state = ScreenState.EMPTY;
        details = null;
        pendingUpload = null;
        pendingDownload = null;
        overwriteRequest = null;
        revision++;
        if (clearCache) {
            RemoteBlueprintPreviewCache.clear();
        }
    }

    private static void show(final String message, final ChatFormatting color) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(message).withStyle(color),
                    true
            );
        }
    }

    public record ScreenState(boolean filled,
                              @Nullable BlueprintAlias alias,
                              QuarterTurn rotation) {
        private static final ScreenState EMPTY = new ScreenState(false, null, QuarterTurn.DEG_0);

        private static ScreenState from(final CompoundTag tag) {
            if (!tag.getBoolean("filled") || !tag.hasUUID("owner_id")) {
                return EMPTY;
            }
            try {
                return new ScreenState(
                        true,
                        new BlueprintAlias(
                                tag.getUUID("owner_id"),
                                tag.getString("owner_name"),
                                tag.getString("blueprint_name")
                        ),
                        QuarterTurn.fromDegrees(tag.getInt("rotation"))
                );
            } catch (final RuntimeException exception) {
                return EMPTY;
            }
        }
    }

    public record OverwriteRequest(UUID transferId, String expectedTargetSha256) {
    }

    private record PendingUpload(String name, byte[] bytes, String sha256, @Nullable UUID transferId) {
        private PendingUpload {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return this.bytes;
        }

        private PendingUpload withTransferId(final UUID value) {
            return new PendingUpload(this.name, this.bytes, this.sha256, value);
        }
    }

    private record PendingDownload(UUID transferId,
                                   int totalChunks,
                                   int totalBytes,
                                   String sha256,
                                   byte[] bytes,
                                   int nextIndex,
                                   int offset) {
        private PendingDownload advance(final int addedBytes) {
            return new PendingDownload(
                    this.transferId,
                    this.totalChunks,
                    this.totalBytes,
                    this.sha256,
                    this.bytes,
                    this.nextIndex + 1,
                    this.offset + addedBytes
            );
        }
    }
}

package dev.rew1nd.sableschematicapi.survival.manuscript;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.network.ManuscriptControlPayload;
import dev.rew1nd.sableschematicapi.network.ManuscriptDownloadChunkPayload;
import dev.rew1nd.sableschematicapi.network.ManuscriptResultPayload;
import dev.rew1nd.sableschematicapi.network.ManuscriptUploadChunkPayload;
import dev.rew1nd.sableschematicapi.survival.BlueprintPayloads;
import dev.rew1nd.sableschematicapi.survival.camera.CameraConfig;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime-only server manuscript sessions and bounded transfers. */
public final class ManuscriptServerSessions {
    private static final long TIMEOUT_TICKS = 20L * 120L;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ManuscriptServerSessions() {
    }

    public static void handleControl(final ServerPlayer player, final ManuscriptControlPayload payload) {
        if ("begin".equals(payload.action())) {
            begin(player, payload.hand());
            return;
        }
        final Session session = validSession(player, payload.token());
        if (session == null) {
            send(player, payload.action(), false, "session_invalid", null, new CompoundTag());
            return;
        }
        session.touch(player);
        try {
            switch (payload.action()) {
                case "close", "cancel" -> close(player.getUUID());
                case "cancel_upload" -> cancelUpload(player, session);
                case "begin_upload" -> beginUpload(player, session, payload.data());
                case "confirm_overwrite" -> confirmOverwrite(player, session, payload.data());
                case "details" -> sendDetails(player, session, payload.data().getString("cached_sha256"));
                case "rotate_left" -> rotate(player, session, false);
                case "rotate_right" -> rotate(player, session, true);
                case "discard" -> discard(player, session);
                default -> send(player, payload.action(), false, "unknown_action", session.token, new CompoundTag());
            }
        } catch (final IOException | RuntimeException exception) {
            SableSchematicApi.LOGGER.warn("Manuscript action {} failed for {}", payload.action(), player.getScoreboardName(), exception);
            send(player, payload.action(), false, "io_error", session.token, new CompoundTag());
        }
    }

    public static void handleUploadChunk(final ServerPlayer player,
                                         final ManuscriptUploadChunkPayload payload) {
        final Session session = validSession(player, payload.token());
        if (session == null || session.upload == null || !session.upload.transferId.equals(payload.transferId())) {
            send(player, "upload", false, "transfer_invalid", payload.token(), new CompoundTag());
            return;
        }
        session.touch(player);
        final Upload upload = session.upload;
        if (payload.index() != upload.nextChunk || payload.data().length == 0
                || payload.data().length > ManuscriptUploadChunkPayload.MAX_CHUNK_BYTES) {
            failUpload(player, session, "chunk_out_of_order");
            return;
        }
        if (upload.receivedBytes + payload.data().length > upload.totalBytes) {
            failUpload(player, session, "upload_too_large");
            return;
        }
        try (OutputStream output = Files.newOutputStream(upload.staging, StandardOpenOption.APPEND)) {
            output.write(payload.data());
        } catch (final IOException exception) {
            failUpload(player, session, "io_error");
            return;
        }
        upload.receivedBytes += payload.data().length;
        upload.nextChunk++;
        if (upload.receivedBytes == upload.totalBytes) {
            finishUpload(player, session);
        }
    }

    public static void tick(final ServerTickEvent.Post event) {
        final long tick = event.getServer().getTickCount();
        SESSIONS.entrySet().removeIf(entry -> {
            if (tick - entry.getValue().lastActivityTick <= TIMEOUT_TICKS) {
                return false;
            }
            entry.getValue().cleanup();
            return true;
        });
    }

    public static void onServerStopped(final ServerStoppedEvent event) {
        SESSIONS.values().forEach(Session::cleanup);
        SESSIONS.clear();
    }

    public static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        close(event.getEntity().getUUID());
    }

    public static void onLivingDeath(final LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player.getUUID());
        }
    }

    private static void begin(final ServerPlayer player, final String handName) {
        final InteractionHand hand = "off_hand".equals(handName)
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        final ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(SableSchematicApiItems.BLUEPRINT_MANUSCRIPT.get())) {
            send(player, "begin", false, "not_holding_manuscript", null, new CompoundTag());
            return;
        }
        close(player.getUUID());
        final Session session = new Session(UUID.randomUUID(), hand, stack, player.server.getTickCount());
        SESSIONS.put(player.getUUID(), session);
        final CompoundTag data = stateTag(BlueprintManuscriptItem.state(stack));
        send(player, "begin", true, "ok", session.token, data);
    }

    private static void beginUpload(final ServerPlayer player,
                                    final Session session,
                                    final CompoundTag data) throws IOException {
        if (BlueprintManuscriptItem.state(session.stackReference).isFilled()) {
            send(player, "begin_upload", false, "manuscript_filled", session.token, new CompoundTag());
            return;
        }
        final String name = data.getString("name").trim();
        final int totalBytes = data.getInt("total_bytes");
        final String sha256 = data.getString("sha256").toLowerCase(java.util.Locale.ROOT);
        if (name.isBlank() || totalBytes <= 0 || totalBytes > CameraConfig.MAX_MANUSCRIPT_UPLOAD_BYTES.get()
                || !sha256.matches("[0-9a-f]{64}")) {
            send(player, "begin_upload", false, "upload_invalid", session.token, new CompoundTag());
            return;
        }
        if (session.upload != null) {
            session.upload.cleanup();
        }
        final UUID transferId = UUID.randomUUID();
        final BlueprintAlias alias = new BlueprintAlias(
                player.getUUID(),
                player.getGameProfile().getName(),
                name
        );
        final String targetSha = BlueprintWorldStore.currentSha256(player.server, alias);
        final Path staging = BlueprintWorldStore.newStagingPath(player.server, player.getUUID(), transferId);
        session.upload = new Upload(transferId, alias, staging, totalBytes, sha256, targetSha);
        SableSchematicApi.LOGGER.info(
                "Started manuscript upload {} for {} ({} bytes, sha256={})",
                transferId,
                player.getScoreboardName(),
                totalBytes,
                sha256.substring(0, 12)
        );
        final CompoundTag result = new CompoundTag();
        result.putUUID("transfer_id", transferId);
        send(player, "begin_upload", true, "ready", session.token, result);
    }

    private static void finishUpload(final ServerPlayer player, final Session session) {
        final Upload upload = session.upload;
        try {
            final String actualSha = BlueprintWorldStore.sha256(upload.staging);
            if (!actualSha.equals(upload.uploadSha256)) {
                failUpload(player, session, "sha256_mismatch");
                return;
            }
            BlueprintWorldStore.inspect(upload.staging);
            final String currentTargetSha = BlueprintWorldStore.currentSha256(player.server, upload.alias);
            if (currentTargetSha.isEmpty()) {
                commitAndFill(player, session);
                return;
            }
            if (currentTargetSha.equals(upload.uploadSha256)) {
                BlueprintWorldStore.deleteStaging(upload.staging);
                fill(player, session, upload.alias);
                session.upload = null;
                send(player, "upload_complete", true, "unchanged", session.token, stateTag(BlueprintManuscriptItem.state(session.stackReference)));
                return;
            }
            upload.expectedTargetSha256 = currentTargetSha;
            SableSchematicApi.LOGGER.info(
                    "Manuscript upload {} requires overwrite confirmation for {}/{}",
                    upload.transferId,
                    upload.alias.ownerId(),
                    upload.alias.blueprintName()
            );
            final CompoundTag result = new CompoundTag();
            result.putUUID("transfer_id", upload.transferId);
            result.putString("expected_target_sha256", currentTargetSha);
            send(player, "overwrite_required", true, "confirm", session.token, result);
        } catch (final IOException | RuntimeException exception) {
            SableSchematicApi.LOGGER.warn("Failed completing manuscript upload for {}", player.getScoreboardName(), exception);
            failUpload(player, session, "blueprint_invalid");
        }
    }

    private static void confirmOverwrite(final ServerPlayer player,
                                         final Session session,
                                         final CompoundTag data) throws IOException {
        final Upload upload = session.upload;
        if (upload == null || !data.hasUUID("transfer_id")
                || !upload.transferId.equals(data.getUUID("transfer_id"))) {
            send(player, "confirm_overwrite", false, "transfer_invalid", session.token, new CompoundTag());
            return;
        }
        final String expected = data.getString("expected_target_sha256");
        final String current = BlueprintWorldStore.currentSha256(player.server, upload.alias);
        if (!expected.equals(upload.expectedTargetSha256) || !current.equals(expected)) {
            upload.expectedTargetSha256 = current;
            final CompoundTag result = new CompoundTag();
            result.putUUID("transfer_id", upload.transferId);
            result.putString("expected_target_sha256", current);
            send(player, "overwrite_required", false, "target_changed", session.token, result);
            return;
        }
        commitAndFill(player, session);
    }

    private static void commitAndFill(final ServerPlayer player, final Session session) throws IOException {
        final Upload upload = session.upload;
        BlueprintWorldStore.commit(upload.staging, player.server, upload.alias);
        fill(player, session, upload.alias);
        SableSchematicApi.LOGGER.info(
                "Committed manuscript upload {} for {}/{} (sha256={})",
                upload.transferId,
                upload.alias.ownerId(),
                upload.alias.blueprintName(),
                upload.uploadSha256.substring(0, 12)
        );
        session.upload = null;
        send(player, "upload_complete", true, "saved", session.token, stateTag(BlueprintManuscriptItem.state(session.stackReference)));
    }

    private static void fill(final ServerPlayer player,
                             final Session session,
                             final BlueprintAlias alias) throws IOException {
        if (validSession(player, session.token) == null) {
            throw new IOException("Manuscript moved before upload completion.");
        }
        BlueprintManuscriptItem.setState(session.stackReference, BlueprintManuscriptState.filled(alias));
    }

    private static void rotate(final ServerPlayer player,
                               final Session session,
                               final boolean clockwise) {
        final BlueprintManuscriptState state = BlueprintManuscriptItem.state(session.stackReference);
        if (!state.isFilled()) {
            send(player, "rotate", false, "manuscript_empty", session.token, new CompoundTag());
            return;
        }
        final BlueprintManuscriptState rotated = clockwise
                ? state.rotateClockwise()
                : state.rotateCounterClockwise();
        BlueprintManuscriptItem.setState(session.stackReference, rotated);
        send(player, "rotate", true, "ok", session.token, stateTag(rotated));
    }

    private static void discard(final ServerPlayer player, final Session session) {
        BlueprintManuscriptItem.setState(session.stackReference, BlueprintManuscriptState.EMPTY);
        if (session.upload != null) {
            session.upload.cleanup();
            session.upload = null;
        }
        send(player, "discard", true, "ok", session.token, stateTag(BlueprintManuscriptState.EMPTY));
    }

    private static void cancelUpload(final ServerPlayer player, final Session session) {
        if (session.upload != null) {
            session.upload.cleanup();
            session.upload = null;
        }
        send(player, "cancel_upload", true, "ok", session.token, new CompoundTag());
    }

    private static void sendDetails(final ServerPlayer player,
                                    final Session session,
                                    final String cachedSha256) throws IOException {
        final BlueprintManuscriptState state = BlueprintManuscriptItem.state(session.stackReference);
        if (state.alias().isEmpty()) {
            send(player, "details", false, "manuscript_empty", session.token, new CompoundTag());
            return;
        }
        final BlueprintAlias alias = state.alias().orElseThrow();
        final BlueprintDocument document;
        try {
            document = BlueprintWorldStore.inspect(player.server, alias);
        } catch (final IOException exception) {
            send(player, "details", false, "blueprint_missing", session.token, stateTag(state));
            return;
        }
        if (document.sha256().equals(cachedSha256)) {
            final CompoundTag result = stateTag(state);
            result.putString("sha256", document.sha256());
            send(player, "details_not_modified", true, "ok", session.token, result);
            return;
        }
        final CompoundTag detail = stateTag(state);
        detail.putString("sha256", document.sha256());
        detail.putString("author", document.author());
        detail.putString("description", document.description());
        final CompoundTag summary = new CompoundTag();
        summary.putInt("sub_levels", document.summary().subLevels());
        summary.putInt("blocks", document.summary().blocks());
        summary.putInt("block_entity_tags", document.summary().blockEntityTags());
        summary.putInt("entities", document.summary().entities());
        detail.put("summary", summary);
        if (document.preview() != null) {
            detail.put("preview", document.preview().save());
        }
        final byte[] bytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(detail, output);
            bytes = output.toByteArray();
        }
        sendDownload(player, session.token, document.sha256(), bytes);
    }

    private static void sendDownload(final ServerPlayer player,
                                     final UUID token,
                                     final String sha256,
                                     final byte[] bytes) {
        final UUID transferId = UUID.randomUUID();
        final int chunkSize = ManuscriptUploadChunkPayload.MAX_CHUNK_BYTES;
        final int totalChunks = Math.max(1, (bytes.length + chunkSize - 1) / chunkSize);
        for (int index = 0; index < totalChunks; index++) {
            final int start = index * chunkSize;
            final int end = Math.min(bytes.length, start + chunkSize);
            PacketDistributor.sendToPlayer(player, new ManuscriptDownloadChunkPayload(
                    token,
                    transferId,
                    index,
                    totalChunks,
                    bytes.length,
                    sha256,
                    Arrays.copyOfRange(bytes, start, end)
            ));
        }
    }

    private static Session validSession(final ServerPlayer player, final UUID token) {
        final Session session = SESSIONS.get(player.getUUID());
        if (session == null || token == null || !session.token.equals(token)
                || player.server.getTickCount() - session.lastActivityTick > TIMEOUT_TICKS) {
            return null;
        }
        final ItemStack held = player.getItemInHand(session.hand);
        if (held != session.stackReference || !held.is(SableSchematicApiItems.BLUEPRINT_MANUSCRIPT.get())) {
            close(player.getUUID());
            return null;
        }
        return session;
    }

    private static CompoundTag stateTag(final BlueprintManuscriptState state) {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("filled", state.isFilled());
        tag.putInt("rotation", state.rotation().degrees());
        state.alias().ifPresent(alias -> {
            tag.putUUID("owner_id", alias.ownerId());
            tag.putString("owner_name", alias.ownerName());
            tag.putString("blueprint_name", alias.blueprintName());
        });
        return tag;
    }

    private static void failUpload(final ServerPlayer player,
                                   final Session session,
                                   final String reason) {
        if (session.upload != null) {
            session.upload.cleanup();
            session.upload = null;
        }
        send(player, "upload", false, reason, session.token, new CompoundTag());
    }

    private static void close(final UUID playerId) {
        final Session removed = SESSIONS.remove(playerId);
        if (removed != null) {
            removed.cleanup();
        }
    }

    private static void send(final ServerPlayer player,
                             final String action,
                             final boolean success,
                             final String reason,
                             final UUID token,
                             final CompoundTag data) {
        PacketDistributor.sendToPlayer(player, new ManuscriptResultPayload(action, success, reason, token, data));
    }

    private static final class Session {
        private final UUID token;
        private final InteractionHand hand;
        private final ItemStack stackReference;
        private long lastActivityTick;
        private Upload upload;

        private Session(final UUID token,
                        final InteractionHand hand,
                        final ItemStack stackReference,
                        final long lastActivityTick) {
            this.token = token;
            this.hand = hand;
            this.stackReference = stackReference;
            this.lastActivityTick = lastActivityTick;
        }

        private void touch(final ServerPlayer player) {
            this.lastActivityTick = player.server.getTickCount();
        }

        private void cleanup() {
            if (this.upload != null) {
                this.upload.cleanup();
                this.upload = null;
            }
        }
    }

    private static final class Upload {
        private final UUID transferId;
        private final BlueprintAlias alias;
        private final Path staging;
        private final int totalBytes;
        private final String uploadSha256;
        private int receivedBytes;
        private int nextChunk;
        private String expectedTargetSha256;

        private Upload(final UUID transferId,
                       final BlueprintAlias alias,
                       final Path staging,
                       final int totalBytes,
                       final String uploadSha256,
                       final String expectedTargetSha256) {
            this.transferId = transferId;
            this.alias = alias;
            this.staging = staging;
            this.totalBytes = totalBytes;
            this.uploadSha256 = uploadSha256;
            this.expectedTargetSha256 = expectedTargetSha256;
        }

        private void cleanup() {
            BlueprintWorldStore.deleteStaging(this.staging);
        }
    }
}

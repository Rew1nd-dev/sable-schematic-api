package dev.rew1nd.sableschematicapi.survival.camera.client;

import dev.rew1nd.sableschematicapi.network.BlueprintToolServerActions;
import dev.rew1nd.sableschematicapi.network.CameraResultPayload;
import dev.rew1nd.sableschematicapi.network.SableSchematicApiPackets;
import dev.rew1nd.sableschematicapi.survival.camera.CameraState;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalMetadata;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolClientPreviewPostProcessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/** Client requests and result handling shared by the viewfinder and camera UI. */
public final class CameraClientSession {
    private CameraClientSession() {
    }

    public static void requestCapture(final int fov) {
        final CompoundTag data = new CompoundTag();
        data.putInt("fov", fov);
        SableSchematicApiPackets.sendAction(BlueprintToolServerActions.CAMERA_CAPTURE, data);
    }

    public static void requestDownload(final CameraState state, final String name) {
        if (state == null || state.captureId() == null || name == null || name.isBlank()) {
            return;
        }
        final CompoundTag data = new CompoundTag();
        data.putUUID("capture_id", state.captureId());
        data.putString("name", name);
        SableSchematicApiPackets.sendAction(BlueprintToolServerActions.CAMERA_DOWNLOAD, data);
    }

    public static void requestSelectLocal(final String name) {
        final CompoundTag data = new CompoundTag();
        data.putString("name", name == null ? "" : name);
        SableSchematicApiPackets.sendAction(BlueprintToolServerActions.CAMERA_SELECT_LOCAL, data);
    }

    public static void requestFov(final int fov) {
        final CompoundTag data = new CompoundTag();
        data.putInt("fov", fov);
        SableSchematicApiPackets.sendAction(BlueprintToolServerActions.CAMERA_SET_FOV, data);
    }

    public static void requestPreviewView(final String view) {
        final CompoundTag data = new CompoundTag();
        data.putString("view", view == null ? CameraState.DEFAULT_PREVIEW_VIEW : view);
        SableSchematicApiPackets.sendAction(BlueprintToolServerActions.CAMERA_SET_PREVIEW_VIEW, data);
    }

    public static void requestPlace(final CameraState state) {
        if (state != null && state.captureId() != null) {
            final CompoundTag data = new CompoundTag();
            data.putUUID("capture_id", state.captureId());
            SableSchematicApiPackets.sendAction(BlueprintToolServerActions.CAMERA_PLACE_CACHED, data);
            return;
        }
        if (state == null || !state.hasSelectedLocalBlueprint()) {
            return;
        }
        try {
            final BlueprintToolLocalFiles.Entry entry = BlueprintToolLocalFiles.list().stream()
                    .filter(candidate -> candidate.name().equals(state.selectedLocalBlueprint()))
                    .findFirst()
                    .orElse(null);
            if (entry == null) {
                requestSelectLocal("");
                show("Camera blueprint is unavailable.", ChatFormatting.YELLOW);
                return;
            }
            final CompoundTag data = new CompoundTag();
            data.putString("name", entry.name());
            data.putByteArray("data", BlueprintToolLocalFiles.read(entry));
            SableSchematicApiPackets.sendAction(BlueprintToolServerActions.CAMERA_PLACE_LOCAL, data);
        } catch (final IOException e) {
            requestSelectLocal("");
            show("Camera blueprint could not be read.", ChatFormatting.YELLOW);
        }
    }

    public static void handleResult(final CameraResultPayload payload) {
        if ("download".equals(payload.action()) && payload.success()) {
            try {
                byte[] saveData = payload.data();
                try {
                    saveData = BlueprintToolClientPreviewPostProcessor.attachClientPreview(saveData);
                } catch (final IOException | RuntimeException ignored) {
                    // A live preview is optional; the blueprint bytes remain valid without it.
                }
                BlueprintToolLocalFiles.save(payload.name(), saveData);
                final BlueprintToolLocalFiles.Entry entry = BlueprintToolLocalFiles.list().stream()
                        .filter(candidate -> candidate.name().equals(payload.name()))
                        .findFirst()
                        .orElse(null);
                if (entry != null && Minecraft.getInstance().player != null) {
                    BlueprintToolLocalMetadata.write(entry, Minecraft.getInstance().player.getGameProfile().getName(), "");
                }
                final CompoundTag complete = new CompoundTag();
                complete.putUUID("capture_id", payload.captureId());
                complete.putString("name", payload.name());
                SableSchematicApiPackets.sendAction(BlueprintToolServerActions.CAMERA_SAVE_COMPLETE, complete);
                show("Camera blueprint saved: " + payload.name(), ChatFormatting.GREEN);
            } catch (final IOException e) {
                show("Failed to write camera blueprint.", ChatFormatting.RED);
            }
            return;
        }
        if (payload.success()) {
            if ("capture".equals(payload.action())) {
                CameraClientEvents.recordCapture(payload.captureId(), payload.bodyIds());
                show("Camera captured " + payload.bodyCount() + " body(s).", ChatFormatting.GREEN);
            }
            return;
        }
        show("Camera: " + payload.reason(), ChatFormatting.YELLOW);
    }

    private static void show(final String message, final ChatFormatting color) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message).withStyle(color), true);
        }
    }
}

package dev.rew1nd.sableschematicapi.survival.camera;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Small, stack-owned editor state for a camera. Blueprint payloads never live
 * in this component: a selected local file is named here and a temporary
 * capture is referenced by an opaque server-runtime id.
 */
public record CameraState(int targetFov,
                          String previewView,
                          String selectedLocalBlueprint,
                          UUID captureId,
                          UUID captureOwner) {
    private static final String ROOT_KEY = "sable_camera";
    private static final String FOV_KEY = "target_fov";
    private static final String PREVIEW_VIEW_KEY = "preview_view";
    private static final String SELECTED_BLUEPRINT_KEY = "selected_local_blueprint";
    private static final String CAPTURE_ID_KEY = "capture_id";
    private static final String CAPTURE_OWNER_KEY = "capture_owner";

    public static final int MIN_FOV = 20;
    public static final int DEFAULT_FOV = 70;
    public static final String DEFAULT_PREVIEW_VIEW = "ISO_XP_ZP";

    public CameraState {
        targetFov = clampFov(targetFov);
        previewView = sanitizePreviewView(previewView);
        selectedLocalBlueprint = sanitizeBlueprintName(selectedLocalBlueprint);
        if (captureId == null || captureOwner == null) {
            captureId = null;
            captureOwner = null;
        }
    }

    public static CameraState defaults() {
        return new CameraState(DEFAULT_FOV, DEFAULT_PREVIEW_VIEW, "", null, null);
    }

    public static CameraState read(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return defaults();
        }
        final CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return defaults();
        }
        return fromTag(root.getCompound(ROOT_KEY));
    }

    public static void write(final ItemStack stack, final CameraState state) {
        Objects.requireNonNull(stack, "stack");
        final CameraState safeState = state == null ? defaults() : state;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.put(ROOT_KEY, safeState.toTag()));
    }

    public CameraState withTargetFov(final int fov) {
        return new CameraState(fov, this.previewView, this.selectedLocalBlueprint, this.captureId, this.captureOwner);
    }

    public CameraState withPreviewView(final String view) {
        return new CameraState(this.targetFov, view, this.selectedLocalBlueprint, this.captureId, this.captureOwner);
    }

    public CameraState withSelectedLocalBlueprint(final String name) {
        return new CameraState(this.targetFov, this.previewView, name, this.captureId, this.captureOwner);
    }

    public CameraState withCapture(final UUID id, final UUID owner) {
        return new CameraState(this.targetFov, this.previewView, this.selectedLocalBlueprint, id, owner);
    }

    public CameraState clearCapture() {
        return new CameraState(this.targetFov, this.previewView, this.selectedLocalBlueprint, null, null);
    }

    public boolean hasSelectedLocalBlueprint() {
        return !this.selectedLocalBlueprint.isEmpty();
    }

    public boolean hasCaptureFor(final UUID playerId) {
        return this.captureId != null && this.captureOwner != null && this.captureOwner.equals(playerId);
    }

    public Optional<UUID> captureIdOptional() {
        return Optional.ofNullable(this.captureId);
    }

    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(FOV_KEY, this.targetFov);
        tag.putString(PREVIEW_VIEW_KEY, this.previewView);
        if (!this.selectedLocalBlueprint.isEmpty()) {
            tag.putString(SELECTED_BLUEPRINT_KEY, this.selectedLocalBlueprint);
        }
        if (this.captureId != null && this.captureOwner != null) {
            tag.putUUID(CAPTURE_ID_KEY, this.captureId);
            tag.putUUID(CAPTURE_OWNER_KEY, this.captureOwner);
        }
        return tag;
    }

    public static CameraState fromTag(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return defaults();
        }
        final UUID id = tag.hasUUID(CAPTURE_ID_KEY) ? tag.getUUID(CAPTURE_ID_KEY) : null;
        final UUID owner = tag.hasUUID(CAPTURE_OWNER_KEY) ? tag.getUUID(CAPTURE_OWNER_KEY) : null;
        return new CameraState(
                tag.contains(FOV_KEY, Tag.TAG_INT) ? tag.getInt(FOV_KEY) : DEFAULT_FOV,
                tag.getString(PREVIEW_VIEW_KEY),
                tag.getString(SELECTED_BLUEPRINT_KEY),
                id,
                owner
        );
    }

    public static int clampFov(final int fov) {
        return Math.clamp(fov, MIN_FOV, 179);
    }

    private static String sanitizePreviewView(final String value) {
        final String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() || trimmed.length() > 64 ? DEFAULT_PREVIEW_VIEW : trimmed;
    }

    private static String sanitizeBlueprintName(final String value) {
        final String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isEmpty() || trimmed.length() > 128 || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            return "";
        }
        return trimmed;
    }
}

package dev.rew1nd.sableschematicapi.blueprint.preview;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class SableBlueprintPreview {
    public static final int VERSION = 2;
    public static final int DEFAULT_RESOLUTION = 512;

    private final int resolution;
    private final int basisSubLevelId;
    private final BoundingBox3d boundsInBasis;
    private final EnumMap<View, int[]> views;

    public SableBlueprintPreview(final int resolution,
                                 final int basisSubLevelId,
                                 final BoundingBox3d boundsInBasis,
                                 final Map<View, int[]> views) {
        this.resolution = resolution;
        this.basisSubLevelId = basisSubLevelId;
        this.boundsInBasis = new BoundingBox3d(boundsInBasis);
        this.views = new EnumMap<>(View.class);

        final int expectedPixels = resolution * resolution;
        for (final View view : View.values()) {
            final int[] pixels = views.get(view);
            if (pixels == null || pixels.length != expectedPixels) {
                this.views.put(view, new int[expectedPixels]);
            } else {
                this.views.put(view, Arrays.copyOf(pixels, pixels.length));
            }
        }
    }

    public int resolution() {
        return this.resolution;
    }

    public int basisSubLevelId() {
        return this.basisSubLevelId;
    }

    public BoundingBox3d boundsInBasis() {
        return new BoundingBox3d(this.boundsInBasis);
    }

    public int[] pixels(final View view) {
        return this.views.get(view);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        final CompoundTag viewsTag = new CompoundTag();

        tag.putInt("version", VERSION);
        tag.putInt("resolution", this.resolution);
        tag.putInt("basis_sub_level_id", this.basisSubLevelId);
        tag.put("bounds_in_basis", writeBounds(this.boundsInBasis));

        for (final View view : View.values()) {
            final CompoundTag viewTag = new CompoundTag();
            viewTag.putInt("width", this.resolution);
            viewTag.putInt("height", this.resolution);
            viewTag.putIntArray("pixels", this.views.get(view));
            viewsTag.put(view.id(), viewTag);
        }

        tag.put("views", viewsTag);
        return tag;
    }

    public static SableBlueprintPreview load(final CompoundTag tag) {
        final int version = tag.getInt("version");
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported Sable blueprint preview version: " + version);
        }

        final int resolution = tag.getInt("resolution");
        if (resolution <= 0) {
            throw new IllegalArgumentException("Invalid Sable blueprint preview resolution: " + resolution);
        }

        final int expectedPixels = resolution * resolution;
        final CompoundTag viewsTag = tag.getCompound("views");
        final EnumMap<View, int[]> views = new EnumMap<>(View.class);
        for (final View view : View.values()) {
            final CompoundTag viewTag = viewsTag.getCompound(view.id());
            final int width = viewTag.getInt("width");
            final int height = viewTag.getInt("height");
            final int[] pixels = viewTag.contains("pixels", Tag.TAG_INT_ARRAY) ? viewTag.getIntArray("pixels") : new int[0];
            if (width != resolution || height != resolution || pixels.length != expectedPixels) {
                views.put(view, new int[expectedPixels]);
            } else {
                views.put(view, pixels);
            }
        }

        return new SableBlueprintPreview(
                resolution,
                tag.getInt("basis_sub_level_id"),
                readBounds(tag.getCompound("bounds_in_basis")),
                views
        );
    }

    private static CompoundTag writeBounds(final BoundingBox3d bounds) {
        final CompoundTag tag = new CompoundTag();
        tag.putDouble("min_x", bounds.minX());
        tag.putDouble("min_y", bounds.minY());
        tag.putDouble("min_z", bounds.minZ());
        tag.putDouble("max_x", bounds.maxX());
        tag.putDouble("max_y", bounds.maxY());
        tag.putDouble("max_z", bounds.maxZ());
        return tag;
    }

    private static BoundingBox3d readBounds(final CompoundTag tag) {
        return new BoundingBox3d(
                tag.getDouble("min_x"),
                tag.getDouble("min_y"),
                tag.getDouble("min_z"),
                tag.getDouble("max_x"),
                tag.getDouble("max_y"),
                tag.getDouble("max_z")
        );
    }

    public enum View {
        ISO_XP_ZP(1, 1),
        ISO_XP_ZN(1, -1),
        ISO_XN_ZP(-1, 1),
        ISO_XN_ZN(-1, -1);

        private final int xSign;
        private final int zSign;

        View(final int xSign, final int zSign) {
            this.xSign = xSign;
            this.zSign = zSign;
        }

        public int xSign() {
            return this.xSign;
        }

        public int zSign() {
            return this.zSign;
        }

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}

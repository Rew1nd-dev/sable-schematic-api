package dev.rew1nd.sableschematicapi.blueprint.preview;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

public final class SableBlueprintPreviewGenerator {
    private static final int PADDING = 8;
    private static final int FALLBACK_COLOR = 0xFF8A8F98;

    private SableBlueprintPreviewGenerator() {
    }

    public static @Nullable SableBlueprintPreview generate(final List<SableBlueprint.SubLevelData> subLevels) {
        if (subLevels.isEmpty()) {
            return null;
        }

        final SableBlueprint.SubLevelData basis = selectBasis(subLevels);
        final List<ProjectedBlock> blocks = new ObjectArrayList<>();
        final BoundingBox3d bounds = new BoundingBox3d();
        boolean hasBounds = false;

        for (final SableBlueprint.SubLevelData entry : subLevels) {
            for (final SableBlueprint.BlockData block : entry.blocks()) {
                if (block.paletteId() < 0 || block.paletteId() >= entry.blockPalette().size()) {
                    continue;
                }

                final BlockState state = entry.blockPalette().get(block.paletteId());
                if (state.isAir()) {
                    continue;
                }

                final ProjectedBlock projected = projectBlock(entry, basis, block.localPos(), blockColor(state));
                blocks.add(projected);
                if (hasBounds) {
                    bounds.expandTo(projected.minX(), projected.minY(), projected.minZ());
                    bounds.expandTo(projected.maxX(), projected.maxY(), projected.maxZ());
                } else {
                    bounds.setUnchecked(projected.minX(), projected.minY(), projected.minZ(), projected.maxX(), projected.maxY(), projected.maxZ());
                    hasBounds = true;
                }
            }
        }

        if (!hasBounds) {
            bounds.setUnchecked(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        }

        final EnumMap<SableBlueprintPreview.View, int[]> views = new EnumMap<>(SableBlueprintPreview.View.class);
        for (final SableBlueprintPreview.View view : SableBlueprintPreview.View.values()) {
            views.put(view, renderView(blocks, bounds, view, SableBlueprintPreview.DEFAULT_RESOLUTION));
        }

        return new SableBlueprintPreview(
                SableBlueprintPreview.DEFAULT_RESOLUTION,
                basis.id(),
                bounds,
                views
        );
    }

    private static SableBlueprint.SubLevelData selectBasis(final List<SableBlueprint.SubLevelData> subLevels) {
        return subLevels.stream()
                .max(Comparator
                        .comparingLong(SableBlueprintPreviewGenerator::localVolume)
                        .thenComparingInt(entry -> entry.blocks().size()))
                .orElseThrow();
    }

    private static long localVolume(final SableBlueprint.SubLevelData entry) {
        final int width = entry.localBounds().maxX() - entry.localBounds().minX() + 1;
        final int height = entry.localBounds().maxY() - entry.localBounds().minY() + 1;
        final int depth = entry.localBounds().maxZ() - entry.localBounds().minZ() + 1;
        return (long) Math.max(width, 0) * Math.max(height, 0) * Math.max(depth, 0);
    }

    private static ProjectedBlock projectBlock(final SableBlueprint.SubLevelData entry,
                                               final SableBlueprint.SubLevelData basis,
                                               final BlockPos localPos,
                                               final int color) {
        final BoundsBuilder bounds = new BoundsBuilder();

        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    final Vector3d point = new Vector3d(
                            localPos.getX() + dx,
                            localPos.getY() + dy,
                            localPos.getZ() + dz
                    );
                    entry.relativePose().transformPosition(point);
                    basis.relativePose().transformPositionInverse(point);
                    bounds.include(point);
                }
            }
        }

        return new ProjectedBlock(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ, color);
    }

    private static int[] renderView(final List<ProjectedBlock> blocks,
                                    final BoundingBox3d bounds,
                                    final SableBlueprintPreview.View view,
                                    final int resolution) {
        final int[] pixels = new int[resolution * resolution];
        final double[] depth = new double[pixels.length];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);

        final double minU = boundsMinU(bounds, view);
        final double maxU = boundsMaxU(bounds, view);
        final double minV = boundsMinV(bounds, view);
        final double maxV = boundsMaxV(bounds, view);
        final double rangeU = Math.max(maxU - minU, 1.0);
        final double rangeV = Math.max(maxV - minV, 1.0);
        final double drawable = Math.max(1.0, resolution - PADDING * 2.0);
        final double scale = drawable / Math.max(rangeU, rangeV);
        final double offsetU = (resolution - rangeU * scale) * 0.5;
        final double offsetV = (resolution - rangeV * scale) * 0.5;
        final double brightness = brightness(view);

        for (final ProjectedBlock block : blocks) {
            final double blockMinU = blockMinU(block, view);
            final double blockMaxU = blockMaxU(block, view);
            final double blockMinV = blockMinV(block, view);
            final double blockMaxV = blockMaxV(block, view);
            final double blockDepth = blockDepth(block, view);
            final int color = shade(block.color(), brightness);

            int x0 = clamp((int) Math.floor(offsetU + (blockMinU - minU) * scale), 0, resolution - 1);
            int x1 = clamp((int) Math.ceil(offsetU + (blockMaxU - minU) * scale), 0, resolution);
            int y0 = clamp((int) Math.floor(offsetV + (maxV - blockMaxV) * scale), 0, resolution - 1);
            int y1 = clamp((int) Math.ceil(offsetV + (maxV - blockMinV) * scale), 0, resolution);

            if (x1 <= x0) {
                x1 = Math.min(resolution, x0 + 1);
            }
            if (y1 <= y0) {
                y1 = Math.min(resolution, y0 + 1);
            }

            for (int y = y0; y < y1; y++) {
                final int row = y * resolution;
                for (int x = x0; x < x1; x++) {
                    final int index = row + x;
                    if (blockDepth >= depth[index]) {
                        depth[index] = blockDepth;
                        pixels[index] = color;
                    }
                }
            }
        }

        return pixels;
    }

    private static int blockColor(final BlockState state) {
        try {
            final MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            final int color = mapColor.col;
            return color == 0 ? FALLBACK_COLOR : 0xFF000000 | color;
        } catch (final RuntimeException e) {
            return FALLBACK_COLOR;
        }
    }

    private static double boundsMinU(final BoundingBox3d bounds, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM, FRONT, BACK -> bounds.minX();
            case RIGHT, LEFT -> bounds.minZ();
        };
    }

    private static double boundsMaxU(final BoundingBox3d bounds, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM, FRONT, BACK -> bounds.maxX();
            case RIGHT, LEFT -> bounds.maxZ();
        };
    }

    private static double boundsMinV(final BoundingBox3d bounds, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM -> bounds.minZ();
            case FRONT, BACK, RIGHT, LEFT -> bounds.minY();
        };
    }

    private static double boundsMaxV(final BoundingBox3d bounds, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM -> bounds.maxZ();
            case FRONT, BACK, RIGHT, LEFT -> bounds.maxY();
        };
    }

    private static double blockMinU(final ProjectedBlock block, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM, FRONT, BACK -> block.minX();
            case RIGHT, LEFT -> block.minZ();
        };
    }

    private static double blockMaxU(final ProjectedBlock block, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM, FRONT, BACK -> block.maxX();
            case RIGHT, LEFT -> block.maxZ();
        };
    }

    private static double blockMinV(final ProjectedBlock block, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM -> block.minZ();
            case FRONT, BACK, RIGHT, LEFT -> block.minY();
        };
    }

    private static double blockMaxV(final ProjectedBlock block, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM -> block.maxZ();
            case FRONT, BACK, RIGHT, LEFT -> block.maxY();
        };
    }

    private static double blockDepth(final ProjectedBlock block, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP -> block.maxY();
            case BOTTOM -> -block.minY();
            case FRONT -> block.maxZ();
            case BACK -> -block.minZ();
            case RIGHT -> block.maxX();
            case LEFT -> -block.minX();
        };
    }

    private static double brightness(final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP -> 1.0;
            case BOTTOM -> 0.55;
            case FRONT -> 0.9;
            case BACK -> 0.72;
            case RIGHT -> 0.82;
            case LEFT -> 0.68;
        };
    }

    private static int shade(final int color, final double brightness) {
        final int alpha = color & 0xFF000000;
        final int red = (int) Math.min(255, ((color >> 16) & 0xFF) * brightness);
        final int green = (int) Math.min(255, ((color >> 8) & 0xFF) * brightness);
        final int blue = (int) Math.min(255, (color & 0xFF) * brightness);
        return alpha | red << 16 | green << 8 | blue;
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class BoundsBuilder {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void include(final Vector3d point) {
            this.minX = Math.min(this.minX, point.x);
            this.minY = Math.min(this.minY, point.y);
            this.minZ = Math.min(this.minZ, point.z);
            this.maxX = Math.max(this.maxX, point.x);
            this.maxY = Math.max(this.maxY, point.y);
            this.maxZ = Math.max(this.maxZ, point.z);
        }
    }

    private record ProjectedBlock(double minX,
                                  double minY,
                                  double minZ,
                                  double maxX,
                                  double maxY,
                                  double maxZ,
                                  int color) {
    }
}

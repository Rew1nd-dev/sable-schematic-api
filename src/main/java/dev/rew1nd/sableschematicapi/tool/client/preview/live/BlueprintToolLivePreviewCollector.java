package dev.rew1nd.sableschematicapi.tool.client.preview.live;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewBounds;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector3d;

import java.util.Comparator;
import java.util.List;

public final class BlueprintToolLivePreviewCollector {
    private BlueprintToolLivePreviewCollector() {
    }

    public static List<BlueprintToolLivePreviewEntry> collect(final SableBlueprint blueprint) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return List.of();
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return List.of();
        }

        final List<BlueprintToolLivePreviewEntry> entries = new ObjectArrayList<>(blueprint.subLevels().size());
        for (final SableBlueprint.SubLevelData data : blueprint.subLevels()) {
            final SubLevel subLevel = container.getSubLevel(data.sourceUuid());
            if (!(subLevel instanceof final ClientSubLevel clientSubLevel) || clientSubLevel.isRemoved()) {
                continue;
            }

            final BoundingBox3ic bounds = clientSubLevel.getPlot().getBoundingBox();
            if (bounds == null || bounds.volume() <= 0) {
                continue;
            }

            entries.add(new BlueprintToolLivePreviewEntry(data, clientSubLevel, new Pose3d(clientSubLevel.renderPose())));
        }
        return entries;
    }

    public static BlueprintToolLivePreviewGeometry analyze(final List<BlueprintToolLivePreviewEntry> entries) {
        final BlueprintToolLivePreviewEntry basis = selectBasis(entries);
        final BoundingBox3d bounds = new BoundingBox3d();
        boolean hasBounds = false;

        for (final BlueprintToolLivePreviewEntry entry : entries) {
            final BoundingBox3ic localBounds = entry.subLevel().getPlot().getBoundingBox();
            for (final Vector3d corner : cornersInBasis(entry, basis, localBounds)) {
                if (hasBounds) {
                    bounds.expandTo(corner.x, corner.y, corner.z);
                } else {
                    bounds.setUnchecked(corner.x, corner.y, corner.z, corner.x, corner.y, corner.z);
                    hasBounds = true;
                }
            }
        }

        if (!hasBounds) {
            bounds.setUnchecked(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        }
        return new BlueprintToolLivePreviewGeometry(List.copyOf(entries), basis, bounds);
    }

    private static List<Vector3d> cornersInBasis(final BlueprintToolLivePreviewEntry entry,
                                                 final BlueprintToolLivePreviewEntry basis,
                                                 final BoundingBox3ic localBounds) {
        final List<Vector3d> corners = new ObjectArrayList<>(8);
        for (final int x : new int[]{localBounds.minX(), localBounds.maxX() + 1}) {
            for (final int y : new int[]{localBounds.minY(), localBounds.maxY() + 1}) {
                for (final int z : new int[]{localBounds.minZ(), localBounds.maxZ() + 1}) {
                    final Vector3d point = new Vector3d(x, y, z);
                    entry.pose().transformPosition(point);
                    basis.pose().transformPositionInverse(point);
                    corners.add(point);
                }
            }
        }
        return corners;
    }

    private static BlueprintToolLivePreviewEntry selectBasis(final List<BlueprintToolLivePreviewEntry> entries) {
        return entries.stream()
                .max(Comparator
                        .comparingLong((BlueprintToolLivePreviewEntry entry) -> BlueprintToolPreviewBounds.localVolume(entry.data()))
                        .thenComparingInt(entry -> entry.data().blocks().size()))
                .orElseThrow();
    }
}

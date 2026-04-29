package dev.rew1nd.sableschematicapi.compat.create.client;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolSelectionPreview;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolSelectionPreviewRenderer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.world.phys.AABB;

public enum CreateBlueprintToolSelectionRenderer implements BlueprintToolSelectionPreviewRenderer {
    INSTANCE;

    private static final String SELECTION_KEY = SableSchematicApi.MODID + ":blueprint_tool_selection";
    private static final String SUBLEVEL_KEY_PREFIX = SableSchematicApi.MODID + ":blueprint_tool_sublevel/";
    private static final int START_COLOR = 0xE6B84A;
    private static final int SELECTION_COLOR = 0x4CC9F0;
    private static final int SUBLEVEL_COLOR = 0x7DFFB2;
    private static final float LINE_WIDTH = 1.0f / 16.0f;

    @Override
    public void render(final BlueprintToolSelectionPreview preview) {
        renderSelection(preview);
        renderIntersectingSubLevels(preview);
    }

    private static void renderSelection(final BlueprintToolSelectionPreview preview) {
        Outliner.getInstance()
                .showAABB(SELECTION_KEY, preview.selectionBox())
                .colored(preview.complete() ? SELECTION_COLOR : START_COLOR)
                .disableLineNormals()
                .lineWidth(LINE_WIDTH);
    }

    private static void renderIntersectingSubLevels(final BlueprintToolSelectionPreview preview) {
        final SubLevelContainer container = SubLevelContainer.getContainer(preview.player().level());
        if (container == null) {
            return;
        }

        final BoundingBox3d selectionBounds = new BoundingBox3d(preview.selectionBox());
        for (final SubLevel subLevel : container.queryIntersecting(selectionBounds)) {
            final BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
            if (plotBounds == null) {
                continue;
            }

            Outliner.getInstance()
                    .showAABB(SUBLEVEL_KEY_PREFIX + subLevel.getUniqueId(), toAabb(plotBounds))
                    .colored(SUBLEVEL_COLOR)
                    .disableLineNormals()
                    .lineWidth(LINE_WIDTH);
        }
    }

    private static AABB toAabb(final BoundingBox3ic bounds) {
        return new AABB(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX() + 1.0,
                bounds.maxY() + 1.0,
                bounds.maxZ() + 1.0
        );
    }
}

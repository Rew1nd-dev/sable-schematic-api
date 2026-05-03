package dev.rew1nd.sableschematicapi.tool.client.preview.live;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;

import java.util.List;

public record BlueprintToolLivePreviewGeometry(List<BlueprintToolLivePreviewEntry> entries,
                                               BlueprintToolLivePreviewEntry basis,
                                               BoundingBox3d bounds) {
}

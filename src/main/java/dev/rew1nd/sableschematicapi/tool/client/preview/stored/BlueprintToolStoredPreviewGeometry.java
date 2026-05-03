package dev.rew1nd.sableschematicapi.tool.client.preview.stored;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;

public record BlueprintToolStoredPreviewGeometry(BlueprintToolStoredPreviewEntry basis, BoundingBox3d bounds) {
}

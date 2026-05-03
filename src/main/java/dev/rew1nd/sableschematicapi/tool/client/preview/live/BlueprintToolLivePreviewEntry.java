package dev.rew1nd.sableschematicapi.tool.client.preview.live;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

public record BlueprintToolLivePreviewEntry(SableBlueprint.SubLevelData data, ClientSubLevel subLevel, Pose3d pose) {
}

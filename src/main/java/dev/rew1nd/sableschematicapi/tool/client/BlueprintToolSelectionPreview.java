package dev.rew1nd.sableschematicapi.tool.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public record BlueprintToolSelectionPreview(Player player, AABB selectionBox, boolean complete) {
}

package dev.rew1nd.sableschematicapi.tool.client;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public record BlueprintToolInputContext(Player player,
                                        InteractionHand hand,
                                        BlueprintToolInputIntent intent) {
    public boolean shifted() {
        return this.intent == BlueprintToolInputIntent.SHIFT_ATTACK || this.intent == BlueprintToolInputIntent.SHIFT_USE;
    }
}

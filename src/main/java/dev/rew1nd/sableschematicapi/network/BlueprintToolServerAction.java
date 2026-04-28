package dev.rew1nd.sableschematicapi.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface BlueprintToolServerAction {
    void handle(ServerPlayer player, CompoundTag data);
}

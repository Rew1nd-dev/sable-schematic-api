package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BlueprintToolClientPacketHandler {
    private BlueprintToolClientPacketHandler() {
    }

    public static void handleSaveResult(final BlueprintToolSaveResultPayload payload) {
        BlueprintToolClientSession.handleSaveResult(payload.name(), payload.success(), payload.message(), payload.data());
    }

    public static void handleSubLevelList(final BlueprintToolSubLevelListPayload payload) {
        BlueprintToolClientSession.handleSubLevelList(payload.data());
    }
}

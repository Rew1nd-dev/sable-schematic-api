package dev.rew1nd.sableschematicapi.compat.copycats;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostRuleCacheMode;
import dev.rew1nd.sableschematicapi.survival.BlueprintBlockCostRules;

public final class CopycatsBlueprintCompat {
    private static final int COPYCAT_RULE_PRIORITY = 1_000;
    private static boolean registered;

    private CopycatsBlueprintCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        final CopycatBlockCostRule rule = new CopycatBlockCostRule();
        BlueprintBlockCostRules.register(
                rule.id(),
                COPYCAT_RULE_PRIORITY,
                payload -> payload.state().getBlock() instanceof ICopycatBlock,
                rule,
                BlueprintBlockCostRuleCacheMode.BLOCK
        );

        registered = true;
        SableSchematicApi.LOGGER.info("Registered Copycats+ blueprint survival cost compatibility");
    }
}

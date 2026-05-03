package dev.rew1nd.sableschematicapi.api.blueprint.survival;

/**
 * Consumer-facing material budget used by the incremental survival placer.
 */
public interface BlueprintBuildMaterialBudget {
    BlueprintBuildMaterialBudget UNLIMITED = new BlueprintBuildMaterialBudget() {
        @Override
        public boolean canAfford(final CostQuote quote) {
            return true;
        }

        @Override
        public ConsumeResult consume(final CostQuote quote) {
            return ConsumeResult.success();
        }
    };

    boolean canAfford(CostQuote quote);

    ConsumeResult consume(CostQuote quote);
}

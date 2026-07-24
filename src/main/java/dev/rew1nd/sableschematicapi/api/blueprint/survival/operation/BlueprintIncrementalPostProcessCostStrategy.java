package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;

/**
 * Optional extension for post-process costs whose quote itself can contain a
 * large amount of work, such as every block inside a Create contraption.
 */
public interface BlueprintIncrementalPostProcessCostStrategy<T extends BlueprintPostProcessOperation>
        extends BlueprintPostProcessCostStrategy<T> {

    Cursor begin(T operation, BlueprintPostProcessCostContext context);

    interface Cursor {
        /**
         * Advances by at most {@code maxWorkUnits}.
         */
        Step step(int maxWorkUnits);
    }

    record Step(CostQuote quote, int workUnits, boolean done) {
        public Step {
            if (workUnits < 0) {
                throw new IllegalArgumentException("workUnits must be non-negative");
            }
        }
    }
}

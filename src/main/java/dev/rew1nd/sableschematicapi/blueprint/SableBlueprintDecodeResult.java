package dev.rew1nd.sableschematicapi.blueprint;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticReport;

/**
 * Result of decoding a blueprint payload.
 *
 * @param blueprint   decoded blueprint model
 * @param diagnostics recoverable decode warnings
 */
public record SableBlueprintDecodeResult(SableBlueprint blueprint, BlueprintDiagnosticReport diagnostics) {
    public SableBlueprintDecodeResult {
        diagnostics = diagnostics == null ? BlueprintDiagnosticReport.empty() : diagnostics;
    }
}

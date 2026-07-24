package dev.rew1nd.sableschematicapi.survival.manuscript;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import org.jetbrains.annotations.Nullable;

/** Decoded server blueprint metadata used by manuscript UI and film freezing. */
public record BlueprintDocument(String sha256,
                                String author,
                                String description,
                                BlueprintSummary summary,
                                @Nullable SableBlueprintPreview preview) {
}

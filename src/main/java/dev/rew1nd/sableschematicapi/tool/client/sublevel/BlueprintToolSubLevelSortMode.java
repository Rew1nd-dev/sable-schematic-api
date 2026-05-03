package dev.rew1nd.sableschematicapi.tool.client.sublevel;

public enum BlueprintToolSubLevelSortMode {
    NAME_DESC("ui.sublevel_sort_name_desc"),
    DISTANCE("ui.sublevel_sort_distance");

    private final String translationKey;

    BlueprintToolSubLevelSortMode(final String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }
}

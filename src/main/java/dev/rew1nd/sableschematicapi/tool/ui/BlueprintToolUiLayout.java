package dev.rew1nd.sableschematicapi.tool.ui;

public final class BlueprintToolUiLayout {
    public static final float ROOT_PADDING = 8;
    public static final float GAP = 4;
    public static final float MODE_BAR_WIDTH = 56;
    public static final float CONTENT_WIDTH = 360;
    public static final float CONTENT_HEIGHT = 224;
    public static final float ROOT_WIDTH = MODE_BAR_WIDTH + CONTENT_WIDTH + GAP + ROOT_PADDING * 2;
    public static final float ROOT_HEIGHT = CONTENT_HEIGHT + ROOT_PADDING * 2;
    public static final float ROW_HEIGHT = 16;
    public static final float ICON_BUTTON_SIZE = 18;
    public static final float LIST_HEIGHT = 140;
    public static final float SUBLEVEL_LIST_HEIGHT = 144;
    public static final float TAB_HEIGHT = 22;
    public static final float SCROLL_ROW_WIDTH = CONTENT_WIDTH - 12;
    public static final float GROUP_PADDING = 3;
    public static final float GROUP_INNER_WIDTH = SCROLL_ROW_WIDTH - GROUP_PADDING * 2;
    public static final float PREVIEW_DIALOG_INNER_WIDTH = CONTENT_WIDTH - 16;
    public static final float PREVIEW_DIALOG_SCROLL_HEIGHT = CONTENT_HEIGHT - 20 - 16 - ROW_HEIGHT - GAP;
    public static final float PREVIEW_CANVAS_HEIGHT = 128;
    public static final float METADATA_PANEL_WIDTH = PREVIEW_DIALOG_INNER_WIDTH - 20;
    public static final float METADATA_PANEL_PADDING = 4;
    public static final float METADATA_PANEL_INNER_WIDTH = METADATA_PANEL_WIDTH - METADATA_PANEL_PADDING * 2;
    public static final float METADATA_PREVIEW_WIDTH = PREVIEW_CANVAS_HEIGHT;
    public static final float METADATA_AUTHOR_WIDTH = METADATA_PANEL_INNER_WIDTH - METADATA_PREVIEW_WIDTH - GAP;
    public static final float DESCRIPTION_HEIGHT = 58;
    public static final float SUBLEVEL_DETAIL_WIDTH = CONTENT_WIDTH - 16;
    public static final float SUBLEVEL_INFO_LABEL_WIDTH = 36;

    private BlueprintToolUiLayout() {
    }
}

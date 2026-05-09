package dev.rew1nd.sableschematicapi.tool.ui;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Supplier;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ICON_BUTTON_SIZE;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROW_HEIGHT;

public final class BlueprintToolUiKit {
    private static final String KEY_PREFIX = "sable_schematic_api.blueprint_tool.";

    public static final int TEXT_COLOR = ColorPattern.WHITE.color;
    public static final int ROOT_COLOR = ColorPattern.BLACK.color;
    public static final int PANEL_COLOR = ColorPattern.SEAL_BLACK.color;
    public static final int FIELD_COLOR = ColorPattern.DARK_GRAY.color;
    public static final int BORDER_COLOR = ColorPattern.GRAY.color;
    public static final int BUTTON_COLOR = ColorPattern.DARK_GRAY.color;
    public static final int BUTTON_HOVER_COLOR = ColorPattern.T_BLUE.color;
    public static final int BUTTON_PRESSED_COLOR = ColorPattern.SEAL_BLACK.color;

    private BlueprintToolUiKit() {
    }

    public static Label modeHeader(final Component modeLabel) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(modeLabel);
        label.layout(layout -> {
            layout.width(CONTENT_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }

    public static Label boundLabel(final Supplier<Component> supplier) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.bindDataSource(SupplierDataSource.<Component>of(supplier).frequency(5));
        label.layout(layout -> {
            layout.width(CONTENT_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        label.style(style -> style
                .backgroundTexture(displayTexture())
                .overflowVisible(false));
        return label;
    }

    public static Button textButton(final Component text) {
        final Button button = new Button();
        button.setText(text);
        button.setOverflowVisible(false);
        button.text.layout(LayoutStyle::widthStretch);
        button.buttonStyle(style -> style
                .baseTexture(buttonTexture(FIELD_COLOR, BORDER_COLOR))
                .hoverTexture(buttonTexture(BUTTON_HOVER_COLOR, BORDER_COLOR))
                .pressedTexture(buttonTexture(BUTTON_PRESSED_COLOR, BORDER_COLOR)));
        button.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textColor(TEXT_COLOR)
                .textShadow(false)
                .fontSize(8)
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(1.0F)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        return button;
    }

    public static Button iconButton(final IGuiTexture icon, final Component tooltip) {
        final Button button = new Button();
        button.noText();
        button.addPreIcon(icon);
        button.setOverflowVisible(false);
        button.style(style -> style.tooltips(tooltip));
        button.buttonStyle(style -> style
                .baseTexture(buttonTexture(BUTTON_COLOR, BORDER_COLOR))
                .hoverTexture(buttonTexture(BUTTON_HOVER_COLOR, BORDER_COLOR))
                .pressedTexture(buttonTexture(BUTTON_PRESSED_COLOR, BORDER_COLOR)));
        button.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
            layout.paddingAll(0);
        });
        return button;
    }

    public static TextField textField(final String id, final float width) {
        final TextField field = new TextField();
        field.setId(id);
        field.style(style -> style.backgroundTexture(fieldTexture()));
        field.textFieldStyle(style -> style
                .textColor(TEXT_COLOR)
                .cursorColor(TEXT_COLOR)
                .textShadow(false)
                .fontSize(8));
        field.layout(layout -> {
            layout.width(width);
            layout.height(ROW_HEIGHT);
            layout.paddingHorizontal(2);
            layout.paddingVertical(1);
        });
        return field;
    }

    public static TextArea textArea(final String id, final float width, final float height) {
        final TextArea area = new TextArea();
        area.setId(id);
        area.style(style -> style.backgroundTexture(fieldTexture()));
        area.contentView.style(style -> style.backgroundTexture(fieldTexture()));
        area.textAreaStyle(style -> style
                .textColor(TEXT_COLOR)
                .cursorColor(TEXT_COLOR)
                .textShadow(false)
                .fontSize(8)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .viewMode(ScrollerMode.VERTICAL)
                .lineSpacing(1.0F));
        area.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.paddingAll(2);
        });
        return area;
    }

    public static Label bareLabel(final Horizontal align) {
        final Label label = new Label();
        label.setText(Component.empty());
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(1.0F)
                .textColor(TEXT_COLOR)
                .textShadow(false)
                .fontSize(8)
                .textAlignHorizontal(align)
                .textAlignVertical(Vertical.CENTER));
        label.style(style -> style.overflowVisible(false));
        return label;
    }

    public static IGuiTexture panelTexture() {
        return SDFRectTexture.of(ROOT_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    public static IGuiTexture dialogTexture() {
        return SDFRectTexture.of(PANEL_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    public static IGuiTexture fieldTexture() {
        return SDFRectTexture.of(FIELD_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    public static IGuiTexture displayTexture() {
        return SDFRectTexture.of(PANEL_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    public static IGuiTexture groupTexture() {
        return SDFRectTexture.of(FIELD_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    public static IGuiTexture groupHeaderTexture() {
        return SDFRectTexture.of(BUTTON_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    public static IGuiTexture selectedRowTexture() {
        return buttonTexture(BUTTON_HOVER_COLOR, BORDER_COLOR);
    }

    public static IGuiTexture buttonTexture(final int fill, final int border) {
        return SDFRectTexture.of(fill)
                .setBorderColor(border)
                .setRadius(2)
                .setStroke(1);
    }

    public static Component tr(final String key, final Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }

    public static void drawLargePreviewTile(final GUIContext guiContext,
                                            final SableBlueprintPreview preview,
                                            final SableBlueprintPreview.View view,
                                            final int x,
                                            final int y,
                                            final int size) {
        guiContext.graphics.fill(x, y, x + size, y + size, FIELD_COLOR);

        if (preview != null) {
            final int resolution = preview.resolution();
            final int[] pixels = preview.pixels(view);
            if (pixels != null && pixels.length == resolution * resolution) {
                for (int py = 0; py < size; py++) {
                    final int sourceY = py * resolution / size;
                    for (int px = 0; px < size; px++) {
                        final int sourceX = px * resolution / size;
                        final int color = pixels[sourceY * resolution + sourceX];
                        if ((color >>> 24) != 0) {
                            guiContext.graphics.fill(x + px, y + py, x + px + 1, y + py + 1, color);
                        }
                    }
                }
            }
        }

        drawBorder(guiContext, x, y, size, size, BORDER_COLOR);
        drawFullViewLabel(guiContext, view, x, y);
    }

    private static void drawFullViewLabel(final GUIContext guiContext,
                                          final SableBlueprintPreview.View view,
                                          final int x,
                                          final int y) {
        final Font font = Minecraft.getInstance().font;
        final String text = view.id().toUpperCase(Locale.ROOT);
        final int labelWidth = font.width(text) + 4;
        guiContext.graphics.fill(x + 1, y + 1, x + labelWidth, y + 10, 0xCC101820);
        guiContext.graphics.drawString(font, text, x + 3, y + 2, TEXT_COLOR, false);
    }

    private static void drawBorder(final GUIContext guiContext,
                                   final int x,
                                   final int y,
                                   final int width,
                                   final int height,
                                   final int color) {
        guiContext.graphics.fill(x, y, x + width, y + 1, color);
        guiContext.graphics.fill(x, y + height - 1, x + width, y + height, color);
        guiContext.graphics.fill(x, y, x + 1, y + height, color);
        guiContext.graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}

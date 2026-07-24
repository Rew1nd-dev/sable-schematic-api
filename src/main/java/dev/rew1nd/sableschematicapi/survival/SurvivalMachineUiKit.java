package dev.rew1nd.sableschematicapi.survival;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Shared LDLib styling primitives for survival machines. */
public final class SurvivalMachineUiKit {
    public static final float ROW_HEIGHT = 14;
    public static final float SLOT_SIZE = 18;
    public static final float GAP = 4;
    public static final int TEXT_COLOR = ColorPattern.WHITE.color;
    public static final int MUTED_TEXT_COLOR = ColorPattern.LIGHT_GRAY.color;
    public static final int ROOT_COLOR = ColorPattern.BLACK.color;
    public static final int PANEL_COLOR = ColorPattern.SEAL_BLACK.color;
    public static final int FIELD_COLOR = ColorPattern.DARK_GRAY.color;
    public static final int BORDER_COLOR = ColorPattern.GRAY.color;
    public static final int BUTTON_COLOR = ColorPattern.DARK_GRAY.color;
    public static final int BUTTON_HOVER_COLOR = ColorPattern.T_BLUE.color;
    public static final int BUTTON_PRESSED_COLOR = ColorPattern.SEAL_BLACK.color;

    private SurvivalMachineUiKit() {
    }

    public static UIElement row(final String id) {
        return new UIElement()
                .setId(id)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(3);
                });
    }

    public static Label label(final Component text, final Horizontal align, final int color) {
        final Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(1.0F)
                .textColor(color)
                .textShadow(false)
                .fontSize(8)
                .textAlignHorizontal(align)
                .textAlignVertical(Vertical.CENTER));
        label.style(style -> style.overflowVisible(false));
        return label;
    }

    public static Label dynamicLabel(final Supplier<Component> supplier, final int color) {
        return new DynamicLabel(supplier, color);
    }

    public static Button iconButton(final IGuiTexture icon, final Component tooltip) {
        final Button button = new Button();
        button.noText();
        button.addPreIcon(icon);
        button.style(style -> style.tooltips(tooltip));
        button.buttonStyle(style -> style
                .baseTexture(buttonTexture(BUTTON_COLOR, BORDER_COLOR))
                .hoverTexture(buttonTexture(BUTTON_HOVER_COLOR, BORDER_COLOR))
                .pressedTexture(buttonTexture(BUTTON_PRESSED_COLOR, BORDER_COLOR)));
        button.layout(layout -> {
            layout.width(SLOT_SIZE);
            layout.height(ROW_HEIGHT);
            layout.flexShrink(0);
            layout.paddingAll(0);
        });
        return button;
    }

    public static IGuiTexture panelBackground() {
        return SDFRectTexture.of(ROOT_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    public static IGuiTexture displayBackground() {
        return SDFRectTexture.of(PANEL_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    public static IGuiTexture buttonTexture(final int fill, final int border) {
        return SDFRectTexture.of(fill)
                .setBorderColor(border)
                .setRadius(2)
                .setStroke(1);
    }

    private static final class DynamicLabel extends Label {
        private final Supplier<Component> supplier;
        private Component previous;

        private DynamicLabel(final Supplier<Component> supplier, final int color) {
            this.supplier = supplier;
            this.previous = supplier.get();
            this.setText(this.previous);
            this.textStyle(style -> style
                    .adaptiveWidth(false)
                    .adaptiveHeight(false)
                    .textWrap(TextWrap.HOVER_ROLL)
                    .rollSpeed(1.0F)
                    .textColor(color)
                    .textShadow(false)
                    .fontSize(8)
                    .textAlignHorizontal(Horizontal.LEFT)
                    .textAlignVertical(Vertical.CENTER));
            this.style(style -> style.overflowVisible(false));
        }

        @Override
        public void screenTick() {
            super.screenTick();
            final Component next = this.supplier.get();
            if (!next.equals(this.previous)) {
                this.previous = next;
                this.setText(next);
            }
        }
    }
}

package dev.rew1nd.sableschematicapi.survival.client.ui;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

/** Stateless ORE controls shared by camera and manuscript blueprint libraries. */
public final class BlueprintLibraryUiKit {
    public static final float ROOT_WIDTH = 284.0F;
    public static final float ROOT_HEIGHT = 210.0F;
    public static final float CONTENT_WIDTH = 272.0F;
    public static final float STRING_HEIGHT = 12.0F;
    public static final float BUTTON_SIZE = 16.0F;
    public static final float GAP = 4.0F;
    public static final String ROOT_CLASS = "__blueprint_library_root__";
    public static final String STATE_CLASS = "__blueprint_library_state__";
    public static final String TILE_CLASS = "__blueprint_library_tile__";
    public static final String NAME_CLASS = "__blueprint_library_name__";
    public static final String PREVIEW_CLASS = "__blueprint_library_preview__";
    public static final String DETAILS_CLASS = "__blueprint_library_details__";
    public static final String OVERLAY_CLASS = "__blueprint_library_overlay__";
    public static final String LSS = """
            .__blueprint_library_root__ {
              background: built-in(ui-ore:BORDER_7);
            }

            .__blueprint_library_state__, .__blueprint_library_name__ {
              background: built-in(ui-ore:RECT2);
              padding-horizontal: 2;
            }

            .__blueprint_library_tile__ {
              background: empty;
              padding-all: 0;
            }

            .__blueprint_library_tile__:hovered {
              background: #66383838;
            }

            .__blueprint_library_preview__ {
              background: built-in(ui-ore:RECT2);
              padding-all: 2;
            }

            .__blueprint_library_details__ {
              background: built-in(ui-ore:RECT2);
              padding-all: 4;
            }

            .__blueprint_library_overlay__ {
              background: built-in(ui-ore:BORDER_7);
            }
            """;

    private BlueprintLibraryUiKit() {
    }

    public static UIElement row(final String id) {
        return new UIElement().setId(id).layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height(BUTTON_SIZE);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(GAP);
            layout.flexShrink(0.0F);
        });
    }

    public static Label title(final Component text) {
        final Label label = label(text, Horizontal.LEFT);
        label.layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height(STRING_HEIGHT);
        });
        return label;
    }

    public static Label label(final Component text, final Horizontal align) {
        final Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .textColor(0xFFFFFFFF)
                .textShadow(false)
                .fontSize(8)
                .textAlignHorizontal(align)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    public static Button iconButton(final IGuiTexture icon, final Component tooltip) {
        final Button button = new Button();
        button.noText();
        button.addPreIcon(icon);
        button.style(style -> style.tooltips(tooltip));
        button.layout(layout -> {
            layout.width(BUTTON_SIZE);
            layout.height(BUTTON_SIZE);
            layout.paddingAll(0);
            layout.flexShrink(0.0F);
        });
        return button;
    }

    public static TextField textField(final String id, final String value) {
        final TextField field = new TextField();
        field.setId(id);
        field.setText(value);
        field.textFieldStyle(style -> style
                .textColor(0xFFFFFFFF)
                .cursorColor(0xFFFFFFFF)
                .textShadow(false)
                .fontSize(8));
        field.layout(layout -> {
            layout.height(STRING_HEIGHT);
            layout.paddingHorizontal(2);
        });
        return field;
    }
}

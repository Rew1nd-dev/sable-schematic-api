package dev.rew1nd.sableschematicapi.survival.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Callback-driven four-column blueprint library grid. */
public final class BlueprintLibraryGrid<E> extends ScrollerView {
    private static final float CARD_WIDTH = 62.0F;
    private static final float PREVIEW_SIZE = 62.0F;
    private static final float CARD_HEIGHT = PREVIEW_SIZE + BlueprintLibraryUiKit.STRING_HEIGHT;

    private final Supplier<List<E>> entries;
    private final Function<E, String> name;
    private final Supplier<SableBlueprintPreview.View> view;
    private final Supplier<String> signature;
    private final BiConsumer<E, Integer> click;
    private final BlueprintPreviewTextureCache<E> previewTextures;
    private int ticks;
    private String renderedSignature = "";

    public BlueprintLibraryGrid(final float width,
                                final float height,
                                final Supplier<List<E>> entries,
                                final Function<E, String> name,
                                final Supplier<SableBlueprintPreview.View> view,
                                final Supplier<String> signature,
                                final BiConsumer<E, Integer> click,
                                final BlueprintPreviewTextureCache<E> previewTextures) {
        this.entries = Objects.requireNonNull(entries, "entries");
        this.name = Objects.requireNonNull(name, "name");
        this.view = Objects.requireNonNull(view, "view");
        this.signature = Objects.requireNonNull(signature, "signature");
        this.click = Objects.requireNonNull(click, "click");
        this.previewTextures = Objects.requireNonNull(previewTextures, "previewTextures");
        setId("sable_blueprint_library_grid");
        layout(layout -> {
            layout.width(width);
            layout.height(height);
        });
        scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .verticalScrollDisplay(ScrollDisplay.AUTO));
        viewContainer(container -> container.layout(layout -> {
            layout.widthPercent(100.0F);
            layout.heightAuto();
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(3.0F);
        }));
        rebuild();
    }

    @Override
    public void screenTick() {
        super.screenTick();
        if (++this.ticks % 10 == 0 && !this.signature.get().equals(this.renderedSignature)) {
            rebuild();
        }
    }

    public void rebuild() {
        clearAllScrollViewChildren();
        this.renderedSignature = this.signature.get();
        final List<E> current = this.entries.get();
        if (current.isEmpty()) {
            final Label empty = BlueprintLibraryUiKit.label(Component.literal("No local blueprints"), Horizontal.LEFT);
            empty.layout(layout -> {
                layout.widthPercent(100.0F);
                layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
            });
            addScrollViewChild(empty);
            return;
        }
        for (int start = 0; start < current.size(); start += 4) {
            final UIElement row = new UIElement().layout(layout -> {
                layout.widthPercent(100.0F);
                layout.height(CARD_HEIGHT);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(4.0F);
            });
            for (int index = start; index < Math.min(start + 4, current.size()); index++) {
                row.addChild(tile(current.get(index)));
            }
            addScrollViewChild(row);
        }
    }

    private UIElement tile(final E entry) {
        final UIElement tile = new UIElement()
                .addClass(BlueprintLibraryUiKit.TILE_CLASS)
                .layout(layout -> {
                    layout.width(CARD_WIDTH);
                    layout.height(CARD_HEIGHT);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.flexShrink(0.0F);
                });
        final PreviewCanvas preview = new PreviewCanvas(entry);
        preview.layout(layout -> {
            layout.width(PREVIEW_SIZE);
            layout.height(PREVIEW_SIZE);
        });
        final Label label = BlueprintLibraryUiKit.label(Component.literal(this.name.apply(entry)), Horizontal.CENTER);
        label.addClass(BlueprintLibraryUiKit.NAME_CLASS);
        label.layout(layout -> {
            layout.width(CARD_WIDTH);
            layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
        });
        tile.addChildren(preview, label);
        tile.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            this.click.accept(entry, event.button);
            event.stopPropagation();
        });
        return tile;
    }

    private final class PreviewCanvas extends UIElement {
        private final E entry;

        private PreviewCanvas(final E entry) {
            this.entry = entry;
        }

        @Override
        public void drawBackgroundAdditional(final GUIContext context) {
            previewTextures.draw(
                    context,
                    this.entry,
                    view.get(),
                    Math.round(getContentX()),
                    Math.round(getContentY()),
                    Math.max(1, Math.round(getContentWidth())),
                    Math.max(1, Math.round(getContentHeight()))
            );
        }
    }
}

package dev.rew1nd.sableschematicapi.survival.client.ui;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

/** Generic callback-driven editable blueprint details overlay. */
public final class BlueprintDetailsDialog<E> extends UIElement {
    private static final float WIDTH = 278.0F;
    private static final float HEIGHT = 166.0F;
    private static final float PREVIEW_SIZE = 94.0F;
    private static final float RIGHT_WIDTH = 158.0F;
    private static final float LABEL_WIDTH = 34.0F;

    private final Adapter<E> adapter;
    private final BlueprintPreviewTextureCache<E> previewTextures;
    private final Consumer<SableBlueprintPreview.View> viewChanged;
    private final Label title;
    private final TextField name;
    private final TextField author;
    private final TextArea description;
    private E entry;
    private SableBlueprintPreview.View view = SableBlueprintPreview.View.ISO_XP_ZP;
    private boolean confirmOverwrite;

    public BlueprintDetailsDialog(final String id,
                                  final Adapter<E> adapter,
                                  final BlueprintPreviewTextureCache<E> previewTextures,
                                  final Consumer<SableBlueprintPreview.View> viewChanged) {
        this.adapter = adapter;
        this.previewTextures = previewTextures;
        this.viewChanged = viewChanged == null ? ignored -> {
        } : viewChanged;
        setId(id);
        addClass(BlueprintLibraryUiKit.OVERLAY_CLASS);
        layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(3.0F);
            layout.top(27.0F);
            layout.width(WIDTH);
            layout.height(HEIGHT);
            layout.paddingAll(6.0F);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(BlueprintLibraryUiKit.GAP);
        });

        this.title = BlueprintLibraryUiKit.label(Component.empty(), Horizontal.LEFT);
        this.title.layout(layout -> {
            layout.flex(1.0F);
            layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
        });
        final Button close = BlueprintLibraryUiKit.iconButton(Icons.CLOSE, Component.literal("Save and close"));
        close.setOnClick(event -> saveAndClose());
        final UIElement header = BlueprintLibraryUiKit.row(id + "_header").addChildren(this.title, close);

        final PreviewCanvas canvas = new PreviewCanvas();
        canvas.layout(layout -> {
            layout.widthPercent(100.0F);
            layout.heightPercent(100.0F);
        });
        final UIElement previewFrame = new UIElement()
                .addClass(BlueprintLibraryUiKit.PREVIEW_CLASS)
                .layout(layout -> {
                    layout.width(PREVIEW_SIZE);
                    layout.height(PREVIEW_SIZE);
                })
                .addChild(canvas);
        final Button previous = BlueprintLibraryUiKit.iconButton(Icons.LEFT, Component.literal("Previous preview angle"));
        previous.setOnClick(event -> shiftView(-1));
        final Button next = BlueprintLibraryUiKit.iconButton(Icons.RIGHT, Component.literal("Next preview angle"));
        next.setOnClick(event -> shiftView(1));
        final UIElement navigation = new UIElement().layout(layout -> {
            layout.width(PREVIEW_SIZE);
            layout.height(BlueprintLibraryUiKit.BUTTON_SIZE);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
        }).addChildren(previous, next);
        final UIElement previewColumn = new UIElement().layout(layout -> {
            layout.width(PREVIEW_SIZE);
            layout.height(PREVIEW_SIZE + BlueprintLibraryUiKit.BUTTON_SIZE + BlueprintLibraryUiKit.GAP);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(BlueprintLibraryUiKit.GAP);
        }).addChildren(previewFrame, navigation);

        this.name = BlueprintLibraryUiKit.textField(id + "_name", "");
        this.author = BlueprintLibraryUiKit.textField(id + "_author", "");
        this.name.layout(layout -> {
            layout.flex(1.0F);
            layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
        });
        this.author.layout(layout -> {
            layout.flex(1.0F);
            layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
        });
        this.description = textArea(id + "_description", RIGHT_WIDTH, 72.0F);
        final UIElement fields = new UIElement().layout(layout -> {
            layout.width(RIGHT_WIDTH);
            layout.heightAuto();
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(BlueprintLibraryUiKit.GAP);
        }).addChildren(
                fieldRow(id, "Name", this.name),
                fieldRow(id, "Author", this.author),
                fieldLabel("Description", RIGHT_WIDTH),
                this.description
        );
        final UIElement body = new UIElement().layout(layout -> {
            layout.widthPercent(100.0F);
            layout.flex(1.0F);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.FLEX_START);
            layout.gapAll(8.0F);
        }).addChildren(previewColumn, fields);
        addChildren(header, body);
        hide();
    }

    public void open(final E entry, final SableBlueprintPreview.View view) {
        if (this.entry != null && !this.entry.equals(entry)) {
            saveDraft(false);
        }
        this.entry = entry;
        this.view = view == null ? SableBlueprintPreview.View.ISO_XP_ZP : view;
        this.confirmOverwrite = false;
        reload();
        setVisible(true);
        setDisplay(true);
    }

    @Override
    protected void onRemoved() {
        saveDraft(false);
        super.onRemoved();
    }

    private void saveAndClose() {
        if (saveDraft(true)) {
            hide();
        }
    }

    private void hide() {
        this.entry = null;
        setVisible(false);
        setDisplay(false);
    }

    private void reload() {
        if (this.entry == null) {
            return;
        }
        this.title.setText(Component.literal(this.adapter.name(this.entry)));
        this.name.setText(this.adapter.name(this.entry));
        try {
            final Details details = this.adapter.details(this.entry);
            this.author.setText(details.author());
            this.description.setLines(Arrays.asList(lines(details.description())));
        } catch (final IOException exception) {
            this.author.setText("");
            this.description.setLines(java.util.List.of(""));
        }
    }

    private void shiftView(final int delta) {
        final SableBlueprintPreview.View[] values = SableBlueprintPreview.View.values();
        this.view = values[Math.floorMod(this.view.ordinal() + delta, values.length)];
        this.viewChanged.accept(this.view);
    }

    private boolean saveDraft(final boolean allowConfirmation) {
        if (this.entry == null) {
            return true;
        }
        try {
            this.entry = this.adapter.save(
                    this.entry,
                    this.name.getText().trim(),
                    this.author.getText(),
                    String.join("\n", this.description.getValue()),
                    this.confirmOverwrite
            );
            this.confirmOverwrite = false;
            return true;
        } catch (final java.nio.file.FileAlreadyExistsException exception) {
            if (allowConfirmation) {
                this.confirmOverwrite = true;
                this.title.setText(Component.literal("Name exists; close again to overwrite."));
                return false;
            }
            return true;
        } catch (final IOException exception) {
            this.adapter.onError(exception);
            return !allowConfirmation;
        }
    }

    private static UIElement fieldRow(final String id, final String name, final TextField field) {
        return BlueprintLibraryUiKit.row(id + "_" + name.toLowerCase(Locale.ROOT))
                .addChildren(fieldLabel(name, LABEL_WIDTH), field);
    }

    private static Label fieldLabel(final String text, final float width) {
        final Label label = BlueprintLibraryUiKit.label(Component.literal(text), Horizontal.LEFT);
        label.layout(layout -> {
            layout.width(width);
            layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
            layout.flexShrink(0.0F);
        });
        return label;
    }

    private static TextArea textArea(final String id, final float width, final float height) {
        final TextArea area = new TextArea();
        area.setId(id);
        area.textAreaStyle(style -> style
                .textColor(0xFFFFFFFF)
                .cursorColor(0xFFFFFFFF)
                .textShadow(false)
                .fontSize(8)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .viewMode(ScrollerMode.VERTICAL)
                .lineSpacing(1.0F));
        area.layout(layout -> {
            layout.width(width);
            layout.height(height);
        });
        return area;
    }

    private static String[] lines(final String value) {
        return value == null || value.isEmpty() ? new String[]{""} : value.split("\\R", -1);
    }

    public interface Adapter<E> {
        String name(E entry);

        Details details(E entry) throws IOException;

        E save(E entry, String name, String author, String description, boolean overwrite) throws IOException;

        default void onError(final IOException exception) {
        }
    }

    public record Details(String author, String description) {
        public Details {
            author = author == null ? "" : author;
            description = description == null ? "" : description;
        }
    }

    private final class PreviewCanvas extends UIElement {
        @Override
        public void drawBackgroundAdditional(final GUIContext context) {
            context.graphics.flush();
            previewTextures.draw(
                    context,
                    entry,
                    view,
                    Math.round(getContentX()),
                    Math.round(getContentY()),
                    Math.max(1, Math.round(getContentWidth())),
                    Math.max(1, Math.round(getContentHeight()))
            );
            context.graphics.flush();
        }
    }
}

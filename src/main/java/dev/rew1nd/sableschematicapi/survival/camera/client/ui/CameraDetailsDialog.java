package dev.rew1nd.sableschematicapi.survival.camera.client.ui;

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
import dev.rew1nd.sableschematicapi.survival.camera.CameraState;
import dev.rew1nd.sableschematicapi.survival.camera.client.CameraClientSession;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolLocalPreviewCache;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalMetadata;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.util.Arrays;

/** Compact auto-saving local-blueprint details overlay. */
final class CameraDetailsDialog extends UIElement {
    private static final float WIDTH = 278.0F;
    private static final float HEIGHT = 166.0F;
    private static final float PREVIEW_SIZE = 94.0F;
    private static final float RIGHT_WIDTH = 158.0F;
    private static final float LABEL_WIDTH = 34.0F;

    private final Player player;
    private final CameraPreviewTextureCache previewTextures;
    private final Label title;
    private final PreviewCanvas canvas;
    private final TextField name;
    private final TextField author;
    private final TextArea description;
    private BlueprintToolLocalFiles.Entry entry;
    private SableBlueprintPreview.View view = SableBlueprintPreview.View.ISO_XP_ZP;
    private boolean confirmOverwrite;

    CameraDetailsDialog(final Player player, final CameraPreviewTextureCache previewTextures) {
        this.player = player;
        this.previewTextures = previewTextures;
        setId("sable_camera_details");
        addClass(CameraUiRenderer.OVERLAY_CLASS);
        layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(3.0F);
            layout.top(27.0F);
            layout.width(WIDTH);
            layout.height(HEIGHT);
            layout.paddingAll(6.0F);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(CameraUiRenderer.GAP);
        });

        this.title = CameraUiRenderer.label(Component.empty(), Horizontal.LEFT);
        this.title.layout(layout -> {
            layout.flex(1.0F);
            layout.height(CameraUiRenderer.STRING_HEIGHT);
        });
        final Button close = CameraUiRenderer.iconButton(Icons.CLOSE, Component.literal("Save and close"));
        final UIElement header = CameraUiRenderer.row("sable_camera_details_header")
                .addChildren(this.title, close);
        close.setOnClick(event -> saveAndClose());

        this.canvas = new PreviewCanvas();
        this.canvas.addClass(CameraUiRenderer.PREVIEW_CLASS);
        this.canvas.layout(layout -> {
            layout.width(PREVIEW_SIZE);
            layout.height(PREVIEW_SIZE);
        });
        final Button previous = CameraUiRenderer.iconButton(Icons.LEFT, Component.literal("Previous preview angle"));
        previous.setOnClick(event -> shiftView(-1));
        final Button next = CameraUiRenderer.iconButton(Icons.RIGHT, Component.literal("Next preview angle"));
        next.setOnClick(event -> shiftView(1));
        final UIElement navigation = new UIElement().layout(layout -> {
            layout.width(PREVIEW_SIZE);
            layout.height(CameraUiRenderer.BUTTON_SIZE);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
        }).addChildren(previous, next);
        final UIElement previewColumn = new UIElement().layout(layout -> {
            layout.width(PREVIEW_SIZE);
            layout.height(PREVIEW_SIZE + CameraUiRenderer.BUTTON_SIZE + CameraUiRenderer.GAP);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(CameraUiRenderer.GAP);
        }).addChildren(this.canvas, navigation);

        this.name = CameraUiRenderer.textField("sable_camera_blueprint_name", "");
        this.author = CameraUiRenderer.textField("sable_camera_blueprint_author", "");
        this.name.layout(layout -> {
            layout.flex(1.0F);
            layout.height(CameraUiRenderer.STRING_HEIGHT);
        });
        this.author.layout(layout -> {
            layout.flex(1.0F);
            layout.height(CameraUiRenderer.STRING_HEIGHT);
        });
        this.description = textArea("sable_camera_blueprint_description", RIGHT_WIDTH, 72.0F);

        final UIElement fields = new UIElement().layout(layout -> {
            layout.width(RIGHT_WIDTH);
            layout.heightAuto();
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(CameraUiRenderer.GAP);
        }).addChildren(
                fieldRow("Name", this.name),
                fieldRow("Author", this.author),
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

    void open(final BlueprintToolLocalFiles.Entry entry) {
        if (this.entry != null && !this.entry.equals(entry)) {
            saveDraft(false);
        }
        this.entry = entry;
        this.confirmOverwrite = false;
        this.view = readView(CameraState.read(player.getMainHandItem()));
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
        if (entry == null) {
            return;
        }
        this.title.setText(Component.literal(entry.name()));
        this.name.setText(entry.name());
        try {
            final BlueprintToolLocalMetadata.Metadata metadata = BlueprintToolLocalMetadata.read(entry);
            this.author.setText(metadata.author());
            this.description.setLines(Arrays.asList(lines(metadata.description())));
        } catch (final IOException ignored) {
            this.author.setText("");
            this.description.setLines(java.util.List.of(""));
        }
    }

    private void shiftView(final int delta) {
        final SableBlueprintPreview.View[] values = SableBlueprintPreview.View.values();
        this.view = values[Math.floorMod(this.view.ordinal() + delta, values.length)];
        CameraClientSession.requestPreviewView(this.view.name());
    }

    /**
     * @param allowConfirmation whether a duplicate name may arm/consume a second close click
     * @return true when the overlay may close
     */
    private boolean saveDraft(final boolean allowConfirmation) {
        if (entry == null) {
            return true;
        }
        final String requestedName = name.getText().trim();
        try {
            if (!requestedName.isEmpty() && !requestedName.equalsIgnoreCase(entry.name())) {
                if (nameExists(requestedName) && !confirmOverwrite) {
                    if (allowConfirmation) {
                        confirmOverwrite = true;
                        title.setText(Component.literal("Name exists; close again to overwrite."));
                        return false;
                    }
                } else {
                    entry = BlueprintToolLocalFiles.rename(entry, requestedName, confirmOverwrite);
                    CameraClientSession.requestSelectLocal(entry.name());
                }
            }
            BlueprintToolLocalMetadata.write(entry, author.getText(), String.join("\n", description.getValue()));
            BlueprintToolLocalPreviewCache.invalidate(entry);
            confirmOverwrite = false;
            return true;
        } catch (final IOException e) {
            player.displayClientMessage(Component.literal("Could not save blueprint details.").withStyle(ChatFormatting.RED), true);
            return !allowConfirmation;
        }
    }

    private boolean nameExists(final String requestedName) throws IOException {
        return BlueprintToolLocalFiles.list().stream()
                .anyMatch(candidate -> !candidate.path().equals(entry.path()) && candidate.name().equalsIgnoreCase(requestedName));
    }

    private UIElement fieldRow(final String name, final TextField field) {
        final UIElement row = CameraUiRenderer.row("sable_camera_details_" + name.toLowerCase(java.util.Locale.ROOT));
        final Label label = fieldLabel(name, LABEL_WIDTH);
        row.addChildren(label, field);
        return row;
    }

    private static Label fieldLabel(final String text, final float width) {
        final Label label = CameraUiRenderer.label(Component.literal(text), Horizontal.LEFT);
        label.layout(layout -> {
            layout.width(width);
            layout.height(CameraUiRenderer.STRING_HEIGHT);
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

    private static SableBlueprintPreview.View readView(final CameraState state) {
        try {
            return SableBlueprintPreview.View.valueOf(state.previewView());
        } catch (final IllegalArgumentException ignored) {
            return SableBlueprintPreview.View.ISO_XP_ZP;
        }
    }

    private static String[] lines(final String value) {
        return value == null || value.isEmpty() ? new String[]{""} : value.split("\\R", -1);
    }

    private final class PreviewCanvas extends UIElement {
        @Override
        public void drawBackgroundAdditional(final GUIContext context) {
            final int x = Math.round(getContentX());
            final int y = Math.round(getContentY());
            final int width = Math.max(1, Math.round(getContentWidth()));
            final int height = Math.max(1, Math.round(getContentHeight()));
            previewTextures.draw(context, entry, view, x, y, width, height);
        }
    }
}

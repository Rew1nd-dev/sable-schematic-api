package dev.rew1nd.sableschematicapi.survival.manuscript.client.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintDetailsDialog;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintLibraryGrid;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintLibraryUiKit;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintPreviewTextureCache;
import dev.rew1nd.sableschematicapi.survival.manuscript.client.ManuscriptClientSession;
import dev.rew1nd.sableschematicapi.survival.manuscript.client.RemoteBlueprintPreviewCache;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolLocalPreviewCache;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalMetadata;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Empty local library and filled remote-reference views for a manuscript. */
public final class BlueprintManuscriptUiRenderer {
    private static final float FILLED_CONTENT_HEIGHT = 148.0F;
    private static final float FILLED_ROOT_HEIGHT = FILLED_CONTENT_HEIGHT + 12.0F;

    private BlueprintManuscriptUiRenderer() {
    }

    public static void open() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new ModularUIScreen(
                    renderClient(minecraft.player),
                    Component.translatable("item.sable_schematic_api.blueprint_manuscript")
            ));
        }
    }

    private static ModularUI renderClient(final Player player) {
        final ManuscriptRoot root = new ManuscriptRoot(player);
        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.ORE_MERGED))),
                player
        );
    }

    private static final class ManuscriptRoot extends UIElement {
        private final Player player;
        private final BlueprintPreviewTextureCache<BlueprintToolLocalFiles.Entry> localTextures =
                new BlueprintPreviewTextureCache<>("sable_manuscript_local_preview", BlueprintToolLocalPreviewCache::preview);
        private final BlueprintPreviewTextureCache<RemoteBlueprintPreviewCache.Entry> remoteTextures =
                new BlueprintPreviewTextureCache<>("sable_manuscript_remote_preview", RemoteBlueprintPreviewCache.Entry::preview);
        private BlueprintToolLocalFiles.Entry selected;
        private SableBlueprintPreview.View localView = SableBlueprintPreview.View.ISO_XP_ZP;
        private int renderedRevision = Integer.MIN_VALUE;
        private int ticks;

        private ManuscriptRoot(final Player player) {
            this.player = player;
            setId("sable_blueprint_manuscript_root");
            addLocalStylesheet(BlueprintLibraryUiKit.LSS);
            layout(layout -> {
                layout.width(BlueprintLibraryUiKit.ROOT_WIDTH);
                layout.height(BlueprintLibraryUiKit.ROOT_HEIGHT);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.CENTER);
                layout.justifyContent(AlignContent.CENTER);
            });
            rebuild();
        }

        @Override
        public void screenTick() {
            super.screenTick();
            if (++this.ticks % 2 == 0 && this.renderedRevision != ManuscriptClientSession.revision()) {
                rebuild();
            }
        }

        @Override
        protected void onRemoved() {
            ManuscriptClientSession.close();
            this.localTextures.close();
            this.remoteTextures.close();
            super.onRemoved();
        }

        private void rebuild() {
            clearAllChildren();
            this.renderedRevision = ManuscriptClientSession.revision();
            if (ManuscriptClientSession.state().filled()) {
                buildFilled();
            } else {
                buildEmpty();
            }
        }

        private void buildEmpty() {
            final UIElement frame = frame(BlueprintLibraryUiKit.ROOT_HEIGHT);
            final Label selection = BlueprintLibraryUiKit.label(
                    Component.literal(this.selected == null ? "No blueprint selected" : "Selected: " + this.selected.name()),
                    Horizontal.LEFT
            );
            selection.addClass(BlueprintLibraryUiKit.STATE_CLASS);
            selection.layout(layout -> {
                layout.flex(1.0F);
                layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
            });
            final Button upload = BlueprintLibraryUiKit.iconButton(Icons.UP, Component.literal("Upload to this manuscript"));
            upload.setOnClick(event -> {
                if (this.selected != null) {
                    ManuscriptClientSession.upload(this.selected);
                }
            });
            final UIElement toolbar = BlueprintLibraryUiKit.row("sable_manuscript_toolbar")
                    .addChildren(upload, selection);

            final BlueprintDetailsDialog<BlueprintToolLocalFiles.Entry> details =
                    new BlueprintDetailsDialog<>(
                            "sable_manuscript_local_details",
                            localDetailsAdapter(),
                            this.localTextures,
                            view -> this.localView = view
                    );
            final BlueprintLibraryGrid<BlueprintToolLocalFiles.Entry> grid = new BlueprintLibraryGrid<>(
                    BlueprintLibraryUiKit.CONTENT_WIDTH,
                    164.0F,
                    BlueprintManuscriptUiRenderer::localEntries,
                    BlueprintToolLocalFiles.Entry::name,
                    () -> this.localView,
                    BlueprintManuscriptUiRenderer::localSignature,
                    (entry, button) -> {
                        if (button == 0) {
                            this.selected = entry;
                            selection.setText(Component.literal("Selected: " + entry.name()));
                        } else if (button == 1) {
                            details.open(entry, this.localView);
                        }
                    },
                    this.localTextures
            );
            frame.addChildren(
                    BlueprintLibraryUiKit.title(Component.translatable(
                            "item.sable_schematic_api.blueprint_manuscript"
                    )),
                    toolbar,
                    grid,
                    details
            );
            addOverwriteDialogIfNeeded(frame);
            addChild(frame);
        }

        private void buildFilled() {
            final UIElement frame = frame(FILLED_ROOT_HEIGHT);
            final ManuscriptClientSession.ScreenState state = ManuscriptClientSession.state();
            final RemoteBlueprintPreviewCache.Entry detail = ManuscriptClientSession.details();
            final SableBlueprintPreview.View view = previewView(state.rotation());

            final RemotePreviewCanvas canvas = new RemotePreviewCanvas(detail, view);
            canvas.addClass(BlueprintLibraryUiKit.PREVIEW_CLASS);
            canvas.layout(layout -> {
                layout.width(128.0F);
                layout.height(128.0F);
            });
            final Button left = BlueprintLibraryUiKit.iconButton(Icons.LEFT, Component.literal("Rotate left"));
            left.setOnClick(event -> ManuscriptClientSession.rotateLeft());
            final Button right = BlueprintLibraryUiKit.iconButton(Icons.RIGHT, Component.literal("Rotate right"));
            right.setOnClick(event -> ManuscriptClientSession.rotateRight());
            final UIElement rotation = new UIElement().layout(layout -> {
                layout.width(128.0F);
                layout.height(BlueprintLibraryUiKit.BUTTON_SIZE);
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.CENTER);
                layout.justifyContent(AlignContent.SPACE_BETWEEN);
            }).addChildren(left, right);
            final UIElement previewColumn = new UIElement().layout(layout -> {
                layout.width(128.0F);
                layout.height(FILLED_CONTENT_HEIGHT);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(BlueprintLibraryUiKit.GAP);
            }).addChildren(canvas, rotation);

            final UIElement metadataPanel = new UIElement()
                    .addClass(BlueprintLibraryUiKit.DETAILS_CLASS)
                    .layout(layout -> {
                layout.widthPercent(100.0F);
                layout.height(128.0F);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(2.0F);
            });
            metadataPanel.addChild(info(
                    "Name: " + (state.alias() == null
                            ? "Broken blueprint reference"
                            : state.alias().blueprintName())
            ));
            if (detail == null) {
                metadataPanel.addChild(info("Blueprint unavailable or preview is loading."));
            } else {
                metadataPanel.addChildren(
                        info("Author: " + blank(detail.author())),
                        info("Owner: " + (state.alias() == null ? "-" : state.alias().ownerName())),
                        info("Rotation: " + state.rotation().degrees() + "°"),
                        info("Blocks: " + detail.summary().blocks()),
                        info("Sub-levels: " + detail.summary().subLevels()),
                        info("Entities: " + detail.summary().entities()),
                        info(detail.description().isBlank() ? "No description" : detail.description())
                );
            }
            final Button discard = BlueprintLibraryUiKit.iconButton(Icons.DELETE, Component.literal("Discard manuscript reference"));
            final UIElement discardDialog = discardDialog();
            discard.setOnClick(event -> {
                discardDialog.setVisible(true);
                discardDialog.setDisplay(true);
            });
            final UIElement actions = new UIElement().layout(layout -> {
                layout.widthPercent(100.0F);
                layout.height(BlueprintLibraryUiKit.BUTTON_SIZE);
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.CENTER);
                layout.justifyContent(AlignContent.FLEX_END);
            }).addChild(discard);

            final UIElement metadataColumn = new UIElement().layout(layout -> {
                layout.flex(1.0F);
                layout.height(FILLED_CONTENT_HEIGHT);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(BlueprintLibraryUiKit.GAP);
            }).addChildren(metadataPanel, actions);

            final UIElement body = new UIElement().layout(layout -> {
                layout.widthPercent(100.0F);
                layout.height(FILLED_CONTENT_HEIGHT);
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.STRETCH);
                layout.gapAll(8.0F);
                layout.flexShrink(0.0F);
            }).addChildren(previewColumn, metadataColumn);
            frame.addChildren(body, discardDialog);
            addChild(frame);
        }

        private UIElement frame(final float height) {
            return new UIElement()
                    .addClass(BlueprintLibraryUiKit.ROOT_CLASS)
                    .layout(layout -> {
                        layout.width(BlueprintLibraryUiKit.ROOT_WIDTH);
                        layout.height(height);
                        layout.paddingAll(6.0F);
                        layout.flexDirection(FlexDirection.COLUMN);
                        layout.alignItems(AlignItems.STRETCH);
                        layout.gapAll(BlueprintLibraryUiKit.GAP);
                        layout.flexShrink(0.0F);
                    });
        }

        private Label info(final String value) {
            final Label label = BlueprintLibraryUiKit.label(Component.literal(value), Horizontal.LEFT);
            label.layout(layout -> {
                layout.widthPercent(100.0F);
                layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
            });
            return label;
        }

        private UIElement discardDialog() {
            final UIElement dialog = confirmationDialog("Discard this manuscript reference?");
            final Button confirm = BlueprintLibraryUiKit.iconButton(Icons.CHECK, Component.literal("Discard"));
            confirm.setOnClick(event -> ManuscriptClientSession.discard());
            final Button cancel = BlueprintLibraryUiKit.iconButton(Icons.CLOSE, Component.literal("Cancel"));
            cancel.setOnClick(event -> {
                dialog.setVisible(false);
                dialog.setDisplay(false);
            });
            dialog.addChildren(confirm, cancel);
            dialog.setVisible(false);
            dialog.setDisplay(false);
            return dialog;
        }

        private void addOverwriteDialogIfNeeded(final UIElement frame) {
            if (ManuscriptClientSession.overwriteRequest() == null) {
                return;
            }
            final UIElement dialog = confirmationDialog("Overwrite the server blueprint?");
            final Button confirm = BlueprintLibraryUiKit.iconButton(Icons.CHECK, Component.literal("Overwrite"));
            confirm.setOnClick(event -> ManuscriptClientSession.confirmOverwrite());
            final Button cancel = BlueprintLibraryUiKit.iconButton(Icons.CLOSE, Component.literal("Cancel"));
            cancel.setOnClick(event -> {
                ManuscriptClientSession.cancelUpload();
                dialog.setVisible(false);
                dialog.setDisplay(false);
            });
            dialog.addChildren(confirm, cancel);
            frame.addChild(dialog);
        }

        private UIElement confirmationDialog(final String message) {
            final UIElement dialog = new UIElement()
                    .addClass(BlueprintLibraryUiKit.OVERLAY_CLASS)
                    .layout(layout -> {
                        layout.positionType(TaffyPosition.ABSOLUTE);
                        layout.left(38.0F);
                        layout.top(82.0F);
                        layout.width(208.0F);
                        layout.height(28.0F);
                        layout.paddingAll(6.0F);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.alignItems(AlignItems.CENTER);
                        layout.gapAll(BlueprintLibraryUiKit.GAP);
                    });
            final Label label = BlueprintLibraryUiKit.label(Component.literal(message), Horizontal.LEFT);
            label.layout(layout -> {
                layout.flex(1.0F);
                layout.height(BlueprintLibraryUiKit.STRING_HEIGHT);
            });
            dialog.addChild(label);
            return dialog;
        }

        private BlueprintDetailsDialog.Adapter<BlueprintToolLocalFiles.Entry> localDetailsAdapter() {
            return new BlueprintDetailsDialog.Adapter<>() {
                @Override
                public String name(final BlueprintToolLocalFiles.Entry entry) {
                    return entry.name();
                }

                @Override
                public BlueprintDetailsDialog.Details details(final BlueprintToolLocalFiles.Entry entry) throws IOException {
                    final BlueprintToolLocalMetadata.Metadata metadata = BlueprintToolLocalMetadata.read(entry);
                    return new BlueprintDetailsDialog.Details(metadata.author(), metadata.description());
                }

                @Override
                public BlueprintToolLocalFiles.Entry save(final BlueprintToolLocalFiles.Entry entry,
                                                          final String name,
                                                          final String author,
                                                          final String description,
                                                          final boolean overwrite) throws IOException {
                    BlueprintToolLocalFiles.Entry saved = entry;
                    if (!name.isBlank() && !name.equalsIgnoreCase(entry.name())) {
                        saved = BlueprintToolLocalFiles.rename(entry, name, overwrite);
                    }
                    BlueprintToolLocalMetadata.write(saved, author, description);
                    BlueprintToolLocalPreviewCache.invalidate(saved);
                    if (selected != null && selected.path().equals(entry.path())) {
                        selected = saved;
                    }
                    return saved;
                }

                @Override
                public void onError(final IOException exception) {
                    player.displayClientMessage(
                            Component.literal("Could not save blueprint details.").withStyle(ChatFormatting.RED),
                            true
                    );
                }
            };
        }

        private final class RemotePreviewCanvas extends UIElement {
            private final RemoteBlueprintPreviewCache.Entry detail;
            private final SableBlueprintPreview.View view;

            private RemotePreviewCanvas(final RemoteBlueprintPreviewCache.Entry detail,
                                        final SableBlueprintPreview.View view) {
                this.detail = detail;
                this.view = view;
            }

            @Override
            public void drawBackgroundAdditional(final GUIContext context) {
                context.graphics.flush();
                remoteTextures.draw(
                        context,
                        this.detail,
                        this.view,
                        Math.round(getContentX()),
                        Math.round(getContentY()),
                        Math.max(1, Math.round(getContentWidth())),
                        Math.max(1, Math.round(getContentHeight()))
                );
                context.graphics.flush();
            }
        }
    }

    private static List<BlueprintToolLocalFiles.Entry> localEntries() {
        try {
            return BlueprintToolLocalFiles.list();
        } catch (final IOException exception) {
            return List.of();
        }
    }

    private static String localSignature() {
        final StringBuilder signature = new StringBuilder();
        for (final BlueprintToolLocalFiles.Entry entry : localEntries()) {
            signature.append(entry.name()).append(':');
            try {
                signature.append(Files.getLastModifiedTime(entry.path()).toMillis());
            } catch (final IOException ignored) {
                signature.append('0');
            }
            signature.append('|');
        }
        return signature.toString();
    }

    private static String blank(final String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static SableBlueprintPreview.View previewView(
            final dev.rew1nd.sableschematicapi.survival.manuscript.QuarterTurn rotation) {
        return switch (rotation) {
            case DEG_0 -> SableBlueprintPreview.View.ISO_XP_ZP;
            case DEG_90 -> SableBlueprintPreview.View.ISO_XP_ZN;
            case DEG_180 -> SableBlueprintPreview.View.ISO_XN_ZN;
            case DEG_270 -> SableBlueprintPreview.View.ISO_XN_ZP;
        };
    }
}

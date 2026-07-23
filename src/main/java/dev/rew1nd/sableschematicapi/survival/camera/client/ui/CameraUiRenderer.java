package dev.rew1nd.sableschematicapi.survival.camera.client.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.survival.camera.CameraState;
import dev.rew1nd.sableschematicapi.survival.camera.client.CameraClientEvents;
import dev.rew1nd.sableschematicapi.survival.camera.client.CameraClientSession;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.util.List;

/** ORE-styled camera library whose controls and chrome are entirely LDLib-owned. */
public final class CameraUiRenderer {
    static final float ROOT_WIDTH = 284.0F;
    static final float ROOT_HEIGHT = 210.0F;
    static final float CONTENT_WIDTH = 272.0F;
    static final float STRING_HEIGHT = 12.0F;
    static final float BUTTON_SIZE = 16.0F;
    static final float GAP = 4.0F;
    static final String ROOT_CLASS = "__camera_root__";
    static final String STATE_CLASS = "__camera_state__";
    static final String TILE_CLASS = "__camera_tile__";
    static final String NAME_CLASS = "__camera_name__";
    static final String PREVIEW_CLASS = "__camera_preview__";
    static final String OVERLAY_CLASS = "__camera_overlay__";
    static final String SAVE_OVERLAY_CLASS = "__camera_save_overlay__";
    static final String LSS = """
            .__camera_root__ {
              background: built-in(ui-ore:BORDER_7);
            }

            .__camera_state__, .__camera_name__ {
              background: built-in(ui-ore:RECT2);
              padding-horizontal: 2;
            }

            .__camera_tile__ {
              background: empty;
              padding-all: 0;
            }

            .__camera_tile__:hovered {
              background: #66383838;
            }

            .__camera_preview__ {
              background: built-in(ui-ore:RECT2);
              padding-all: 2;
            }

            .__camera_overlay__, .__camera_save_overlay__ {
              background: built-in(ui-ore:BORDER_7);
            }
            """;

    private CameraUiRenderer() {
    }

    public static void open() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player != null) {
            minecraft.setScreen(new ModularUIScreen(renderClient(player), Component.translatable("item.sable_schematic_api.camera")));
        }
    }

    public static ModularUI renderClient(final Player player) {
        final CameraPreviewTextureCache previewTextures = new CameraPreviewTextureCache();
        final UIElement root = new UIElement() {
                    @Override
                    protected void onRemoved() {
                        super.onRemoved();
                        previewTextures.close();
                    }
                }
                .setId("sable_camera_root")
                .addClass(ROOT_CLASS)
                .addLocalStylesheet(LSS)
                .layout(layout -> {
                    layout.width(ROOT_WIDTH);
                    layout.height(ROOT_HEIGHT);
                    layout.paddingAll(6);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(GAP);
                });

        final UIElement saveDialog = saveDialog(player);
        final CameraDetailsDialog details = new CameraDetailsDialog(player, previewTextures);
        final Button save = iconButton(Icons.SAVE, Component.literal("Save camera cache"));
        save.setOnClick(event -> {
            final CameraState state = CameraState.read(player.getMainHandItem());
            if (state.hasCaptureFor(player.getUUID())) {
                saveDialog.setVisible(true);
                saveDialog.setDisplay(true);
            }
        });
        final Button preview = iconButton(Icons.EYE, Component.literal("Preview captured bodies"));
        preview.setOnClick(event -> {
            CameraClientEvents.showCachedPreview(CameraState.read(player.getMainHandItem()).captureId());
            Minecraft.getInstance().setScreen(null);
        });

        final UIElement toolbar = row("sable_camera_toolbar")
                .addChildren(save, preview, stateLabel(player));
        root.addChildren(
                title(Component.translatable("item.sable_schematic_api.camera")),
                toolbar,
                new CameraBlueprintGrid(player, CONTENT_WIDTH, 164.0F, details, previewTextures),
                saveDialog,
                details
        );
        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.ORE_MERGED))),
                player
        );
    }

    private static Label stateLabel(final Player player) {
        final Label label = label(Component.empty(), Horizontal.LEFT);
        label.addClass(STATE_CLASS);
        label.bindDataSource(SupplierDataSource.<Component>of(() -> {
            final CameraState state = CameraState.read(player.getMainHandItem());
            if (state.hasCaptureFor(player.getUUID())) {
                return Component.literal("Temporary capture ready");
            }
            return state.hasSelectedLocalBlueprint()
                    ? Component.literal("Selected: " + state.selectedLocalBlueprint())
                    : Component.literal("No local blueprint selected");
        }).frequency(5));
        label.layout(layout -> {
            layout.flex(1);
            layout.height(STRING_HEIGHT);
        });
        return label;
    }

    private static UIElement saveDialog(final Player player) {
        final UIElement dialog = new UIElement()
                .setId("sable_camera_save_dialog")
                .addClass(SAVE_OVERLAY_CLASS)
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(42);
                    layout.top(82);
                    layout.width(200);
                    layout.height(28);
                    layout.paddingAll(6);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(GAP);
                });
        final TextField name = textField("sable_camera_save_name", "blueprint");
        name.layout(layout -> {
            layout.flex(1);
            layout.height(STRING_HEIGHT);
        });
        final boolean[] confirmOverwrite = {false};
        final Button confirm = iconButton(Icons.CHECK, Component.literal("Confirm"));
        confirm.addClass("__confirm-button__");
        confirm.setOnClick(event -> {
            final String requested = name.getText().trim();
            if (requested.isEmpty()) {
                return;
            }
            if (localNameExists(requested) && !confirmOverwrite[0]) {
                confirmOverwrite[0] = true;
                player.displayClientMessage(
                        Component.literal("A blueprint with this name already exists. Confirm again to overwrite."),
                        true
                );
                return;
            }
            CameraClientSession.requestDownload(CameraState.read(player.getMainHandItem()), requested);
            confirmOverwrite[0] = false;
            dialog.setVisible(false);
            dialog.setDisplay(false);
        });
        final Button cancel = iconButton(Icons.CLOSE, Component.literal("Cancel"));
        cancel.addClass("__reject-button__");
        cancel.setOnClick(event -> {
            confirmOverwrite[0] = false;
            dialog.setVisible(false);
            dialog.setDisplay(false);
        });
        dialog.addChildren(name, confirm, cancel);
        dialog.setVisible(false);
        dialog.setDisplay(false);
        return dialog;
    }

    static UIElement row(final String id) {
        return new UIElement().setId(id).layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height(BUTTON_SIZE);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(GAP);
            layout.flexShrink(0.0F);
        });
    }

    static Label title(final Component text) {
        final Label label = label(text, Horizontal.LEFT);
        label.layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height(STRING_HEIGHT);
        });
        return label;
    }

    static Label label(final Component text, final Horizontal align) {
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

    static Button iconButton(final IGuiTexture icon, final Component tooltip) {
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

    static TextField textField(final String id, final String value) {
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

    private static boolean localNameExists(final String name) {
        try {
            return BlueprintToolLocalFiles.list().stream().anyMatch(entry -> entry.name().equalsIgnoreCase(name));
        } catch (final IOException ignored) {
            return false;
        }
    }

    private static final class CameraBlueprintGrid extends ScrollerView {
        private static final float CARD_WIDTH = 62.0F;
        private static final float PREVIEW_SIZE = 62.0F;
        private static final float CARD_HEIGHT = PREVIEW_SIZE + STRING_HEIGHT;

        private final Player player;
        private final float width;
        private final CameraDetailsDialog details;
        private final CameraPreviewTextureCache previewTextures;
        private int ticks;
        private String signature = "";

        private CameraBlueprintGrid(final Player player,
                                    final float width,
                                    final float height,
                                    final CameraDetailsDialog details,
                                    final CameraPreviewTextureCache previewTextures) {
            this.player = player;
            this.width = width;
            this.details = details;
            this.previewTextures = previewTextures;
            setId("sable_camera_blueprint_grid");
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
            if (++ticks % 10 == 0 && !signature().equals(signature)) {
                rebuild();
            }
        }

        private String signature() {
            try {
                final String names = BlueprintToolLocalFiles.list().stream()
                        .map(BlueprintToolLocalFiles.Entry::name)
                        .reduce("", (left, right) -> left + "|" + right);
                return names + ":" + CameraState.read(player.getMainHandItem()).selectedLocalBlueprint();
            } catch (final IOException ignored) {
                return "error";
            }
        }

        private void rebuild() {
            clearAllScrollViewChildren();
            signature = signature();
            final List<BlueprintToolLocalFiles.Entry> entries;
            try {
                entries = BlueprintToolLocalFiles.list();
            } catch (final IOException ignored) {
                return;
            }
            final CameraState state = CameraState.read(player.getMainHandItem());
            if (state.hasSelectedLocalBlueprint()
                    && entries.stream().noneMatch(entry -> entry.name().equals(state.selectedLocalBlueprint()))) {
                CameraClientSession.requestSelectLocal("");
            }
            if (entries.isEmpty()) {
                final Label empty = label(Component.literal("No local blueprints"), Horizontal.LEFT);
                empty.layout(layout -> {
                    layout.widthPercent(100.0F);
                    layout.height(STRING_HEIGHT);
                });
                addScrollViewChild(empty);
                return;
            }
            for (int start = 0; start < entries.size(); start += 4) {
                final UIElement row = new UIElement().layout(layout -> {
                    layout.widthPercent(100.0F);
                    layout.height(CARD_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(4.0F);
                });
                for (int index = start; index < Math.min(start + 4, entries.size()); index++) {
                    row.addChild(tile(entries.get(index)));
                }
                addScrollViewChild(row);
            }
        }

        private UIElement tile(final BlueprintToolLocalFiles.Entry entry) {
            final UIElement tile = new UIElement()
                    .addClass(TILE_CLASS)
                    .layout(layout -> {
                        layout.width(CARD_WIDTH);
                        layout.height(CARD_HEIGHT);
                        layout.flexDirection(FlexDirection.COLUMN);
                        layout.flexShrink(0.0F);
                    });
            final CameraPreviewCanvas preview = new CameraPreviewCanvas(entry, previewView(), previewTextures);
            preview.layout(layout -> {
                layout.width(PREVIEW_SIZE);
                layout.height(PREVIEW_SIZE);
            });
            final Label name = label(Component.literal(entry.name()), Horizontal.CENTER);
            name.addClass(NAME_CLASS);
            name.layout(layout -> {
                layout.width(CARD_WIDTH);
                layout.height(STRING_HEIGHT);
            });
            tile.addChildren(preview, name);
            tile.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 0) {
                    CameraClientSession.requestSelectLocal(entry.name());
                    event.stopPropagation();
                } else if (event.button == 1) {
                    details.open(entry);
                    event.stopPropagation();
                }
            });
            return tile;
        }

        private SableBlueprintPreview.View previewView() {
            try {
                return SableBlueprintPreview.View.valueOf(CameraState.read(player.getMainHandItem()).previewView());
            } catch (final IllegalArgumentException ignored) {
                return SableBlueprintPreview.View.ISO_XP_ZP;
            }
        }
    }

    private static final class CameraPreviewCanvas extends UIElement {
        private final BlueprintToolLocalFiles.Entry entry;
        private final SableBlueprintPreview.View view;
        private final CameraPreviewTextureCache previewTextures;

        private CameraPreviewCanvas(final BlueprintToolLocalFiles.Entry entry,
                                    final SableBlueprintPreview.View view,
                                    final CameraPreviewTextureCache previewTextures) {
            this.entry = entry;
            this.view = view;
            this.previewTextures = previewTextures;
        }

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

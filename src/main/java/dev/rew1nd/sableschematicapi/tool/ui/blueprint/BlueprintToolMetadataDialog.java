package dev.rew1nd.sableschematicapi.tool.ui.blueprint;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.bareLabel;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.dialogTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.displayTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.fieldTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.iconButton;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.textArea;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.textField;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.tr;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.DESCRIPTION_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GAP;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ICON_BUTTON_SIZE;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.METADATA_AUTHOR_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.METADATA_PANEL_PADDING;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.METADATA_PANEL_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.METADATA_PREVIEW_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.PREVIEW_CANVAS_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.PREVIEW_DIALOG_INNER_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.PREVIEW_DIALOG_SCROLL_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROW_HEIGHT;

final class BlueprintToolMetadataDialog extends UIElement {
    private BlueprintToolLocalFiles.Entry renderedEntry;
    private SableBlueprintPreview.View renderedView;
    private int renderedRevision = Integer.MIN_VALUE;

    BlueprintToolMetadataDialog() {
        setId("sable_blueprint_preview_detail");
        layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(20);
            layout.width(CONTENT_WIDTH);
            layout.height(CONTENT_HEIGHT - 20);
            layout.paddingAll(8);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(GAP);
        });
        style(style -> style.backgroundTexture(dialogTexture()));
        setVisible(false);
        setDisplay(true);
    }

    @Override
    public void screenTick() {
        super.screenTick();
        sync();
    }

    private void sync() {
        final BlueprintToolLocalFiles.Entry entry = BlueprintToolClientSession.openedBlueprintPreview();
        if (entry == null) {
            setVisible(false);
            this.renderedEntry = null;
            this.renderedView = null;
            this.renderedRevision = Integer.MIN_VALUE;
            return;
        }

        setVisible(true);
        final int currentRevision = BlueprintToolClientSession.blueprintPreviewRevision();
        final SableBlueprintPreview.View view = BlueprintToolClientSession.openedBlueprintPreviewView();
        if (entry.equals(this.renderedEntry) && view == this.renderedView && currentRevision == this.renderedRevision) {
            return;
        }

        this.renderedEntry = entry;
        this.renderedView = view;
        this.renderedRevision = currentRevision;
        rebuild(entry, view);
    }

    private void rebuild(final BlueprintToolLocalFiles.Entry entry, final SableBlueprintPreview.View view) {
        clearAllChildren();

        addChildren(header(entry), metadataScroller(entry, view));
    }

    private UIElement header(final BlueprintToolLocalFiles.Entry entry) {
        final Label title = detailLabel(Component.literal(entry.name()));
        title.layout(layout -> {
            layout.widthStretch();
            layout.height(ROW_HEIGHT);
        });

        final Button close = iconButton(Icons.CLOSE, tr("ui.close"));
        close.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        close.setOnClick(event -> {
            BlueprintToolClientSession.closeBlueprintPreview();
            sync();
        });

        return new UIElement()
                .layout(layout -> {
                    layout.width(PREVIEW_DIALOG_INNER_WIDTH);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(GAP);
                })
                .addChildren(title, close);
    }

    private ScrollerView metadataScroller(final BlueprintToolLocalFiles.Entry entry,
                                          final SableBlueprintPreview.View view) {
        final ScrollerView scroller = new ScrollerView();
        scroller.layout(layout -> {
            layout.width(PREVIEW_DIALOG_INNER_WIDTH);
            layout.height(PREVIEW_DIALOG_SCROLL_HEIGHT);
        });
        scroller.style(style -> style.backgroundTexture(displayTexture()));
        scroller.viewContainer(container -> container.layout(layout -> {
            layout.width(PREVIEW_DIALOG_INNER_WIDTH - 12);
            layout.heightAuto();
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(GAP);
            layout.paddingAll(4);
        }));

        scroller.addScrollViewChild(metadataPanel(entry, view));
        return scroller;
    }

    private UIElement metadataPanel(final BlueprintToolLocalFiles.Entry entry,
                                    final SableBlueprintPreview.View view) {
        return new UIElement()
                .layout(layout -> {
                    layout.width(METADATA_PANEL_WIDTH);
                    layout.heightAuto();
                    layout.paddingAll(METADATA_PANEL_PADDING);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(GAP);
                })
                .style(style -> style.backgroundTexture(displayTexture()))
                .addChildren(previewColumn(entry, view), authorColumn());
    }

    private UIElement previewColumn(final BlueprintToolLocalFiles.Entry entry,
                                    final SableBlueprintPreview.View view) {
        final BlueprintToolPreviewCanvas canvas = new BlueprintToolPreviewCanvas(entry, view);
        canvas.layout(layout -> {
            layout.width(METADATA_PREVIEW_WIDTH);
            layout.height(PREVIEW_CANVAS_HEIGHT);
            layout.paddingAll(4);
        });
        canvas.style(style -> style.backgroundTexture(fieldTexture()));

        final Button previous = navButton(Icons.LEFT, tr("ui.preview_previous"));
        previous.setOnClick(event -> {
            BlueprintToolClientSession.previousBlueprintPreviewView();
            sync();
        });

        final Button next = navButton(Icons.RIGHT, tr("ui.preview_next"));
        next.setOnClick(event -> {
            BlueprintToolClientSession.nextBlueprintPreviewView();
            sync();
        });

        final UIElement switchRow = new UIElement()
                .layout(layout -> {
                    layout.width(METADATA_PREVIEW_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.justifyContent(AlignContent.SPACE_EVENLY);
                })
                .addChildren(previous, next);

        return new UIElement()
                .layout(layout -> {
                    layout.width(METADATA_PREVIEW_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.justifyContent(AlignContent.SPACE_BETWEEN);
                    layout.gapAll(GAP);
                })
                .addChildren(canvas, switchRow);
    }

    private UIElement authorColumn() {
        final boolean editing = BlueprintToolClientSession.blueprintMetadataEditing();
        final String author = editing
                ? BlueprintToolClientSession.draftBlueprintAuthor()
                : BlueprintToolClientSession.openedBlueprintMetadata().author();
        final String description = editing
                ? BlueprintToolClientSession.draftBlueprintDescription()
                : BlueprintToolClientSession.openedBlueprintMetadata().description();

        final UIElement descriptionField = editing
                ? editableDescription(description)
                : descriptionViewer(description);
        final UIElement authorRow = editing
                ? editableAuthorRow(author)
                : authorRow(author);
        final UIElement editRow = editConfigRow(editing);
        final UIElement contentGroup = new UIElement()
                .layout(layout -> {
                    layout.width(METADATA_AUTHOR_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(GAP);
                })
                .addChildren(descriptionField, authorRow);

        return new UIElement()
                .layout(layout -> {
                    layout.width(METADATA_AUTHOR_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.justifyContent(AlignContent.SPACE_BETWEEN);
                    layout.gapAll(GAP);
                })
                .addChildren(contentGroup, editRow);
    }

    private UIElement descriptionViewer(final String description) {
        final ScrollerView scroller = new ScrollerView();
        scroller.layout(layout -> {
            layout.width(METADATA_AUTHOR_WIDTH);
            layout.height(DESCRIPTION_HEIGHT);
        });
        scroller.style(style -> style.backgroundTexture(fieldTexture()));
        scroller.viewContainer(container -> container.layout(layout -> {
            layout.width(METADATA_AUTHOR_WIDTH - 12);
            layout.heightAuto();
            layout.paddingAll(3);
        }));

        final Label text = bareLabel(Horizontal.LEFT);
        text.setText(description == null || description.isBlank()
                ? tr("ui.description_empty")
                : Component.literal(description));
        text.textStyle(style -> style
                .adaptiveHeight(true)
                .textWrap(TextWrap.WRAP)
                .textAlignVertical(Vertical.TOP));
        text.layout(layout -> {
            layout.width(METADATA_AUTHOR_WIDTH - 16);
            layout.heightAuto();
        });
        scroller.addScrollViewChild(text);
        return scroller;
    }

    private TextArea editableDescription(final String description) {
        final TextArea area = textArea(
                "sable_blueprint_description",
                METADATA_AUTHOR_WIDTH,
                DESCRIPTION_HEIGHT
        );
        area.setLines(lines(description));
        area.setLinesResponder(lines -> BlueprintToolClientSession.setDraftBlueprintDescription(joinLines(lines)));
        return area;
    }

    private UIElement authorRow(final String author) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.layout(layout -> {
            layout.width(METADATA_AUTHOR_WIDTH);
            layout.height(ROW_HEIGHT);
            layout.paddingHorizontal(2);
        });
        label.setText(author == null || author.isBlank()
                ? Component.literal("unknown author")
                : Component.literal(author));
        label.style(style -> style.backgroundTexture(fieldTexture()));
        return label;
    }

    private UIElement editableAuthorRow(final String author) {
        final TextField field = textField(
                "sable_blueprint_author",
                METADATA_AUTHOR_WIDTH
        );
        field.setText(author == null ? "" : author);
        field.setTextResponder(BlueprintToolClientSession::setDraftBlueprintAuthor);
        return field;
    }

    private UIElement editConfigRow(final boolean editing) {
        final Button edit = navButton(
                editing ? Icons.SAVE : Icons.EDIT_FILE,
                editing ? tr("ui.save_metadata") : tr("ui.edit_metadata")
        );
        edit.setOnClick(event -> {
            if (editing) {
                BlueprintToolClientSession.saveBlueprintMetadataEdit(
                        BlueprintToolClientSession.draftBlueprintAuthor(),
                        BlueprintToolClientSession.draftBlueprintDescription()
                );
            } else {
                BlueprintToolClientSession.beginBlueprintMetadataEdit();
            }
            sync();
        });

        final UIElement row = new UIElement()
                .layout(layout -> {
                    layout.width(METADATA_AUTHOR_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.justifyContent(AlignContent.SPACE_EVENLY);
                })
                .addChild(edit);

        if (editing) {
            final Button discard = navButton(Icons.CLOSE, tr("ui.discard_metadata"));
            discard.setOnClick(event -> {
                BlueprintToolClientSession.discardBlueprintMetadataEdit();
                sync();
            });
            row.addChild(discard);
        }

        return row;
    }

    private Button navButton(final IGuiTexture icon, final Component tooltip) {
        final Button button = iconButton(icon, tooltip);
        button.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        return button;
    }

    private Label detailLabel(final Component text) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(text);
        label.layout(layout -> {
            layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }

    private List<String> lines(final String text) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        return Arrays.asList(text.split("\\R", -1));
    }

    private String joinLines(final String[] lines) {
        return lines == null ? "" : String.join("\n", lines);
    }
}

package dev.rew1nd.sableschematicapi.survival.physicsmanager;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import dev.rew1nd.sableschematicapi.survival.SurvivalMachineUiKit;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintLibraryUiKit;
import dev.rew1nd.sableschematicapi.survival.physicsmanager.client.PhysicsManagerBodyEntry;
import dev.rew1nd.sableschematicapi.survival.physicsmanager.client.PhysicsManagerClientSession;
import dev.rew1nd.sableschematicapi.survival.physicsmanager.client.PhysicsManagerGroup;
import dev.rew1nd.sableschematicapi.survival.physicsmanager.client.PhysicsManagerRemovalProgress;
import dev.rew1nd.sableschematicapi.survival.physicsmanager.client.PhysicsManagerSortMode;
import dev.rew1nd.sableschematicapi.survival.physicsmanager.client.PhysicsManagerStaticState;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PhysicsManagerUiRenderer {
    private static final float WIDTH = 430.0F;
    private static final float HEIGHT = 225.0F;
    private static final float CONTENT_WIDTH = 416.0F;
    private static final float TOOLBAR_HEIGHT = 18.0F;
    private static final float LIST_HEIGHT = 173.0F;
    private static final float STATUS_HEIGHT = 12.0F;
    private static final float GROUP_HEIGHT = 24.0F;
    private static final float BODY_HEIGHT = 20.0F;
    private static final float ACTION_SIZE = 16.0F;
    private static final float DETAILS_WIDTH = 320.0F;
    private static final float DETAILS_HEIGHT = 200.0F;
    private static final float DETAILS_INFO_HEIGHT = 120.0F;
    private static final float DETAILS_INFO_ROW_HEIGHT = 9.0F;
    private static final float DETAILS_INFO_KEY_WIDTH = 62.0F;
    private static final String ROOT_CLASS = "__physics_manager_root__";
    private static final String TOOLBAR_CLASS = "__physics_manager_toolbar__";
    private static final String LIST_CLASS = "__physics_manager_list__";
    private static final String GROUP_CLASS = "__physics_manager_group__";
    private static final String BODY_CLASS = "__physics_manager_body__";
    private static final String INFO_CLASS = "__physics_manager_info__";
    private static final String OVERLAY_CLASS = "__physics_manager_overlay__";
    private static final String MODAL_CLASS = "__physics_manager_modal__";
    private static final String DANGER_CLASS = "__physics_manager_danger__";

    private static final String LSS = """
            .__physics_manager_root__ {
              background: built-in(ui-ore:BORDER_7);
            }

            .__physics_manager_toolbar__, .__physics_manager_list__, .__physics_manager_info__ {
              background: built-in(ui-ore:RECT2);
            }

            .__physics_manager_group__ {
              background: built-in(ui-ore:RECT2);
              padding-horizontal: 2;
            }

            .__physics_manager_group__:hovered {
              background: #663D5244;
            }

            .__physics_manager_body__ {
              background: empty;
              padding-left: 10;
              padding-right: 2;
            }

            .__physics_manager_body__:hovered {
              background: #55383838;
            }

            .__physics_manager_overlay__, .__physics_manager_modal__ {
              background: built-in(ui-ore:BORDER_7);
            }

            .__physics_manager_danger__ {
              background: #77662222;
            }

            .__physics_manager_danger__:hovered {
              background: #AA8C2D2D;
            }
            """;

    private PhysicsManagerUiRenderer() {
    }

    public static ModularUI render(final HeldItemUIMenuType.HeldItemUIHolder holder) {
        if (!holder.player.level().isClientSide()) {
            final UIElement serverRoot = new UIElement().layout(layout -> {
                layout.width(WIDTH);
                layout.height(HEIGHT);
            });
            return ModularUI.of(UI.of(serverRoot), holder.player);
        }

        final UIElement root = new UIElement()
                .setId("physics_manager_root")
                .addClass(ROOT_CLASS)
                .addLocalStylesheet(BlueprintLibraryUiKit.LSS)
                .addLocalStylesheet(LSS)
                .layout(layout -> {
                    layout.width(WIDTH);
                    layout.height(HEIGHT);
                    layout.paddingAll(7);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(4);
                });

        final ConfirmationLayer confirmation = new ConfirmationLayer();
        final DetailsLayer details = new DetailsLayer(confirmation);
        confirmation.bindDetails(details);
        root.addChildren(toolbar(), new ManagerList(confirmation), statusRow(), details, confirmation);
        PhysicsManagerClientSession.requestRefresh();
        return ModularUI.of(
                UI.of(root, List.of(
                        StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.ORE_MERGED)
                )),
                holder.player
        );
    }

    private static UIElement toolbar() {
        final UIElement row = new UIElement()
                .addClass(TOOLBAR_CLASS)
                .layout(layout -> {
                    layout.width(CONTENT_WIDTH);
                    layout.height(TOOLBAR_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(4);
                    layout.flexShrink(0);
                });
        final Button refresh = iconButton(
                Icons.REPLAY,
                Component.translatable("gui.sable_schematic_api.physics_manager.refresh")
        );
        refresh.setOnClick(event -> PhysicsManagerClientSession.requestRefresh());

        final Selector<PhysicsManagerSortMode> selector = new Selector<>();
        selector.setCandidates(List.of(PhysicsManagerSortMode.values()));
        selector.setCandidateUIProvider(UIElementProvider.text(mode -> Component.translatable(
                (mode == null ? PhysicsManagerSortMode.DISTANCE : mode).translationKey()
        )));
        selector.setSelected(PhysicsManagerClientSession.sortMode(), false);
        selector.setOnValueChanged(PhysicsManagerClientSession::setSortMode);
        selector.layout(layout -> {
            layout.flex(1);
            layout.height(TOOLBAR_HEIGHT);
        });
        row.addChildren(refresh, selector);
        return row;
    }

    private static UIElement statusRow() {
        final Label label = SurvivalMachineUiKit.dynamicLabel(
                PhysicsManagerClientSession::status,
                SurvivalMachineUiKit.TEXT_COLOR
        );
        label.addClass(BlueprintLibraryUiKit.STATE_CLASS);
        label.layout(layout -> {
            layout.width(CONTENT_WIDTH);
            layout.height(STATUS_HEIGHT);
            layout.flexShrink(0);
        });
        return label;
    }

    private static Button iconButton(final com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture icon,
                                     final Component tooltip) {
        final Button button = BlueprintLibraryUiKit.iconButton(icon, tooltip);
        button.layout(layout -> {
            layout.width(ACTION_SIZE);
            layout.height(ACTION_SIZE);
            layout.flexShrink(0);
        });
        return button;
    }

    private static Label textLabel(final Component text, final Horizontal align) {
        final Label label = BlueprintLibraryUiKit.label(text, align);
        label.layout(layout -> {
            layout.height(12);
            layout.flex(1);
        });
        return label;
    }

    private static Component staticText(final PhysicsManagerStaticState state) {
        return Component.translatable(
                "gui.sable_schematic_api.physics_manager.static." + state.name().toLowerCase(Locale.ROOT)
        );
    }

    private static Component loadStateText(final PhysicsManagerBodyEntry body) {
        return Component.translatable(
                "gui.sable_schematic_api.physics_manager.load_state."
                        + (body.loaded() ? "loaded" : "stored")
        );
    }

    private static final class ManagerList extends ScrollerView {
        private final ConfirmationLayer confirmation;
        private long renderedRevision = Long.MIN_VALUE;
        private boolean restoringScroll;

        private ManagerList(final ConfirmationLayer confirmation) {
            this.confirmation = confirmation;
            setId("physics_manager_list");
            addClass(LIST_CLASS);
            layout(layout -> {
                layout.width(CONTENT_WIDTH);
                layout.height(LIST_HEIGHT);
                layout.flexShrink(0);
            });
            viewPort(port -> port.layout(layout -> layout.paddingAll(3)));
            viewContainer(container -> container.layout(layout -> {
                layout.widthPercent(100);
                layout.heightAuto();
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(2);
            }));
            verticalScroller.setOnValueChanged(value -> {
                onVerticalScroll(value);
                if (!restoringScroll) {
                    PhysicsManagerClientSession.setScroll(verticalScroller.getNormalizedValue());
                }
            });
            refreshIfNeeded();
        }

        @Override
        public void screenTick() {
            super.screenTick();
            refreshIfNeeded();
            if (!restoringScroll) {
                final float current = verticalScroller.getNormalizedValue();
                if (!Float.isNaN(current)) {
                    PhysicsManagerClientSession.setScroll(current);
                }
            }
        }

        private void refreshIfNeeded() {
            final long revision = PhysicsManagerClientSession.listRevision();
            if (revision == this.renderedRevision) {
                return;
            }
            this.renderedRevision = revision;
            rebuild();
        }

        private void rebuild() {
            clearAllScrollViewChildren();
            final List<PhysicsManagerGroup> groups = PhysicsManagerClientSession.sortedGroups();
            if (groups.isEmpty()) {
                final Label empty = textLabel(
                        Component.translatable("gui.sable_schematic_api.physics_manager.empty")
                                .withStyle(ChatFormatting.GRAY),
                        Horizontal.LEFT
                );
                empty.layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(GROUP_HEIGHT);
                });
                addScrollViewChild(empty);
            } else {
                for (final PhysicsManagerGroup group : groups) {
                    addScrollViewChild(groupRow(group));
                    if (PhysicsManagerClientSession.isExpanded(group.groupId())) {
                        for (final PhysicsManagerBodyEntry body : group.members()) {
                            addScrollViewChild(bodyRow(group, body));
                        }
                    }
                }
            }
            restoringScroll = true;
            verticalScroller.setNormalizedValue(PhysicsManagerClientSession.scroll(), false);
            restoringScroll = false;
        }

        private UIElement groupRow(final PhysicsManagerGroup group) {
            final UIElement row = new UIElement()
                    .addClass(GROUP_CLASS)
                    .layout(layout -> {
                        layout.widthPercent(100);
                        layout.height(GROUP_HEIGHT);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.alignItems(AlignItems.CENTER);
                        layout.gapAll(3);
                        layout.flexShrink(0);
                    });

            final boolean expanded = PhysicsManagerClientSession.isExpanded(group.groupId());
            final Runnable toggleExpanded = () ->
                    PhysicsManagerClientSession.toggleExpanded(group.groupId());
            final UIElement summary = new UIElement();
            summary.setOverflowVisible(false);
            summary.layout(layout -> {
                layout.flex(1);
                layout.height(GROUP_HEIGHT - 2);
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.CENTER);
                layout.gapAll(3);
                layout.paddingAll(0);
            });
            final UIElement expandIcon = new UIElement()
                    .style(style -> style.backgroundTexture(
                            expanded ? Icons.DOWN_ARROW_NO_BAR : Icons.RIGHT_ARROW_NO_BAR
                    ))
                    .layout(layout -> {
                        layout.width(14);
                        layout.height(14);
                        layout.flexShrink(0);
                    });
            final Label label = textLabel(groupSummary(group), Horizontal.LEFT);
            label.setOverflowVisible(false);
            label.layout(layout -> {
                layout.height(12);
                layout.flex(1);
                layout.flexShrink(1);
            });
            summary.addChildren(expandIcon, label);
            summary.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    toggleExpanded.run();
                    event.stopPropagation();
                }
            });

            final boolean deleting = group.members().stream()
                    .anyMatch(body -> PhysicsManagerClientSession.isDeleting(body.uuid()));
            final Button outline = iconButton(
                    Icons.EYE,
                    Component.translatable("gui.sable_schematic_api.physics_manager.highlight_group")
            );
            outline.setActive(!deleting);
            outline.setOnClick(event -> PhysicsManagerClientSession.highlightGroup(group));
            final Button delete = iconButton(
                    Icons.DELETE,
                    Component.translatable("gui.sable_schematic_api.physics_manager.delete_group")
            );
            delete.addClass(DANGER_CLASS);
            delete.setActive(!deleting);
            delete.setOnClick(event -> confirmation.showGroup(group));
            final Button teleport = iconButton(
                    Icons.TRANSFORM_TRANSLATE,
                    Component.translatable("gui.sable_schematic_api.physics_manager.teleport_group")
            );
            teleport.setActive(!deleting);
            teleport.setOnClick(event -> PhysicsManagerClientSession.requestTeleportPlayer(
                    group.largestBody(),
                    group,
                    true
            ));
            row.addChildren(summary, outline, delete, teleport);
            return row;
        }

        private UIElement bodyRow(final PhysicsManagerGroup group,
                                  final PhysicsManagerBodyEntry body) {
            final UIElement row = new UIElement()
                    .addClass(BODY_CLASS)
                    .layout(layout -> {
                        layout.widthPercent(100);
                        layout.height(BODY_HEIGHT);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.alignItems(AlignItems.CENTER);
                        layout.gapAll(3);
                        layout.flexShrink(0);
                    });
            final Button summary = new Button();
            summary.noText();
            summary.buttonStyle(style -> style
                    .baseTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                    .hoverTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                    .pressedTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY));
            summary.layout(layout -> {
                layout.flex(1);
                layout.height(BODY_HEIGHT - 2);
                layout.paddingAll(0);
            });
            summary.addChild(textLabel(bodySummary(body), Horizontal.LEFT));
            summary.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 1) {
                    PhysicsManagerClientSession.openDetails(body);
                    event.stopPropagation();
                }
            });

            final boolean deleting = PhysicsManagerClientSession.isDeleting(body.uuid());
            final Button outline = iconButton(
                    Icons.EYE,
                    Component.translatable("gui.sable_schematic_api.physics_manager.highlight_body")
            );
            outline.setActive(!deleting);
            outline.setOnClick(event -> PhysicsManagerClientSession.highlightBody(body));
            final Button delete = iconButton(
                    Icons.DELETE,
                    Component.translatable("gui.sable_schematic_api.physics_manager.delete_body")
            );
            delete.addClass(DANGER_CLASS);
            delete.setActive(!deleting);
            delete.setOnClick(event -> confirmation.showBody(group, body));
            row.addChildren(summary, outline, delete);
            return row;
        }

        private Component groupSummary(final PhysicsManagerGroup group) {
            final Component deleting = group.members().stream()
                    .anyMatch(body -> PhysicsManagerClientSession.isDeleting(body.uuid()))
                    ? Component.translatable("gui.sable_schematic_api.physics_manager.deleting_suffix")
                    : Component.empty();
            return Component.translatable(
                    "gui.sable_schematic_api.physics_manager.group_summary",
                    group.displayName(),
                    PhysicsManagerBodyEntry.shortId(group.groupId()),
                    group.members().size(),
                    group.loadedCount(),
                    group.storedCount(),
                    group.dimensionLabel(),
                    group.distanceLabel(),
                    staticText(group.staticState()),
                    deleting
            );
        }

        private Component bodySummary(final PhysicsManagerBodyEntry body) {
            final Component deleting = PhysicsManagerClientSession.isDeleting(body.uuid())
                    ? Component.translatable("gui.sable_schematic_api.physics_manager.deleting_suffix")
                    : Component.empty();
            return Component.translatable(
                    "gui.sable_schematic_api.physics_manager.body_summary",
                    body.displayName(),
                    PhysicsManagerBodyEntry.shortId(body.uuid()),
                    loadStateText(body),
                    body.dimension(),
                    body.distanceLabel(),
                    staticText(body.staticLocked()
                            ? PhysicsManagerStaticState.STATIC
                            : PhysicsManagerStaticState.DYNAMIC),
                    deleting
            );
        }
    }

    private static final class DetailsLayer extends UIElement {
        private final ConfirmationLayer confirmation;
        private long renderedRevision = Long.MIN_VALUE;
        private UUID renderedBody;

        private DetailsLayer(final ConfirmationLayer confirmation) {
            this.confirmation = confirmation;
            setId("physics_manager_details_layer");
            layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(0);
                layout.width(WIDTH);
                layout.height(HEIGHT);
                layout.alignItems(AlignItems.CENTER);
                layout.justifyContent(AlignContent.CENTER);
            });
            stopInteractionEventsPropagation();
            setFocusable(true);
            addEventListener(UIEvents.KEY_DOWN, event -> {
                if (event.keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    PhysicsManagerClientSession.closeDetails();
                    sync();
                    event.stopPropagation();
                }
            }, true);
            setVisible(false);
            setDisplay(true);
        }

        @Override
        public void screenTick() {
            super.screenTick();
            sync();
        }

        private void sync() {
            if (!PhysicsManagerClientSession.detailsOpen()) {
                setVisible(false);
                this.renderedBody = null;
                this.renderedRevision = Long.MIN_VALUE;
                return;
            }
            final PhysicsManagerBodyEntry body = PhysicsManagerClientSession
                    .findBody(PhysicsManagerClientSession.detailsBody())
                    .orElse(null);
            if (body == null) {
                PhysicsManagerClientSession.closeDetails();
                setVisible(false);
                return;
            }
            setVisible(true);
            final long revision = PhysicsManagerClientSession.viewRevision();
            if (body.uuid().equals(this.renderedBody) && revision == this.renderedRevision) {
                return;
            }
            this.renderedBody = body.uuid();
            this.renderedRevision = revision;
            rebuild(body);
            focus();
        }

        private void rebuild(final PhysicsManagerBodyEntry body) {
            clearAllChildren();
            final PhysicsManagerGroup group = PhysicsManagerClientSession
                    .groupForBody(body.uuid())
                    .orElseThrow();
            final UIElement panel = new UIElement()
                    .addClass(OVERLAY_CLASS)
                    .layout(layout -> {
                        layout.width(DETAILS_WIDTH);
                        layout.height(DETAILS_HEIGHT);
                        layout.paddingAll(7);
                        layout.flexDirection(FlexDirection.COLUMN);
                        layout.gapAll(4);
                    });
            final UIElement closeRow = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(ACTION_SIZE);
                layout.flexDirection(FlexDirection.ROW);
                layout.justifyContent(AlignContent.FLEX_END);
            });
            final Button close = iconButton(
                    Icons.CLOSE,
                    Component.translatable("gui.sable_schematic_api.physics_manager.close")
            );
            close.setOnClick(event -> {
                PhysicsManagerClientSession.closeDetails();
                sync();
            });
            closeRow.addChild(close);

            final UIElement info = new UIElement()
                    .addClass(INFO_CLASS)
                    .layout(layout -> {
                        layout.widthPercent(100);
                        layout.height(DETAILS_INFO_HEIGHT);
                        layout.paddingAll(3);
                        layout.flexDirection(FlexDirection.COLUMN);
                        layout.gapAll(0);
                    });
            info.addChildren(
                    infoRow("field.uuid", body.uuid().toString()),
                    infoRow("field.group", body.groupId().toString()),
                    infoRow("field.bodies", Integer.toString(group.members().size())),
                    infoRow("field.state", "[" + loadStateText(body).getString() + "]"),
                    infoRow("field.dimension", body.dimension()),
                    infoRow("field.position", triple(body.x(), body.y(), body.z())),
                    infoRow("field.aabb_min", triple(body.minX(), body.minY(), body.minZ())),
                    infoRow("field.aabb_max", triple(body.maxX(), body.maxY(), body.maxZ())),
                    infoRow("field.aabb_size", triple(
                            body.maxX() - body.minX(),
                            body.maxY() - body.minY(),
                            body.maxZ() - body.minZ()
                    )),
                    infoRow("field.volume", String.format(Locale.ROOT, "%.2f", body.volume())),
                    infoRow("field.distance", body.distanceLabel()),
                    infoRow("field.group_static", staticText(group.staticState()).getString())
            );

            final TextField name = BlueprintLibraryUiKit.textField(
                    "physics_manager_name",
                    PhysicsManagerClientSession.nameDraft(body)
            );
            name.setTextValidator(value -> value != null && value.length() <= 128);
            name.setTextResponder(value -> PhysicsManagerClientSession.setNameDraft(body.uuid(), value));
            name.layout(layout -> {
                layout.flex(1);
                layout.height(14);
            });
            final Button rename = iconButton(
                    Icons.CHECK,
                    Component.translatable("gui.sable_schematic_api.physics_manager.rename")
            );
            final Runnable submitRename = () -> {
                final String value = name.getText() == null ? "" : name.getText().trim();
                if (!value.isEmpty()) {
                    PhysicsManagerClientSession.requestRename(body, value);
                }
            };
            rename.setOnClick(event -> submitRename.run());
            name.addEventListener(UIEvents.KEY_DOWN, event -> {
                if (event.keyCode == GLFW.GLFW_KEY_ENTER
                        || event.keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    submitRename.run();
                    event.stopPropagation();
                }
            });
            final UIElement nameRow = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(16);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(4);
            }).addChildren(name, rename);

            final UIElement actions = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(18);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(4);
            });
            actions.addChildren(
                    textButton("gui.sable_schematic_api.physics_manager.highlight", () ->
                            PhysicsManagerClientSession.highlightBody(body)),
                    textButton("gui.sable_schematic_api.physics_manager.teleport_self", () ->
                            PhysicsManagerClientSession.requestTeleportPlayer(body, group, false)),
                    textButton("gui.sable_schematic_api.physics_manager.bring", () ->
                            PhysicsManagerClientSession.requestBringGroup(group, body)),
                    textButton(
                            group.staticState() == PhysicsManagerStaticState.STATIC
                                    ? "gui.sable_schematic_api.physics_manager.dynamic"
                                    : "gui.sable_schematic_api.physics_manager.static",
                            () -> PhysicsManagerClientSession.requestSetStatic(group, body)
                    )
            );
            panel.addChildren(closeRow, info, nameRow, actions);
            addChild(panel);
        }

        private UIElement infoRow(final String key, final String value) {
            final UIElement row = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(DETAILS_INFO_ROW_HEIGHT);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(4);
                layout.flexShrink(0);
            });
            final Label keyLabel = detailLabel(Component.translatable(
                    "gui.sable_schematic_api.physics_manager." + key
            ), Horizontal.LEFT);
            keyLabel.layout(layout -> {
                layout.width(DETAILS_INFO_KEY_WIDTH);
                layout.height(DETAILS_INFO_ROW_HEIGHT);
                layout.flexShrink(0);
            });
            final Label valueLabel = detailLabel(Component.literal(value), Horizontal.LEFT);
            valueLabel.layout(layout -> {
                layout.flex(1);
                layout.height(DETAILS_INFO_ROW_HEIGHT);
                layout.flexShrink(1);
            });
            row.addChildren(keyLabel, valueLabel);
            return row;
        }

        private Label detailLabel(final Component text, final Horizontal align) {
            final Label label = BlueprintLibraryUiKit.label(text, align);
            label.setOverflowVisible(false);
            label.textStyle(style -> style
                    .adaptiveWidth(false)
                    .adaptiveHeight(false)
                    .fontSize(7)
                    .textWrap(TextWrap.HOVER_ROLL));
            return label;
        }

        private Button textButton(final String key, final Runnable action) {
            final Button button = new Button();
            button.setText(Component.translatable(key));
            button.textStyle(style -> style
                    .adaptiveWidth(true)
                    .adaptiveHeight(false)
                    .fontSize(7)
                    .textColor(0xFFFFFFFF));
            button.layout(layout -> {
                layout.flex(1);
                layout.height(18);
                layout.paddingAll(1);
            });
            button.setOnClick(event -> action.run());
            return button;
        }

        private String triple(final double x, final double y, final double z) {
            return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", x, y, z);
        }
    }

    private static final class ConfirmationLayer extends UIElement {
        private PhysicsManagerGroup group;
        private PhysicsManagerBodyEntry body;
        private boolean wholeGroup;
        private DetailsLayer details;

        private ConfirmationLayer() {
            setId("physics_manager_confirmation_layer");
            layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(0);
                layout.width(WIDTH);
                layout.height(HEIGHT);
                layout.alignItems(AlignItems.CENTER);
                layout.justifyContent(AlignContent.CENTER);
            });
            stopInteractionEventsPropagation();
            setFocusable(true);
            addEventListener(UIEvents.KEY_DOWN, event -> {
                if (event.keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    hide();
                    event.stopPropagation();
                }
            }, true);
            setVisible(false);
            setDisplay(true);
        }

        private void bindDetails(final DetailsLayer details) {
            this.details = details;
        }

        private void showBody(final PhysicsManagerGroup group,
                              final PhysicsManagerBodyEntry body) {
            this.group = group;
            this.body = body;
            this.wholeGroup = false;
            rebuild();
            setVisible(true);
            focus();
        }

        private void showGroup(final PhysicsManagerGroup group) {
            this.group = group;
            this.body = null;
            this.wholeGroup = true;
            rebuild();
            setVisible(true);
            focus();
        }

        private void hide() {
            setVisible(false);
            this.group = null;
            this.body = null;
            if (this.details != null && PhysicsManagerClientSession.detailsOpen()) {
                this.details.focus();
            } else {
                blur();
            }
        }

        private void rebuild() {
            clearAllChildren();
            final UIElement panel = new UIElement()
                    .addClass(MODAL_CLASS)
                    .layout(layout -> {
                        layout.width(300);
                        layout.heightAuto();
                        layout.paddingAll(8);
                        layout.flexDirection(FlexDirection.COLUMN);
                        layout.gapAll(6);
                    });
            final Component message;
            if (wholeGroup) {
                message = Component.translatable(
                        "gui.sable_schematic_api.physics_manager.confirm_group",
                        group.displayName(),
                        group.members().size(),
                        group.loadedCount(),
                        group.storedCount()
                ).withStyle(ChatFormatting.RED);
            } else {
                final boolean connected = group.members().size() > 1;
                message = Component.translatable(
                        connected
                                ? "gui.sable_schematic_api.physics_manager.confirm_body_connected"
                                : "gui.sable_schematic_api.physics_manager.confirm_body",
                        body.displayName(),
                        PhysicsManagerBodyEntry.shortId(body.uuid()),
                        loadStateText(body)
                ).withStyle(ChatFormatting.RED);
            }
            final Label label = textLabel(message, Horizontal.LEFT);
            label.setOverflowVisible(false);
            label.layout(layout -> {
                layout.widthPercent(100);
                layout.height(18);
                layout.flexShrink(1);
            });
            final UIElement messagePanel = new UIElement()
                    .addClass(INFO_CLASS)
                    .layout(layout -> {
                        layout.widthPercent(100);
                        layout.height(26);
                        layout.paddingAll(4);
                        layout.alignItems(AlignItems.CENTER);
                    });
            messagePanel.setOverflowVisible(false);
            messagePanel.addChild(label);

            final UIElement buttons = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(20);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(6);
            });
            final Button confirm = iconButton(
                    Icons.CHECK,
                    Component.translatable("gui.sable_schematic_api.physics_manager.confirm")
            );
            confirm.layout(layout -> {
                layout.flex(1);
                layout.height(20);
                layout.flexShrink(1);
            });
            confirm.addClass(DANGER_CLASS);
            confirm.setOnClick(event -> {
                if (wholeGroup && group != null) {
                    PhysicsManagerClientSession.requestDeleteGroup(group);
                } else if (body != null) {
                    PhysicsManagerClientSession.requestDeleteBody(body);
                }
                hide();
            });
            final Button cancel = iconButton(
                    Icons.CLOSE,
                    Component.translatable("gui.sable_schematic_api.physics_manager.cancel")
            );
            cancel.layout(layout -> {
                layout.flex(1);
                layout.height(20);
                layout.flexShrink(1);
            });
            cancel.setOnClick(event -> hide());
            buttons.addChildren(confirm, cancel);
            panel.addChildren(messagePanel, buttons);
            addChild(panel);
        }
    }
}

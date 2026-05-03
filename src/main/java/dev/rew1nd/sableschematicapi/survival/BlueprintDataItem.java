package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Survival-facing item that references a blueprint stored on the server disk.
 *
 * <p>The actual blueprint data lives under {@code Sable-Schematics/uploaded/<uploader>/<name>.nbt}.
 * The item stack only carries a reference: uploader name, blueprint name, hash, and summary.</p>
 */
public class BlueprintDataItem extends Item {
    private static final String ROOT_KEY = "sable_survival_blueprint";
    private static final String KEY_NAME = "name";
    private static final String KEY_UPLOADER = "uploader";
    private static final String KEY_HASH = "hash";
    private static final String KEY_SUB_LEVELS = "sub_levels";
    private static final String KEY_BLOCKS = "blocks";
    private static final String KEY_BLOCK_ENTITY_TAGS = "block_entity_tags";
    private static final String KEY_ENTITIES = "entities";

    public BlueprintDataItem(final Properties properties) {
        super(properties);
    }

    /**
     * Creates a survival blueprint item stack that references a server file.
     */
    public static ItemStack createFromServerFile(final String uploader,
                                                  final String name,
                                                  final byte[] hash,
                                                  final BlueprintSummary summary) {
        final ItemStack stack = new ItemStack(SableSchematicApiItems.SURVIVAL_BLUEPRINT.get());
        stack.set(DataComponents.ITEM_NAME, Component.literal(sanitizeName(name)));

        final CompoundTag root = new CompoundTag();
        final CompoundTag payloadTag = new CompoundTag();
        payloadTag.putString(KEY_NAME, sanitizeName(name));
        payloadTag.putString(KEY_UPLOADER, uploader);
        payloadTag.putByteArray(KEY_HASH, hash.clone());
        payloadTag.putInt(KEY_SUB_LEVELS, summary.subLevels());
        payloadTag.putInt(KEY_BLOCKS, summary.blocks());
        payloadTag.putInt(KEY_BLOCK_ENTITY_TAGS, summary.blockEntityTags());
        payloadTag.putInt(KEY_ENTITIES, summary.entities());

        root.put(ROOT_KEY, payloadTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    public static boolean hasPayload(final ItemStack stack) {
        if (stack.isEmpty() || !isBlueprintItem(stack)) {
            return false;
        }
        final CompoundTag payload = payloadTag(stack);
        return payload.contains(KEY_NAME);
    }

    /**
     * Reads the blueprint from the server file referenced by this item.
     */
    public static Optional<SableBlueprint> readBlueprint(final ServerLevel level, final ItemStack stack) {
        final CompoundTag payload = payloadTag(stack);
        final String uploader = payload.getString(KEY_UPLOADER);
        final String name = payload.getString(KEY_NAME);
        if (uploader.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(BlueprintPayloads.readCompressed(
                    BlueprintServerFiles.read(level, uploader, name)));
        } catch (final IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static String hashString(final ItemStack stack) {
        final CompoundTag payload = payloadTag(stack);
        if (!payload.contains(KEY_HASH)) {
            return "";
        }
        return BlueprintPayloads.hashString(payload.getByteArray(KEY_HASH));
    }

    public static String blueprintName(final ItemStack stack) {
        final CompoundTag payload = payloadTag(stack);
        return payload.contains(KEY_NAME) ? payload.getString(KEY_NAME) : "";
    }

    public static BlueprintSummary summary(final ItemStack stack) {
        final CompoundTag payload = payloadTag(stack);
        return new BlueprintSummary(
                payload.getInt(KEY_SUB_LEVELS),
                payload.getInt(KEY_BLOCKS),
                payload.getInt(KEY_BLOCK_ENTITY_TAGS),
                payload.getInt(KEY_ENTITIES)
        );
    }

    public static boolean isBlueprintItem(final ItemStack stack) {
        return !stack.isEmpty() && stack.is(SableSchematicApiItems.SURVIVAL_BLUEPRINT.get());
    }

    private static CompoundTag payloadTag(final ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        final CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return root.getCompound(ROOT_KEY);
    }

    private static String sanitizeName(final String name) {
        if (name == null || name.isBlank()) {
            return "blueprint";
        }
        return name.trim();
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltip,
                                final TooltipFlag tooltipFlag) {
        final CompoundTag payload = payloadTag(stack);
        if (payload.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.sable_schematic_api.survival_blueprint.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        final BlueprintSummary summary = summary(stack);
        tooltip.add(Component.translatable(
                "tooltip.sable_schematic_api.survival_blueprint.summary",
                summary.subLevels(),
                summary.blocks(),
                summary.blockEntityTags(),
                summary.entities()
        ).withStyle(ChatFormatting.GRAY));

        final String hash = hashString(stack);
        if (!hash.isEmpty()) {
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.survival_blueprint.hash",
                    hash.substring(0, Math.min(12, hash.length()))
            ).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

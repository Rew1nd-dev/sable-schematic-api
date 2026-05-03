package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadMode;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtSanitizeContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtSanitizer;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.NbtSanitizeResult;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * Process-global registry for survival block entity NBT sanitizers.
 */
public final class BlueprintNbtSanitizers {
    private static final Map<Block, BlueprintNbtSanitizer> BLOCK_SANITIZERS = new Object2ObjectOpenHashMap<>();
    private static final Map<BlockEntityType<?>, BlueprintNbtSanitizer> BLOCK_ENTITY_SANITIZERS = new Object2ObjectOpenHashMap<>();
    private static BlueprintNbtSanitizer fallback = new FallbackSanitizer();

    private BlueprintNbtSanitizers() {
    }

    public static void register(final Block block, final BlueprintNbtSanitizer sanitizer) {
        BLOCK_SANITIZERS.put(block, sanitizer);
    }

    public static void register(final BlockEntityType<?> type, final BlueprintNbtSanitizer sanitizer) {
        BLOCK_ENTITY_SANITIZERS.put(type, sanitizer);
    }

    public static void registerFallback(final BlueprintNbtSanitizer sanitizer) {
        fallback = sanitizer;
    }

    public static NbtSanitizeResult sanitize(final BlueprintNbtSanitizeContext context) {
        if (context.mode() == BlueprintNbtLoadMode.NONE || context.mode() == BlueprintNbtLoadMode.DENY) {
            return new NbtSanitizeResult(context.mode(), null);
        }

        CompoundTag current = context.currentTag();
        if (context.mode() == BlueprintNbtLoadMode.FULL) {
            return new NbtSanitizeResult(BlueprintNbtLoadMode.FULL, current);
        }

        BlueprintNbtLoadMode mode = context.mode();
        final BlueprintNbtSanitizer blockSanitizer = BLOCK_SANITIZERS.get(context.state().getBlock());
        if (blockSanitizer != null) {
            final NbtSanitizeResult result = blockSanitizer.sanitize(with(context, mode, current));
            mode = result.mode();
            current = result.tag();
            if (terminal(mode)) {
                return new NbtSanitizeResult(mode, current);
            }
        }

        final Optional<BlockEntityType<?>> type = blockEntityType(context.rawTag());
        if (type.isPresent()) {
            final BlueprintNbtSanitizer typeSanitizer = BLOCK_ENTITY_SANITIZERS.get(type.get());
            if (typeSanitizer != null) {
                final NbtSanitizeResult result = typeSanitizer.sanitize(with(context, mode, current));
                mode = result.mode();
                current = result.tag();
                if (terminal(mode)) {
                    return new NbtSanitizeResult(mode, current);
                }
            }
        }

        return fallback.sanitize(with(context, mode, current));
    }

    private static BlueprintNbtSanitizeContext with(final BlueprintNbtSanitizeContext context,
                                                   final BlueprintNbtLoadMode mode,
                                                   @Nullable final CompoundTag current) {
        return new BlueprintNbtSanitizeContext(
                context.blueprint(),
                context.subLevel(),
                context.block(),
                context.ref(),
                context.state(),
                context.rawTag(),
                current,
                mode
        );
    }

    private static boolean terminal(final BlueprintNbtLoadMode mode) {
        return mode == BlueprintNbtLoadMode.NONE || mode == BlueprintNbtLoadMode.DENY;
    }

    private static Optional<BlockEntityType<?>> blockEntityType(@Nullable final CompoundTag tag) {
        if (tag == null || !tag.contains("id")) {
            return Optional.empty();
        }

        final ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        return id == null ? Optional.empty() : BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id);
    }

    private static final class FallbackSanitizer implements BlueprintNbtSanitizer {
        private static final String[] REMOVED_KEYS = {
                "Items",
                "Inventory",
                "Fluid",
                "Fluids",
                "Tank",
                "Tanks",
                "Energy",
                "ForgeCaps",
                "LootTable",
                "LootTableSeed",
                "Lock"
        };

        @Override
        public ResourceLocation id() {
            return SableSchematicApi.id("fallback_sanitizer");
        }

        @Override
        public NbtSanitizeResult sanitize(final BlueprintNbtSanitizeContext context) {
            final CompoundTag tag = context.currentTag();
            if (tag == null) {
                return new NbtSanitizeResult(BlueprintNbtLoadMode.NONE, null);
            }

            for (final String key : REMOVED_KEYS) {
                tag.remove(key);
            }

            return new NbtSanitizeResult(BlueprintNbtLoadMode.SANITIZED, tag);
        }
    }
}

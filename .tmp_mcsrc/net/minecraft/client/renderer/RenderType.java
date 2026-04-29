package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class RenderType extends RenderStateShard {
    public static final int MEGABYTE = 1048576;
    public static final int BIG_BUFFER_SIZE = 4194304;
    public static final int SMALL_BUFFER_SIZE = 786432;
    public static final int TRANSIENT_BUFFER_SIZE = 1536;
    public static final RenderType SOLID = create(
        "solid",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        4194304,
        true,
        false,
        RenderType.CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(RENDERTYPE_SOLID_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .createCompositeState(true)
    );
    public static final RenderType CUTOUT_MIPPED = create(
        "cutout_mipped",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        4194304,
        true,
        false,
        RenderType.CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(RENDERTYPE_CUTOUT_MIPPED_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .createCompositeState(true)
    );
    public static final RenderType CUTOUT = create(
        "cutout",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        786432,
        true,
        false,
        RenderType.CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(RENDERTYPE_CUTOUT_SHADER)
            .setTextureState(BLOCK_SHEET)
            .createCompositeState(true)
    );
    public static final RenderType TRANSLUCENT = create(
        "translucent", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 786432, true, true, translucentState(RENDERTYPE_TRANSLUCENT_SHADER)
    );
    public static final RenderType TRANSLUCENT_MOVING_BLOCK = create(
        "translucent_moving_block", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 786432, false, true, translucentMovingBlockState()
    );
    public static final Function<ResourceLocation, RenderType> ARMOR_CUTOUT_NO_CULL = Util.memoize(
        p_297924_ -> createArmorCutoutNoCull("armor_cutout_no_cull", p_297924_, false)
    );
    public static final Function<ResourceLocation, RenderType> ENTITY_SOLID = Util.memoize(
        p_286159_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286159_, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
            return create("entity_solid", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate);
        }
    );
    public static final Function<ResourceLocation, RenderType> ENTITY_CUTOUT = Util.memoize(
        p_286173_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286173_, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
            return create("entity_cutout", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate);
        }
    );
    public static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_CUTOUT_NO_CULL = Util.memoize(
        (p_286166_, p_286167_) -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286166_, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(p_286167_);
            return create("entity_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate);
        }
    );
    public static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_CUTOUT_NO_CULL_Z_OFFSET = Util.memoize(
        (p_286153_, p_286154_) -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_Z_OFFSET_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286153_, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(p_286154_);
            return create(
                "entity_cutout_no_cull_z_offset", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate
            );
        }
    );
    public static final Function<ResourceLocation, RenderType> ITEM_ENTITY_TRANSLUCENT_CULL = Util.memoize(
        p_286155_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286155_, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setOutputState(ITEM_ENTITY_TARGET)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .createCompositeState(true);
            return create("item_entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, rendertype$compositestate);
        }
    );
    public static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_CULL = Util.memoize(
        p_286165_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286165_, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
            return create("entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, rendertype$compositestate);
        }
    );
    public static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_TRANSLUCENT = Util.memoize(
        (p_286156_, p_286157_) -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286156_, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(p_286157_);
            return create("entity_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, rendertype$compositestate);
        }
    );
    public static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_TRANSLUCENT_EMISSIVE = Util.memoize(
        (p_286163_, p_286164_) -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286163_, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .setOverlayState(OVERLAY)
                .createCompositeState(p_286164_);
            return create("entity_translucent_emissive", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, rendertype$compositestate);
        }
    );
    public static final Function<ResourceLocation, RenderType> ENTITY_SMOOTH_CUTOUT = Util.memoize(
        p_286169_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_SMOOTH_CUTOUT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286169_, false, false))
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .createCompositeState(true);
            return create("entity_smooth_cutout", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, rendertype$compositestate);
        }
    );
    public static final BiFunction<ResourceLocation, Boolean, RenderType> BEACON_BEAM = Util.memoize(
        (p_234330_, p_234331_) -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_234330_, false, false))
                .setTransparencyState(p_234331_ ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                .setWriteMaskState(p_234331_ ? COLOR_WRITE : COLOR_DEPTH_WRITE)
                .createCompositeState(false);
            return create("beacon_beam", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, false, true, rendertype$compositestate);
        }
    );
    public static final Function<ResourceLocation, RenderType> ENTITY_DECAL = Util.memoize(
        p_286171_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_DECAL_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286171_, false, false))
                .setDepthTestState(EQUAL_DEPTH_TEST)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(false);
            return create("entity_decal", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, rendertype$compositestate);
        }
    );
    public static final Function<ResourceLocation, RenderType> ENTITY_NO_OUTLINE = Util.memoize(
        p_286160_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_NO_OUTLINE_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286160_, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false);
            return create("entity_no_outline", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true, rendertype$compositestate);
        }
    );
    public static final Function<ResourceLocation, RenderType> ENTITY_SHADOW = Util.memoize(
        p_286151_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_SHADOW_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286151_, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(false);
            return create("entity_shadow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false, rendertype$compositestate);
        }
    );
    public static final Function<ResourceLocation, RenderType> DRAGON_EXPLOSION_ALPHA = Util.memoize(
        p_286150_ -> {
            RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_ALPHA_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(p_286150_, false, false))
                .setCullState(NO_CULL)
                .createCompositeState(true);
            return create("entity_alpha", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, rendertype$compositestate);
        }
    );
    public static final BiFunction<ResourceLocation, RenderStateShard.TransparencyStateShard, RenderType> EYES = Util.memoize(
        (p_311464_, p_311465_) -> {
            RenderStateShard.TextureStateShard renderstateshard$texturestateshard = new RenderStateShard.TextureStateShard(p_311464_, false, false);
            return create(
                "eyes",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(renderstateshard$texturestateshard)
                    .setTransparencyState(p_311465_)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
            );
        }
    );
    public static final RenderType LEASH = create(
        "leash",
        DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
        VertexFormat.Mode.TRIANGLE_STRIP,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_LEASH_SHADER)
            .setTextureState(NO_TEXTURE)
            .setCullState(NO_CULL)
            .setLightmapState(LIGHTMAP)
            .createCompositeState(false)
    );
    public static final RenderType WATER_MASK = create(
        "water_mask",
        DefaultVertexFormat.POSITION,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_WATER_MASK_SHADER)
            .setTextureState(NO_TEXTURE)
            .setWriteMaskState(DEPTH_WRITE)
            .createCompositeState(false)
    );
    public static final RenderType ARMOR_ENTITY_GLINT = create(
        "armor_entity_glint",
        DefaultVertexFormat.POSITION_TEX,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ENTITY, true, false))
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setDepthTestState(EQUAL_DEPTH_TEST)
            .setTransparencyState(GLINT_TRANSPARENCY)
            .setTexturingState(ENTITY_GLINT_TEXTURING)
            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
            .createCompositeState(false)
    );
    public static final RenderType GLINT_TRANSLUCENT = create(
        "glint_translucent",
        DefaultVertexFormat.POSITION_TEX,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_GLINT_TRANSLUCENT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ITEM, true, false))
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setDepthTestState(EQUAL_DEPTH_TEST)
            .setTransparencyState(GLINT_TRANSPARENCY)
            .setTexturingState(GLINT_TEXTURING)
            .setOutputState(ITEM_ENTITY_TARGET)
            .createCompositeState(false)
    );
    public static final RenderType GLINT = create(
        "glint",
        DefaultVertexFormat.POSITION_TEX,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_GLINT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ITEM, true, false))
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setDepthTestState(EQUAL_DEPTH_TEST)
            .setTransparencyState(GLINT_TRANSPARENCY)
            .setTexturingState(GLINT_TEXTURING)
            .createCompositeState(false)
    );
    public static final RenderType ENTITY_GLINT = create(
        "entity_glint",
        DefaultVertexFormat.POSITION_TEX,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_GLINT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ENTITY, true, false))
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setDepthTestState(EQUAL_DEPTH_TEST)
            .setTransparencyState(GLINT_TRANSPARENCY)
            .setOutputState(ITEM_ENTITY_TARGET)
            .setTexturingState(ENTITY_GLINT_TEXTURING)
            .createCompositeState(false)
    );
    public static final RenderType ENTITY_GLINT_DIRECT = create(
        "entity_glint_direct",
        DefaultVertexFormat.POSITION_TEX,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_GLINT_DIRECT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ENTITY, true, false))
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setDepthTestState(EQUAL_DEPTH_TEST)
            .setTransparencyState(GLINT_TRANSPARENCY)
            .setTexturingState(ENTITY_GLINT_TEXTURING)
            .createCompositeState(false)
    );
    public static final Function<ResourceLocation, RenderType> CRUMBLING = Util.memoize(
        p_286174_ -> {
            RenderStateShard.TextureStateShard renderstateshard$texturestateshard = new RenderStateShard.TextureStateShard(p_286174_, false, false);
            return create(
                "crumbling",
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_CRUMBLING_SHADER)
                    .setTextureState(renderstateshard$texturestateshard)
                    .setTransparencyState(CRUMBLING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false)
            );
        }
    );
    public static final Function<ResourceLocation, RenderType> TEXT = Util.memoize(
        p_307114_ -> create(
                "text",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                786432,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(p_307114_, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false)
            )
    );
    public static final RenderType TEXT_BACKGROUND = create(
        "text_background",
        DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_TEXT_BACKGROUND_SHADER)
            .setTextureState(NO_TEXTURE)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(LIGHTMAP)
            .createCompositeState(false)
    );
    public static final Function<ResourceLocation, RenderType> TEXT_INTENSITY = Util.memoize(
        p_307113_ -> create(
                "text_intensity",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                786432,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_INTENSITY_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(p_307113_, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false)
            )
    );
    public static final Function<ResourceLocation, RenderType> TEXT_POLYGON_OFFSET = Util.memoize(
        p_286152_ -> create(
                "text_polygon_offset",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(p_286152_, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false)
            )
    );
    public static final Function<ResourceLocation, RenderType> TEXT_INTENSITY_POLYGON_OFFSET = Util.memoize(
        p_286175_ -> create(
                "text_intensity_polygon_offset",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_INTENSITY_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(p_286175_, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false)
            )
    );
    public static final Function<ResourceLocation, RenderType> TEXT_SEE_THROUGH = Util.memoize(
        p_286158_ -> create(
                "text_see_through",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(p_286158_, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
            )
    );
    public static final RenderType TEXT_BACKGROUND_SEE_THROUGH = create(
        "text_background_see_through",
        DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_TEXT_BACKGROUND_SEE_THROUGH_SHADER)
            .setTextureState(NO_TEXTURE)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(LIGHTMAP)
            .setDepthTestState(NO_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
    );
    public static final Function<ResourceLocation, RenderType> TEXT_INTENSITY_SEE_THROUGH = Util.memoize(
        p_286168_ -> create(
                "text_intensity_see_through",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_INTENSITY_SEE_THROUGH_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(p_286168_, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
            )
    );
    public static final RenderType LIGHTNING = create(
        "lightning",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
            .setWriteMaskState(COLOR_DEPTH_WRITE)
            .setTransparencyState(LIGHTNING_TRANSPARENCY)
            .setOutputState(WEATHER_TARGET)
            .createCompositeState(false)
    );
    public static final RenderType DRAGON_RAYS = create(
        "dragon_rays",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        1536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
            .setWriteMaskState(COLOR_WRITE)
            .setTransparencyState(LIGHTNING_TRANSPARENCY)
            .createCompositeState(false)
    );
    public static final RenderType DRAGON_RAYS_DEPTH = create(
        "dragon_rays_depth",
        DefaultVertexFormat.POSITION,
        VertexFormat.Mode.TRIANGLES,
        1536,
        false,
        false,
        RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_SHADER).setWriteMaskState(DEPTH_WRITE).createCompositeState(false)
    );
    public static final RenderType TRIPWIRE = create("tripwire", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, true, true, tripwireState());
    public static final RenderType END_PORTAL = create(
        "end_portal",
        DefaultVertexFormat.POSITION,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_END_PORTAL_SHADER)
            .setTextureState(
                RenderStateShard.MultiTextureStateShard.builder()
                    .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                    .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                    .build()
            )
            .createCompositeState(false)
    );
    public static final RenderType END_GATEWAY = create(
        "end_gateway",
        DefaultVertexFormat.POSITION,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_END_GATEWAY_SHADER)
            .setTextureState(
                RenderStateShard.MultiTextureStateShard.builder()
                    .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                    .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                    .build()
            )
            .createCompositeState(false)
    );
    public static final RenderType CLOUDS = createClouds(false);
    public static final RenderType CLOUDS_DEPTH_ONLY = createClouds(true);
    public static final RenderType.CompositeRenderType LINES = create(
        "lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setOutputState(ITEM_ENTITY_TARGET)
            .setWriteMaskState(COLOR_DEPTH_WRITE)
            .setCullState(NO_CULL)
            .createCompositeState(false)
    );
    public static final RenderType.CompositeRenderType LINE_STRIP = create(
        "line_strip",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINE_STRIP,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setOutputState(ITEM_ENTITY_TARGET)
            .setWriteMaskState(COLOR_DEPTH_WRITE)
            .setCullState(NO_CULL)
            .createCompositeState(false)
    );
    public static final Function<Double, RenderType.CompositeRenderType> DEBUG_LINE_STRIP = Util.memoize(
        p_286162_ -> create(
                "debug_line_strip",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.DEBUG_LINE_STRIP,
                1536,
                RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(p_286162_)))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
            )
    );
    public static final RenderType.CompositeRenderType DEBUG_FILLED_BOX = create(
        "debug_filled_box",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLE_STRIP,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER)
            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .createCompositeState(false)
    );
    public static final RenderType.CompositeRenderType DEBUG_QUADS = create(
        "debug_quads",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .createCompositeState(false)
    );
    public static final RenderType.CompositeRenderType DEBUG_STRUCTURE_QUADS = create(
        "debug_structure_quads",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
    );
    public static final RenderType.CompositeRenderType DEBUG_SECTION_QUADS = create(
        "debug_section_quads",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER)
            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(CULL)
            .createCompositeState(false)
    );
    public static final RenderType.CompositeRenderType GUI = create(
        "gui",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        786432,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_GUI_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .createCompositeState(false)
    );
    public static final RenderType.CompositeRenderType GUI_OVERLAY = create(
        "gui_overlay",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_GUI_OVERLAY_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(NO_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
    );
    public static final RenderType.CompositeRenderType GUI_TEXT_HIGHLIGHT = create(
        "gui_text_highlight",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_GUI_TEXT_HIGHLIGHT_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(NO_DEPTH_TEST)
            .setColorLogicState(OR_REVERSE_COLOR_LOGIC)
            .createCompositeState(false)
    );
    public static final RenderType.CompositeRenderType GUI_GHOST_RECIPE_OVERLAY = create(
        "gui_ghost_recipe_overlay",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_GUI_GHOST_RECIPE_OVERLAY_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(GREATER_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
    );
    public static final ImmutableList<RenderType> CHUNK_BUFFER_LAYERS = ImmutableList.of(solid(), cutoutMipped(), cutout(), translucent(), tripwire());
    public final VertexFormat format;
    public final VertexFormat.Mode mode;
    public final int bufferSize;
    public final boolean affectsCrumbling;
    public final boolean sortOnUpload;

    public static RenderType solid() {
        return SOLID;
    }

    public static RenderType cutoutMipped() {
        return CUTOUT_MIPPED;
    }

    public static RenderType cutout() {
        return CUTOUT;
    }

    private static RenderType.CompositeState translucentState(RenderStateShard.ShaderStateShard state) {
        return RenderType.CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(state)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setOutputState(TRANSLUCENT_TARGET)
            .createCompositeState(true);
    }

    public static RenderType translucent() {
        return TRANSLUCENT;
    }

    private static RenderType.CompositeState translucentMovingBlockState() {
        return RenderType.CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(RENDERTYPE_TRANSLUCENT_MOVING_BLOCK_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setOutputState(ITEM_ENTITY_TARGET)
            .createCompositeState(true);
    }

    public static RenderType translucentMovingBlock() {
        return TRANSLUCENT_MOVING_BLOCK;
    }

    private static RenderType.CompositeRenderType createArmorCutoutNoCull(String name, ResourceLocation id, boolean equalDepthTest) {
        RenderType.CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ARMOR_CUTOUT_NO_CULL_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(id, false, false))
            .setTransparencyState(NO_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
            .setDepthTestState(equalDepthTest ? EQUAL_DEPTH_TEST : LEQUAL_DEPTH_TEST)
            .createCompositeState(true);
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate);
    }

    public static RenderType armorCutoutNoCull(ResourceLocation location) {
        return ARMOR_CUTOUT_NO_CULL.apply(location);
    }

    public static RenderType createArmorDecalCutoutNoCull(ResourceLocation id) {
        return createArmorCutoutNoCull("armor_decal_cutout_no_cull", id, true);
    }

    public static RenderType entitySolid(ResourceLocation location) {
        return ENTITY_SOLID.apply(location);
    }

    public static RenderType entityCutout(ResourceLocation location) {
        return ENTITY_CUTOUT.apply(location);
    }

    public static RenderType entityCutoutNoCull(ResourceLocation location, boolean outline) {
        return ENTITY_CUTOUT_NO_CULL.apply(location, outline);
    }

    public static RenderType entityCutoutNoCull(ResourceLocation location) {
        return entityCutoutNoCull(location, true);
    }

    public static RenderType entityCutoutNoCullZOffset(ResourceLocation location, boolean outline) {
        return ENTITY_CUTOUT_NO_CULL_Z_OFFSET.apply(location, outline);
    }

    public static RenderType entityCutoutNoCullZOffset(ResourceLocation location) {
        return entityCutoutNoCullZOffset(location, true);
    }

    public static RenderType itemEntityTranslucentCull(ResourceLocation location) {
        return ITEM_ENTITY_TRANSLUCENT_CULL.apply(location);
    }

    public static RenderType entityTranslucentCull(ResourceLocation location) {
        return ENTITY_TRANSLUCENT_CULL.apply(location);
    }

    public static RenderType entityTranslucent(ResourceLocation location, boolean outline) {
        return ENTITY_TRANSLUCENT.apply(location, outline);
    }

    public static RenderType entityTranslucent(ResourceLocation location) {
        return entityTranslucent(location, true);
    }

    public static RenderType entityTranslucentEmissive(ResourceLocation location, boolean outline) {
        return ENTITY_TRANSLUCENT_EMISSIVE.apply(location, outline);
    }

    public static RenderType entityTranslucentEmissive(ResourceLocation location) {
        return entityTranslucentEmissive(location, true);
    }

    public static RenderType entitySmoothCutout(ResourceLocation location) {
        return ENTITY_SMOOTH_CUTOUT.apply(location);
    }

    public static RenderType beaconBeam(ResourceLocation location, boolean colorFlag) {
        return BEACON_BEAM.apply(location, colorFlag);
    }

    public static RenderType entityDecal(ResourceLocation location) {
        return ENTITY_DECAL.apply(location);
    }

    public static RenderType entityNoOutline(ResourceLocation location) {
        return ENTITY_NO_OUTLINE.apply(location);
    }

    public static RenderType entityShadow(ResourceLocation location) {
        return ENTITY_SHADOW.apply(location);
    }

    public static RenderType dragonExplosionAlpha(ResourceLocation id) {
        return DRAGON_EXPLOSION_ALPHA.apply(id);
    }

    public static RenderType eyes(ResourceLocation location) {
        return EYES.apply(location, ADDITIVE_TRANSPARENCY);
    }

    public static RenderType breezeEyes(ResourceLocation location) {
        return ENTITY_TRANSLUCENT_EMISSIVE.apply(location, false);
    }

    public static RenderType breezeWind(ResourceLocation location, float u, float v) {
        return create(
            "breeze_wind",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_BREEZE_WIND_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(location, false, false))
                .setTexturingState(new RenderStateShard.OffsetTexturingStateShard(u, v))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(false)
        );
    }

    public static RenderType energySwirl(ResourceLocation location, float u, float v) {
        return create(
            "energy_swirl",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENERGY_SWIRL_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(location, false, false))
                .setTexturingState(new RenderStateShard.OffsetTexturingStateShard(u, v))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(false)
        );
    }

    public static RenderType leash() {
        return LEASH;
    }

    public static RenderType waterMask() {
        return WATER_MASK;
    }

    public static RenderType outline(ResourceLocation location) {
        return RenderType.CompositeRenderType.OUTLINE.apply(location, NO_CULL);
    }

    public static RenderType armorEntityGlint() {
        return ARMOR_ENTITY_GLINT;
    }

    public static RenderType glintTranslucent() {
        return GLINT_TRANSLUCENT;
    }

    public static RenderType glint() {
        return GLINT;
    }

    public static RenderType entityGlint() {
        return ENTITY_GLINT;
    }

    public static RenderType entityGlintDirect() {
        return ENTITY_GLINT_DIRECT;
    }

    public static RenderType crumbling(ResourceLocation location) {
        return CRUMBLING.apply(location);
    }

    public static RenderType text(ResourceLocation location) {
        return net.neoforged.neoforge.client.NeoForgeRenderTypes.getText(location);
    }

    public static RenderType textBackground() {
        return TEXT_BACKGROUND;
    }

    public static RenderType textIntensity(ResourceLocation id) {
        return net.neoforged.neoforge.client.NeoForgeRenderTypes.getTextIntensity(id);
    }

    public static RenderType textPolygonOffset(ResourceLocation id) {
        return net.neoforged.neoforge.client.NeoForgeRenderTypes.getTextPolygonOffset(id);
    }

    public static RenderType textIntensityPolygonOffset(ResourceLocation id) {
        return net.neoforged.neoforge.client.NeoForgeRenderTypes.getTextIntensityPolygonOffset(id);
    }

    public static RenderType textSeeThrough(ResourceLocation location) {
        return net.neoforged.neoforge.client.NeoForgeRenderTypes.getTextSeeThrough(location);
    }

    public static RenderType textBackgroundSeeThrough() {
        return TEXT_BACKGROUND_SEE_THROUGH;
    }

    public static RenderType textIntensitySeeThrough(ResourceLocation id) {
        return net.neoforged.neoforge.client.NeoForgeRenderTypes.getTextIntensitySeeThrough(id);
    }

    public static RenderType lightning() {
        return LIGHTNING;
    }

    public static RenderType dragonRays() {
        return DRAGON_RAYS;
    }

    public static RenderType dragonRaysDepth() {
        return DRAGON_RAYS_DEPTH;
    }

    private static RenderType.CompositeState tripwireState() {
        return RenderType.CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(RENDERTYPE_TRIPWIRE_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setOutputState(WEATHER_TARGET)
            .createCompositeState(true);
    }

    public static RenderType tripwire() {
        return TRIPWIRE;
    }

    public static RenderType endPortal() {
        return END_PORTAL;
    }

    public static RenderType endGateway() {
        return END_GATEWAY;
    }

    private static RenderType.CompositeRenderType createClouds(boolean color) {
        return create(
            "clouds",
            DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
            VertexFormat.Mode.QUADS,
            786432,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_CLOUDS_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(LevelRenderer.CLOUDS_LOCATION, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(color ? DEPTH_WRITE : COLOR_DEPTH_WRITE)
                .setOutputState(CLOUDS_TARGET)
                .createCompositeState(true)
        );
    }

    public static RenderType clouds() {
        return CLOUDS;
    }

    public static RenderType cloudsDepthOnly() {
        return CLOUDS_DEPTH_ONLY;
    }

    public static RenderType lines() {
        return LINES;
    }

    public static RenderType lineStrip() {
        return LINE_STRIP;
    }

    public static RenderType debugLineStrip(double width) {
        return DEBUG_LINE_STRIP.apply(width);
    }

    public static RenderType debugFilledBox() {
        return DEBUG_FILLED_BOX;
    }

    public static RenderType debugQuads() {
        return DEBUG_QUADS;
    }

    public static RenderType debugStructureQuads() {
        return DEBUG_STRUCTURE_QUADS;
    }

    public static RenderType debugSectionQuads() {
        return DEBUG_SECTION_QUADS;
    }

    public static RenderType gui() {
        return GUI;
    }

    public static RenderType guiOverlay() {
        return GUI_OVERLAY;
    }

    public static RenderType guiTextHighlight() {
        return GUI_TEXT_HIGHLIGHT;
    }

    public static RenderType guiGhostRecipeOverlay() {
        return GUI_GHOST_RECIPE_OVERLAY;
    }

    public RenderType(
        String name,
        VertexFormat format,
        VertexFormat.Mode mode,
        int bufferSize,
        boolean affectsCrumbling,
        boolean sortOnUpload,
        Runnable setupState,
        Runnable clearState
    ) {
        super(name, setupState, clearState);
        this.format = format;
        this.mode = mode;
        this.bufferSize = bufferSize;
        this.affectsCrumbling = affectsCrumbling;
        this.sortOnUpload = sortOnUpload;
    }

    public static RenderType.CompositeRenderType create(
        String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, RenderType.CompositeState state
    ) {
        return create(name, format, mode, bufferSize, false, false, state);
    }

    public static RenderType.CompositeRenderType create(
        String name,
        VertexFormat format,
        VertexFormat.Mode mode,
        int bufferSize,
        boolean affectsCrumbling,
        boolean sortOnUpload,
        RenderType.CompositeState state
    ) {
        return new RenderType.CompositeRenderType(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, state);
    }

    public void draw(MeshData meshData) {
        this.setupRenderState();
        BufferUploader.drawWithShader(meshData);
        this.clearRenderState();
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static List<RenderType> chunkBufferLayers() {
        return CHUNK_BUFFER_LAYERS;
    }

    public int bufferSize() {
        return this.bufferSize;
    }

    public VertexFormat format() {
        return this.format;
    }

    public VertexFormat.Mode mode() {
        return this.mode;
    }

    public Optional<RenderType> outline() {
        return Optional.empty();
    }

    public boolean isOutline() {
        return false;
    }

    public boolean affectsCrumbling() {
        return this.affectsCrumbling;
    }

    public boolean canConsolidateConsecutiveGeometry() {
        return !this.mode.connectedPrimitives;
    }

    public boolean sortOnUpload() {
        return this.sortOnUpload;
    }

    @OnlyIn(Dist.CLIENT)
    static final class CompositeRenderType extends RenderType {
        static final BiFunction<ResourceLocation, RenderStateShard.CullStateShard, RenderType> OUTLINE = Util.memoize(
            (p_349872_, p_349873_) -> RenderType.create(
                    "outline",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_OUTLINE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(p_349872_, false, false))
                        .setCullState(p_349873_)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setOutputState(OUTLINE_TARGET)
                        .createCompositeState(RenderType.OutlineProperty.IS_OUTLINE)
                )
        );
        private final RenderType.CompositeState state;
        private final Optional<RenderType> outline;
        private final boolean isOutline;

        CompositeRenderType(
            String name,
            VertexFormat format,
            VertexFormat.Mode mode,
            int bufferSize,
            boolean affectsCrumbling,
            boolean sortOnUpload,
            RenderType.CompositeState state
        ) {
            super(
                name,
                format,
                mode,
                bufferSize,
                affectsCrumbling,
                sortOnUpload,
                () -> state.states.forEach(RenderStateShard::setupRenderState),
                () -> state.states.forEach(RenderStateShard::clearRenderState)
            );
            this.state = state;
            this.outline = state.outlineProperty == RenderType.OutlineProperty.AFFECTS_OUTLINE
                ? state.textureState.cutoutTexture().map(p_173270_ -> OUTLINE.apply(p_173270_, state.cullState))
                : Optional.empty();
            this.isOutline = state.outlineProperty == RenderType.OutlineProperty.IS_OUTLINE;
        }

        @Override
        public Optional<RenderType> outline() {
            return this.outline;
        }

        @Override
        public boolean isOutline() {
            return this.isOutline;
        }

        protected final RenderType.CompositeState state() {
            return this.state;
        }

        @Override
        public String toString() {
            return "RenderType[" + this.name + ":" + this.state + "]";
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static final class CompositeState {
        final RenderStateShard.EmptyTextureStateShard textureState;
        private final RenderStateShard.ShaderStateShard shaderState;
        private final RenderStateShard.TransparencyStateShard transparencyState;
        private final RenderStateShard.DepthTestStateShard depthTestState;
        final RenderStateShard.CullStateShard cullState;
        private final RenderStateShard.LightmapStateShard lightmapState;
        private final RenderStateShard.OverlayStateShard overlayState;
        private final RenderStateShard.LayeringStateShard layeringState;
        private final RenderStateShard.OutputStateShard outputState;
        private final RenderStateShard.TexturingStateShard texturingState;
        private final RenderStateShard.WriteMaskStateShard writeMaskState;
        private final RenderStateShard.LineStateShard lineState;
        private final RenderStateShard.ColorLogicStateShard colorLogicState;
        final RenderType.OutlineProperty outlineProperty;
        final ImmutableList<RenderStateShard> states;

        CompositeState(
            RenderStateShard.EmptyTextureStateShard textureState,
            RenderStateShard.ShaderStateShard shaderState,
            RenderStateShard.TransparencyStateShard transparencyState,
            RenderStateShard.DepthTestStateShard depthState,
            RenderStateShard.CullStateShard cullState,
            RenderStateShard.LightmapStateShard lightmapState,
            RenderStateShard.OverlayStateShard overlayState,
            RenderStateShard.LayeringStateShard layeringState,
            RenderStateShard.OutputStateShard outputState,
            RenderStateShard.TexturingStateShard texturingState,
            RenderStateShard.WriteMaskStateShard writeMaskState,
            RenderStateShard.LineStateShard lineState,
            RenderStateShard.ColorLogicStateShard colorLogicState,
            RenderType.OutlineProperty outlineProperty
        ) {
            this.textureState = textureState;
            this.shaderState = shaderState;
            this.transparencyState = transparencyState;
            this.depthTestState = depthState;
            this.cullState = cullState;
            this.lightmapState = lightmapState;
            this.overlayState = overlayState;
            this.layeringState = layeringState;
            this.outputState = outputState;
            this.texturingState = texturingState;
            this.writeMaskState = writeMaskState;
            this.lineState = lineState;
            this.colorLogicState = colorLogicState;
            this.outlineProperty = outlineProperty;
            this.states = ImmutableList.of(
                this.textureState,
                this.shaderState,
                this.transparencyState,
                this.depthTestState,
                this.cullState,
                this.lightmapState,
                this.overlayState,
                this.layeringState,
                this.outputState,
                this.texturingState,
                this.writeMaskState,
                this.colorLogicState,
                this.lineState
            );
        }

        @Override
        public String toString() {
            return "CompositeState[" + this.states + ", outlineProperty=" + this.outlineProperty + "]";
        }

        public static RenderType.CompositeState.CompositeStateBuilder builder() {
            return new RenderType.CompositeState.CompositeStateBuilder();
        }

        @OnlyIn(Dist.CLIENT)
        public static class CompositeStateBuilder {
            private RenderStateShard.EmptyTextureStateShard textureState = RenderStateShard.NO_TEXTURE;
            private RenderStateShard.ShaderStateShard shaderState = RenderStateShard.NO_SHADER;
            private RenderStateShard.TransparencyStateShard transparencyState = RenderStateShard.NO_TRANSPARENCY;
            private RenderStateShard.DepthTestStateShard depthTestState = RenderStateShard.LEQUAL_DEPTH_TEST;
            private RenderStateShard.CullStateShard cullState = RenderStateShard.CULL;
            private RenderStateShard.LightmapStateShard lightmapState = RenderStateShard.NO_LIGHTMAP;
            private RenderStateShard.OverlayStateShard overlayState = RenderStateShard.NO_OVERLAY;
            private RenderStateShard.LayeringStateShard layeringState = RenderStateShard.NO_LAYERING;
            private RenderStateShard.OutputStateShard outputState = RenderStateShard.MAIN_TARGET;
            private RenderStateShard.TexturingStateShard texturingState = RenderStateShard.DEFAULT_TEXTURING;
            private RenderStateShard.WriteMaskStateShard writeMaskState = RenderStateShard.COLOR_DEPTH_WRITE;
            private RenderStateShard.LineStateShard lineState = RenderStateShard.DEFAULT_LINE;
            private RenderStateShard.ColorLogicStateShard colorLogicState = RenderStateShard.NO_COLOR_LOGIC;

            CompositeStateBuilder() {
            }

            public RenderType.CompositeState.CompositeStateBuilder setTextureState(RenderStateShard.EmptyTextureStateShard textureState) {
                this.textureState = textureState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setShaderState(RenderStateShard.ShaderStateShard shaderState) {
                this.shaderState = shaderState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setTransparencyState(RenderStateShard.TransparencyStateShard transparencyState) {
                this.transparencyState = transparencyState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setDepthTestState(RenderStateShard.DepthTestStateShard depthTestState) {
                this.depthTestState = depthTestState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setCullState(RenderStateShard.CullStateShard cullState) {
                this.cullState = cullState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setLightmapState(RenderStateShard.LightmapStateShard lightmapState) {
                this.lightmapState = lightmapState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setOverlayState(RenderStateShard.OverlayStateShard overlayState) {
                this.overlayState = overlayState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setLayeringState(RenderStateShard.LayeringStateShard layerState) {
                this.layeringState = layerState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setOutputState(RenderStateShard.OutputStateShard outputState) {
                this.outputState = outputState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setTexturingState(RenderStateShard.TexturingStateShard texturingState) {
                this.texturingState = texturingState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setWriteMaskState(RenderStateShard.WriteMaskStateShard writeMaskState) {
                this.writeMaskState = writeMaskState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setLineState(RenderStateShard.LineStateShard lineState) {
                this.lineState = lineState;
                return this;
            }

            public RenderType.CompositeState.CompositeStateBuilder setColorLogicState(RenderStateShard.ColorLogicStateShard colorLogicState) {
                this.colorLogicState = colorLogicState;
                return this;
            }

            public RenderType.CompositeState createCompositeState(boolean outline) {
                return this.createCompositeState(outline ? RenderType.OutlineProperty.AFFECTS_OUTLINE : RenderType.OutlineProperty.NONE);
            }

            public RenderType.CompositeState createCompositeState(RenderType.OutlineProperty outlineState) {
                return new RenderType.CompositeState(
                    this.textureState,
                    this.shaderState,
                    this.transparencyState,
                    this.depthTestState,
                    this.cullState,
                    this.lightmapState,
                    this.overlayState,
                    this.layeringState,
                    this.outputState,
                    this.texturingState,
                    this.writeMaskState,
                    this.lineState,
                    this.colorLogicState,
                    outlineState
                );
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum OutlineProperty {
        NONE("none"),
        IS_OUTLINE("is_outline"),
        AFFECTS_OUTLINE("affects_outline");

        private final String name;

        private OutlineProperty(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    // Neo: Assign internal IDs for RenderType to be used in rendering
    public int chunkLayerId = -1;
    /** {@return the unique ID of this {@link RenderType} for chunk rendering purposes, or {@literal -1} if this is not a chunk {@link RenderType}} */
    public final int getChunkLayerId() {
        return chunkLayerId;
    }
    static {
        int i = 0;
        for (var layer : chunkBufferLayers())
            layer.chunkLayerId = i++;
    }
}

package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockRenderDispatcher implements ResourceManagerReloadListener {
    private final BlockModelShaper blockModelShaper;
    private final ModelBlockRenderer modelRenderer;
    private final BlockEntityWithoutLevelRenderer blockEntityRenderer;
    private final LiquidBlockRenderer liquidBlockRenderer;
    private final RandomSource random = RandomSource.create();
    private final BlockColors blockColors;

    public BlockRenderDispatcher(BlockModelShaper blockModelShaper, BlockEntityWithoutLevelRenderer blockEntityRenderer, BlockColors blockColors) {
        this.blockModelShaper = blockModelShaper;
        this.blockEntityRenderer = blockEntityRenderer;
        this.blockColors = blockColors;
        this.modelRenderer = new net.neoforged.neoforge.client.model.lighting.LightPipelineAwareModelBlockRenderer(this.blockColors);
        this.liquidBlockRenderer = new LiquidBlockRenderer();
    }

    public BlockModelShaper getBlockModelShaper() {
        return this.blockModelShaper;
    }

    @Deprecated //Forge: Model data parameter
    public void renderBreakingTexture(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack, VertexConsumer consumer) {
         renderBreakingTexture(state, pos, level, poseStack, consumer, net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
    }
    public void renderBreakingTexture(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack, VertexConsumer consumer, net.neoforged.neoforge.client.model.data.ModelData modelData) {
        if (state.getRenderShape() == RenderShape.MODEL) {
            BakedModel bakedmodel = this.blockModelShaper.getBlockModel(state);
            long i = state.getSeed(pos);
            modelData = bakedmodel.getModelData(level, pos, state, modelData);
            this.modelRenderer
                .tesselateBlock(level, bakedmodel, state, pos, poseStack, consumer, true, this.random, i, OverlayTexture.NO_OVERLAY, modelData, null);
        }
    }

    public void renderBatched(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            VertexConsumer consumer,
            boolean checkSides,
            RandomSource random
    ) {
        renderBatched(state, pos, level, poseStack, consumer, checkSides, random, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
    }

    public void renderBatched(
        BlockState state,
        BlockPos pos,
        BlockAndTintGetter level,
        PoseStack poseStack,
        VertexConsumer consumer,
        boolean checkSides,
        RandomSource random,
        net.neoforged.neoforge.client.model.data.ModelData modelData,
        net.minecraft.client.renderer.RenderType renderType
    ) {
        try {
            this.modelRenderer
                .tesselateBlock(
                    level,
                    this.getBlockModel(state),
                    state,
                    pos,
                    poseStack,
                    consumer,
                    checkSides,
                    random,
                    state.getSeed(pos),
                    OverlayTexture.NO_OVERLAY,
                    modelData,
                    renderType
                );
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Tesselating block in world");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being tesselated");
            CrashReportCategory.populateBlockDetails(crashreportcategory, level, pos, state);
            throw new ReportedException(crashreport);
        }
    }

    public void renderLiquid(BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer, BlockState blockState, FluidState fluidState) {
        try {
            if (net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluidState).renderFluid(fluidState, level, pos, consumer, blockState)) return;
            this.liquidBlockRenderer.tesselate(level, pos, consumer, blockState, fluidState);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Tesselating liquid in world");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being tesselated");
            CrashReportCategory.populateBlockDetails(crashreportcategory, level, pos, null);
            throw new ReportedException(crashreport);
        }
    }

    public ModelBlockRenderer getModelRenderer() {
        return this.modelRenderer;
    }

    public BakedModel getBlockModel(BlockState state) {
        return this.blockModelShaper.getBlockModel(state);
    }

    @Deprecated //Forge: Model data and render type parameter
    public void renderSingleBlock(BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
    }
    public void renderSingleBlock(BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, net.neoforged.neoforge.client.model.data.ModelData modelData, net.minecraft.client.renderer.RenderType renderType) {
        RenderShape rendershape = state.getRenderShape();
        if (rendershape != RenderShape.INVISIBLE) {
            switch (rendershape) {
                case MODEL:
                    BakedModel bakedmodel = this.getBlockModel(state);
                    int i = this.blockColors.getColor(state, null, null, 0);
                    float f = (float)(i >> 16 & 0xFF) / 255.0F;
                    float f1 = (float)(i >> 8 & 0xFF) / 255.0F;
                    float f2 = (float)(i & 0xFF) / 255.0F;
                    for (net.minecraft.client.renderer.RenderType rt : bakedmodel.getRenderTypes(state, RandomSource.create(42), modelData))
                    this.modelRenderer
                        .renderModel(
                            poseStack.last(),
                            bufferSource.getBuffer(renderType != null ? renderType : net.neoforged.neoforge.client.RenderTypeHelper.getEntityRenderType(rt, false)),
                            state,
                            bakedmodel,
                            f,
                            f1,
                            f2,
                            packedLight,
                            packedOverlay,
                            modelData,
                            rt
                        );
                    break;
                case ENTITYBLOCK_ANIMATED:
                    ItemStack stack = new ItemStack(state.getBlock());
                    net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(stack, ItemDisplayContext.NONE, poseStack, bufferSource, packedLight, packedOverlay);
            }
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.liquidBlockRenderer.setupSprites();
    }

    public LiquidBlockRenderer getLiquidBlockRenderer() {
        return this.liquidBlockRenderer;
    }
}

package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.survival.filmprinter.FilmPrinterBlock;
import dev.rew1nd.sableschematicapi.survival.projector.ProjectorBlock;
import dev.rew1nd.sableschematicapi.survival.projector.ProjectorScreenLocatorBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SableSchematicApiBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, SableSchematicApi.MODID);

    public static final DeferredHolder<Block, BlueprintCannonBlock> BLUEPRINT_CANNON = BLOCKS.register(
            "blueprint_cannon",
            () -> new BlueprintCannonBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isRedstoneConductor((s, r, p) -> false)
                    .isViewBlocking((s, r, p) -> false))
    );

    public static final DeferredHolder<Block, BlueprintTableBlock> BLUEPRINT_TABLE = BLOCKS.register(
            "blueprint_table",
            () -> new BlueprintTableBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.WOOD)
                    .strength(2.5F)
                    .noOcclusion()
                    .isRedstoneConductor((s, r, p) -> false)
                    .isViewBlocking((s, r, p) -> false))
    );

    public static final DeferredHolder<Block, FilmPrinterBlock> FILM_PRINTER = BLOCKS.register(
            "film_printer",
            () -> new FilmPrinterBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isRedstoneConductor((s, r, p) -> false)
                    .isViewBlocking((s, r, p) -> false))
    );

    public static final DeferredHolder<Block, ProjectorBlock> PROJECTOR = BLOCKS.register(
            "projector",
            () -> new ProjectorBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isRedstoneConductor((s, r, p) -> false)
                    .isViewBlocking((s, r, p) -> false))
    );

    public static final DeferredHolder<Block, Block> PROJECTOR_BASE = BLOCKS.register(
            "projector_base",
            () -> new Block(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isRedstoneConductor((s, r, p) -> false)
                    .isViewBlocking((s, r, p) -> false))
    );

    public static final DeferredHolder<Block, Block> PROJECTOR_SCREEN = BLOCKS.register(
            "projector_screen",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE))
    );

    public static final DeferredHolder<Block, ProjectorScreenLocatorBlock> PROJECTOR_SCREEN_LOCATOR = BLOCKS.register(
            "projector_screen_locator",
            () -> new ProjectorScreenLocatorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE))
    );

    private SableSchematicApiBlocks() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}

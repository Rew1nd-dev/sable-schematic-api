package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
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

    private SableSchematicApiBlocks() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}

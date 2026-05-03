package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SableSchematicApiBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE,
            SableSchematicApi.MODID
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlueprintCannonBlockEntity>> BLUEPRINT_CANNON = BLOCK_ENTITIES.register(
            "blueprint_cannon",
            () -> BlockEntityType.Builder.of(
                    BlueprintCannonBlockEntity::new,
                    SableSchematicApiBlocks.BLUEPRINT_CANNON.get()
            ).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlueprintTableBlockEntity>> BLUEPRINT_TABLE = BLOCK_ENTITIES.register(
            "blueprint_table",
            () -> BlockEntityType.Builder.of(
                    BlueprintTableBlockEntity::new,
                    SableSchematicApiBlocks.BLUEPRINT_TABLE.get()
            ).build(null)
    );

    private SableSchematicApiBlockEntities() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}

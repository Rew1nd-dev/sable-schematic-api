package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.io.IOException;

public class BlueprintTableBlockEntity extends BlockEntity {
    public static final int BLUEPRINT_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    private static final String KEY_INVENTORY = "inventory";

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public int getSlotLimit(final int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return BlueprintDataItem.isBlueprintItem(stack);
        }

        @Override
        protected void onContentsChanged(final int slot) {
            BlueprintTableBlockEntity.this.setChanged();
        }
    };

    public BlueprintTableBlockEntity(final BlockPos pos, final BlockState state) {
        super(SableSchematicApiBlockEntities.BLUEPRINT_TABLE.get(), pos, state);
    }

    public ItemStackHandler inventory() {
        return this.inventory;
    }

    public ItemStack insertBlueprint(final ItemStack stack) {
        if (stack.isEmpty() || !BlueprintDataItem.isBlueprintItem(stack)) {
            return stack;
        }
        final ItemStack single = stack.copyWithCount(1);
        final ItemStack remaining = this.inventory.insertItem(BLUEPRINT_SLOT, single, false);
        if (remaining.isEmpty()) {
            final ItemStack remainder = stack.copy();
            remainder.shrink(1);
            return remainder;
        }
        return stack;
    }

    public ItemStack ejectBlueprint() {
        final ItemStack stack = this.inventory.getStackInSlot(BLUEPRINT_SLOT);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.inventory.setStackInSlot(BLUEPRINT_SLOT, ItemStack.EMPTY);
        return stack;
    }

    /**
     * Saves the compressed blueprint to the server file system and writes a
     * reference (uploader + name + hash + summary) into the item in the table slot.
     */
    public boolean uploadBlueprint(final String uploader,
                                   final String name,
                                   final byte[] compressedData,
                                   final byte[] hash) {
        if (this.level == null || this.level.isClientSide()) {
            return false;
        }
        final ItemStack stack = this.inventory.getStackInSlot(BLUEPRINT_SLOT);
        if (stack.isEmpty() || !BlueprintDataItem.isBlueprintItem(stack)) {
            return false;
        }
        try {
            final SableBlueprint blueprint = BlueprintPayloads.readCompressed(compressedData);
            final var summary = dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary.of(blueprint);
            BlueprintServerFiles.save((ServerLevel) this.level, uploader, name, compressedData);
            final ItemStack result = BlueprintDataItem.createFromServerFile(uploader, name, hash, summary);
            this.inventory.setStackInSlot(BLUEPRINT_SLOT, result);
            this.setChanged();
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_INVENTORY, this.inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_INVENTORY)) {
            this.inventory.deserializeNBT(registries, tag.getCompound(KEY_INVENTORY));
        }
    }
}

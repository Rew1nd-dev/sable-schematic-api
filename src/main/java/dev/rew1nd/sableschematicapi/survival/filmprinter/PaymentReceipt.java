package dev.rew1nd.sableschematicapi.survival.filmprinter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Exact finite-source items removed while paying for one film. */
public record PaymentReceipt(List<ItemStack> items) {
    public static final PaymentReceipt EMPTY = new PaymentReceipt(List.of());

    public PaymentReceipt {
        final List<ItemStack> copies = new ArrayList<>();
        for (final ItemStack item : items == null ? List.<ItemStack>of() : items) {
            merge(copies, item);
        }
        items = List.copyOf(copies);
    }

    @Override
    public List<ItemStack> items() {
        return this.items.stream().map(ItemStack::copy).toList();
    }

    public CompoundTag save(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        final ListTag list = new ListTag();
        for (final ItemStack item : this.items) {
            list.add(item.saveOptional(registries));
        }
        tag.put("items", list);
        return tag;
    }

    public static PaymentReceipt load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
        final List<ItemStack> items = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            final ItemStack item = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return new PaymentReceipt(items);
    }

    public int totalItems() {
        long total = 0L;
        for (final ItemStack item : this.items) {
            total += item.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    static void merge(final List<ItemStack> target, final ItemStack incoming) {
        if (incoming == null || incoming.isEmpty() || incoming.getCount() <= 0) {
            return;
        }
        for (final ItemStack existing : target) {
            if (ItemStack.isSameItemSameComponents(existing, incoming)) {
                existing.setCount(Math.addExact(existing.getCount(), incoming.getCount()));
                return;
            }
        }
        target.add(incoming.copy());
    }
}

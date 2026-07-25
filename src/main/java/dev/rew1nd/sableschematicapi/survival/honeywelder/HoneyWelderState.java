package dev.rew1nd.sableschematicapi.survival.honeywelder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Persistent per-stack fuel and selected consumer state. */
public record HoneyWelderState(int fuel, Optional<ResourceLocation> selectedConsumer) {
    public static final HoneyWelderState EMPTY = new HoneyWelderState(0, Optional.empty());
    public static final Codec<HoneyWelderState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("fuel", 0).forGetter(HoneyWelderState::fuel),
            ResourceLocation.CODEC.optionalFieldOf("selected_consumer").forGetter(HoneyWelderState::selectedConsumer)
    ).apply(instance, HoneyWelderState::new));

    public HoneyWelderState {
        fuel = Math.max(0, fuel);
        selectedConsumer = selectedConsumer == null ? Optional.empty() : selectedConsumer;
    }

    public static HoneyWelderState read(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY;
        }
        return stack.getOrDefault(SableSchematicApiDataComponents.HONEY_WELDER_STATE.get(), EMPTY).normalized();
    }

    public static void write(final ItemStack stack, final HoneyWelderState state) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(
                SableSchematicApiDataComponents.HONEY_WELDER_STATE.get(),
                (state == null ? EMPTY : state).normalized()
        );
    }

    public HoneyWelderState normalized() {
        final int clamped = Math.clamp(this.fuel, 0, HoneyWelderConfig.capacity());
        return clamped == this.fuel ? this : new HoneyWelderState(clamped, this.selectedConsumer);
    }

    public HoneyWelderState withFuel(final int fuel) {
        return new HoneyWelderState(fuel, this.selectedConsumer).normalized();
    }

    public HoneyWelderState withSelectedConsumer(final ResourceLocation consumer) {
        return new HoneyWelderState(this.fuel, Optional.ofNullable(consumer)).normalized();
    }
}

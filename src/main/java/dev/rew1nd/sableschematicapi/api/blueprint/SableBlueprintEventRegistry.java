package dev.rew1nd.sableschematicapi.api.blueprint;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class SableBlueprintEventRegistry {
    private static final List<SableBlueprintEvent> EVENTS = new ObjectArrayList<>();

    private SableBlueprintEventRegistry() {
    }

    public static void register(final SableBlueprintEvent event) {
        EVENTS.add(event);
    }

    public static List<SableBlueprintEvent> events() {
        return List.copyOf(EVENTS);
    }

    public static void saveBeforeBlocks(final BlueprintSaveSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            final CompoundTag eventData = eventData(session.globalExtraData(), event.id());
            event.onSaveBeforeBlocks(session, eventData);
            session.globalExtraData().put(event.id().toString(), eventData);
        }
    }

    public static void saveAfterBlocks(final BlueprintSaveSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            final CompoundTag eventData = eventData(session.globalExtraData(), event.id());
            event.onSaveAfterBlocks(session, eventData);
            session.globalExtraData().put(event.id().toString(), eventData);
        }
    }

    public static void saveAfterEntities(final BlueprintSaveSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            final CompoundTag eventData = eventData(session.globalExtraData(), event.id());
            event.onSaveAfterEntities(session, eventData);
            session.globalExtraData().put(event.id().toString(), eventData);
        }
    }

    public static void placeBeforeBlocks(final BlueprintPlaceSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            event.onPlaceBeforeBlocks(session, session.globalExtraData().getCompound(event.id().toString()));
        }
    }

    public static void placeAfterBlockEntities(final BlueprintPlaceSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            event.onPlaceAfterBlockEntities(session, session.globalExtraData().getCompound(event.id().toString()));
        }
    }

    public static void placeAfterBlocks(final BlueprintPlaceSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            event.onPlaceAfterBlocks(session, session.globalExtraData().getCompound(event.id().toString()));
        }
    }

    private static CompoundTag eventData(final CompoundTag globalExtraData, final ResourceLocation id) {
        return globalExtraData.getCompound(id.toString());
    }
}

package dev.rew1nd.sableschematicapi.api.blueprint;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Process-global registry and dispatcher for blueprint sidecar events.
 */
public final class SableBlueprintEventRegistry {
    private static final List<SableBlueprintEvent> EVENTS = new ObjectArrayList<>();

    private SableBlueprintEventRegistry() {
    }

    /**
     * Registers a global blueprint event.
     *
     * <p>Events are invoked in registration order. Register optional compatibility
     * events only after confirming the target mod is loaded.</p>
     *
     * @param event event implementation
     */
    public static void register(final SableBlueprintEvent event) {
        EVENTS.add(event);
    }

    /**
     * Returns registered events.
     *
     * @return immutable snapshot of registered events
     */
    public static List<SableBlueprintEvent> events() {
        return List.copyOf(EVENTS);
    }

    /**
     * Invokes {@link SableBlueprintEvent#onSaveBeforeBlocks(BlueprintSaveSession, CompoundTag)}.
     *
     * @param session save session
     */
    public static void saveBeforeBlocks(final BlueprintSaveSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            final CompoundTag eventData = eventData(session.globalExtraData(), event.id());
            event.onSaveBeforeBlocks(session, eventData);
            session.globalExtraData().put(event.id().toString(), eventData);
        }
    }

    /**
     * Invokes {@link SableBlueprintEvent#onSaveAfterBlocks(BlueprintSaveSession, CompoundTag)}.
     *
     * @param session save session
     */
    public static void saveAfterBlocks(final BlueprintSaveSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            final CompoundTag eventData = eventData(session.globalExtraData(), event.id());
            event.onSaveAfterBlocks(session, eventData);
            session.globalExtraData().put(event.id().toString(), eventData);
        }
    }

    /**
     * Invokes {@link SableBlueprintEvent#onSaveAfterEntities(BlueprintSaveSession, CompoundTag)}.
     *
     * @param session save session
     */
    public static void saveAfterEntities(final BlueprintSaveSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            final CompoundTag eventData = eventData(session.globalExtraData(), event.id());
            event.onSaveAfterEntities(session, eventData);
            session.globalExtraData().put(event.id().toString(), eventData);
        }
    }

    /**
     * Invokes {@link SableBlueprintEvent#onPlaceBeforeBlocks(BlueprintPlaceSession, CompoundTag)}.
     *
     * @param session placement session
     */
    public static void placeBeforeBlocks(final BlueprintPlaceSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            event.onPlaceBeforeBlocks(session, session.globalExtraData().getCompound(event.id().toString()));
        }
    }

    /**
     * Invokes {@link SableBlueprintEvent#onPlaceAfterBlockEntities(BlueprintPlaceSession, CompoundTag)}.
     *
     * @param session placement session
     */
    public static void placeAfterBlockEntities(final BlueprintPlaceSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            event.onPlaceAfterBlockEntities(session, session.globalExtraData().getCompound(event.id().toString()));
        }
    }

    /**
     * Invokes {@link SableBlueprintEvent#onPlaceAfterBlocks(BlueprintPlaceSession, CompoundTag)}.
     *
     * @param session placement session
     */
    public static void placeAfterBlocks(final BlueprintPlaceSession session) {
        for (final SableBlueprintEvent event : EVENTS) {
            event.onPlaceAfterBlocks(session, session.globalExtraData().getCompound(event.id().toString()));
        }
    }

    private static CompoundTag eventData(final CompoundTag globalExtraData, final ResourceLocation id) {
        return globalExtraData.getCompound(id.toString());
    }
}

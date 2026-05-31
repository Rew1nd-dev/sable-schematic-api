package dev.rew1nd.sableschematicapi.client.frontier;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class FrontierDataStore {
    private static final Map<ClientSubLevel, SableFrontierData> DATA =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FrontierDataStore() {
    }

    public static SableFrontierData getOrCreate(final ClientSubLevel subLevel) {
        return DATA.computeIfAbsent(subLevel, ignored -> new SableFrontierData());
    }

    public static SableFrontierData get(final ClientSubLevel subLevel) {
        return DATA.get(subLevel);
    }

    public static void clear(final ClientSubLevel subLevel) {
        DATA.remove(subLevel);
    }

    public static void clearAll() {
        DATA.clear();
    }
}

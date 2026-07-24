package dev.rew1nd.sableschematicapi.survival.projector;

import com.mojang.serialization.Codec;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelTicketInfo;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public final class ProjectorForceLoadTickets {
    public static final SubLevelLoadingTicketType<String> PROJECTOR =
            SubLevelLoadingTicketType.create(SableSchematicApi.id("projector"), Codec.STRING);

    private ProjectorForceLoadTickets() {
    }

    public static void register() {
        // Class initialization registers the ticket codec before persisted tickets are decoded.
    }

    public static boolean retain(final ServerSubLevelContainer container,
                                 final ServerSubLevel subLevel,
                                 final String key) {
        return container.addForceLoadTicket(subLevel, PROJECTOR, key);
    }

    public static void release(final ServerSubLevelContainer container,
                               final ServerSubLevel subLevel,
                               final String key) {
        container.removeForceLoadTicket(subLevel, PROJECTOR, key);
    }

    public static void cleanupOrphans(final Level level, final SubLevelContainer genericContainer) {
        if (!(level instanceof ServerLevel) || !(genericContainer instanceof ServerSubLevelContainer container)) {
            return;
        }
        for (final Map.Entry<UUID, SubLevelTicketInfo> entry :
                new ArrayList<>(container.getAllTickets().entrySet())) {
            final SubLevel subLevel = container.getSubLevel(entry.getKey());
            if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) {
                continue;
            }
            for (final SubLevelLoadingTicket<?> ticket :
                    new ArrayList<>(entry.getValue().tickets())) {
                if (ticket.getType() == PROJECTOR && ticket.getKey() instanceof final String key) {
                    release(container, serverSubLevel, key);
                }
            }
        }
    }
}

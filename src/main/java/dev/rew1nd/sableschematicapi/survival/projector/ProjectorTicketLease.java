package dev.rew1nd.sableschematicapi.survival.projector;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class ProjectorTicketLease {
    private final ServerLevel level;
    private final String key = UUID.randomUUID().toString();
    private final Set<UUID> retained = new LinkedHashSet<>();

    ProjectorTicketLease(final ServerLevel level) {
        this.level = level;
    }

    void retain(final UUID bodyId) throws ProjectorAdmissionException {
        if (!this.retained.add(bodyId)) {
            return;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(this.level);
        final SubLevel subLevel = container == null ? null : container.getSubLevel(bodyId);
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)
                || !ProjectorForceLoadTickets.retain(container, serverSubLevel, this.key)) {
            this.retained.remove(bodyId);
            throw new ProjectorAdmissionException("sable_schematic_api.projector.status.ticket_failed");
        }
    }

    void releaseAll() {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container != null) {
            for (final UUID id : this.retained) {
                final SubLevel subLevel = container.getSubLevel(id);
                if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                    ProjectorForceLoadTickets.release(container, serverSubLevel, this.key);
                }
            }
        }
        this.retained.clear();
    }
}

package dev.rew1nd.sableschematicapi.sublevel;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SubLevelGroupService {
    private SubLevelGroupService() {
    }

    public static List<SubLevelGroupRecord> listAll(final MinecraftServer server) {
        return group(server, SubLevelDirectoryService.listAll(server));
    }

    public static Optional<SubLevelGroupRecord> findGroup(final MinecraftServer server, final UUID memberId) {
        if (memberId == null) {
            return Optional.empty();
        }

        return listAll(server).stream()
                .filter(group -> group.contains(memberId))
                .findFirst();
    }

    private static List<SubLevelGroupRecord> group(final MinecraftServer server,
                                                   final Collection<SubLevelRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }

        final Map<UUID, SubLevelRecord> recordsById = new LinkedHashMap<>();
        for (final SubLevelRecord record : records) {
            recordsById.put(record.uuid(), record);
        }

        final DisjointSet groups = new DisjointSet(recordsById.keySet());
        addLoadedConnectionEdges(server, recordsById, groups);
        addStoredFallbackEdges(recordsById, groups);

        final Map<UUID, List<SubLevelRecord>> grouped = new LinkedHashMap<>();
        for (final SubLevelRecord record : recordsById.values()) {
            grouped.computeIfAbsent(groups.find(record.uuid()), ignored -> new ArrayList<>()).add(record);
        }

        final List<SubLevelGroupRecord> result = new ArrayList<>(grouped.size());
        for (final List<SubLevelRecord> members : grouped.values()) {
            members.sort(SubLevelGroupRecord.MEMBER_ORDER);
            result.add(new SubLevelGroupRecord(groupId(members), members));
        }

        result.sort(GROUP_ORDER);
        return List.copyOf(result);
    }

    private static void addLoadedConnectionEdges(final MinecraftServer server,
                                                 final Map<UUID, SubLevelRecord> recordsById,
                                                 final DisjointSet groups) {
        for (final ServerLevel level : server.getAllLevels()) {
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }

            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved() || !recordsById.containsKey(subLevel.getUniqueId())) {
                    continue;
                }

                for (final SubLevel dependency : SubLevelHelper.getConnectedChain(subLevel)) {
                    final SubLevelRecord dependencyRecord = recordsById.get(dependency.getUniqueId());
                    if (dependencyRecord == null || !dependencyRecord.dimension().equals(level.dimension())) {
                        continue;
                    }

                    groups.union(subLevel.getUniqueId(), dependency.getUniqueId());
                }
            }
        }
    }

    private static void addStoredFallbackEdges(final Map<UUID, SubLevelRecord> recordsById,
                                               final DisjointSet groups) {
        for (final SubLevelRecord record : recordsById.values()) {
            if (record.loaded()) {
                continue;
            }

            for (final UUID dependencyId : record.dependencies()) {
                final SubLevelRecord dependencyRecord = recordsById.get(dependencyId);
                if (dependencyRecord == null || !dependencyRecord.dimension().equals(record.dimension())) {
                    continue;
                }

                groups.union(record.uuid(), dependencyId);
            }
        }
    }

    private static UUID groupId(final List<SubLevelRecord> members) {
        return members.stream()
                .map(SubLevelRecord::uuid)
                .min(Comparator.comparing(UUID::toString))
                .orElseThrow();
    }

    private static final Comparator<SubLevelGroupRecord> GROUP_ORDER = Comparator
            .comparing((SubLevelGroupRecord group) -> group.members().getFirst().dimension().location().toString())
            .thenComparing(group -> group.representativeName().toLowerCase(Locale.ROOT))
            .thenComparing(group -> group.groupId().toString());

    private static final class DisjointSet {
        private final Map<UUID, UUID> parents = new LinkedHashMap<>();

        private DisjointSet(final Collection<UUID> ids) {
            for (final UUID id : ids) {
                this.parents.put(id, id);
            }
        }

        private UUID find(final UUID id) {
            final UUID parent = this.parents.get(Objects.requireNonNull(id, "id"));
            if (parent == null) {
                return id;
            }
            if (parent.equals(id)) {
                return id;
            }

            final UUID root = find(parent);
            this.parents.put(id, root);
            return root;
        }

        private void union(final UUID left, final UUID right) {
            final UUID leftRoot = find(left);
            final UUID rightRoot = find(right);
            if (leftRoot.equals(rightRoot)) {
                return;
            }

            if (leftRoot.toString().compareTo(rightRoot.toString()) <= 0) {
                this.parents.put(rightRoot, leftRoot);
            } else {
                this.parents.put(leftRoot, rightRoot);
            }
        }
    }
}

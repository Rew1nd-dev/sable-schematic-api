package dev.rew1nd.sableschematicapi.sublevel;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SubLevelGroupRecord(UUID groupId, List<SubLevelRecord> members) {
    public SubLevelGroupRecord {
        Objects.requireNonNull(groupId, "groupId");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Sub-level group must have at least one member.");
        }
    }

    public Optional<SubLevelRecord> find(final UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }

        return this.members.stream()
                .filter(record -> uuid.equals(record.uuid()))
                .findFirst();
    }

    public boolean contains(final UUID uuid) {
        return find(uuid).isPresent();
    }

    public List<UUID> memberIds() {
        return this.members.stream()
                .map(SubLevelRecord::uuid)
                .toList();
    }

    public String representativeName() {
        return this.members.stream()
                .map(SubLevelRecord::displayName)
                .min(String.CASE_INSENSITIVE_ORDER)
                .orElse(this.groupId.toString());
    }

    static final Comparator<SubLevelRecord> MEMBER_ORDER = Comparator
            .comparing((SubLevelRecord record) -> record.displayName().toLowerCase(Locale.ROOT))
            .thenComparing(record -> record.uuid().toString());
}

package dev.rew1nd.sableschematicapi.tool.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record BlueprintToolSubLevelGroup(UUID groupId, List<BlueprintToolSubLevelEntry> members) {
    public BlueprintToolSubLevelGroup {
        Objects.requireNonNull(groupId, "groupId");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Sub-level group must have at least one member.");
        }
    }

    public String representativeName() {
        return this.members.stream()
                .map(BlueprintToolSubLevelEntry::displayName)
                .min(String.CASE_INSENSITIVE_ORDER)
                .orElse(this.groupId.toString());
    }

    public String representativeId() {
        final String value = this.groupId.toString();
        return value.substring(0, Math.min(8, value.length()));
    }

    public double nearestDistance() {
        return this.members.stream()
                .mapToDouble(BlueprintToolSubLevelEntry::distance)
                .filter(distance -> distance >= 0.0)
                .min()
                .orElse(-1.0);
    }

    public String distanceLabel() {
        final double distance = nearestDistance();
        return distance < 0.0 ? "--" : String.format(Locale.ROOT, "%.2f", distance);
    }

    static final Comparator<BlueprintToolSubLevelEntry> MEMBER_ORDER = Comparator
            .comparing((BlueprintToolSubLevelEntry entry) -> entry.displayName().toLowerCase(Locale.ROOT))
            .thenComparing(entry -> entry.uuid().toString());
}

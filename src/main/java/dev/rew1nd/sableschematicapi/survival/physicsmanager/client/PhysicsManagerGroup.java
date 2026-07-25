package dev.rew1nd.sableschematicapi.survival.physicsmanager.client;

import dev.rew1nd.sableschematicapi.survival.physicsmanager.PhysicsManagerAlgorithms;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record PhysicsManagerGroup(UUID groupId,
                                  String signature,
                                  List<PhysicsManagerBodyEntry> members) {
    public PhysicsManagerGroup {
        members = members.stream()
                .sorted(Comparator
                        .comparing((PhysicsManagerBodyEntry entry) ->
                                entry.displayName().toLowerCase(Locale.ROOT))
                        .thenComparing(entry -> entry.uuid().toString()))
                .toList();
    }

    public String representativeName() {
        return this.members.stream()
                .map(PhysicsManagerBodyEntry::displayName)
                .min(String.CASE_INSENSITIVE_ORDER)
                .orElse(PhysicsManagerBodyEntry.shortId(this.groupId));
    }

    public String displayName() {
        if (this.members.size() == 1) {
            return this.members.getFirst().displayName();
        }
        return representativeName() + " · Cluster " + PhysicsManagerBodyEntry.shortId(this.groupId);
    }

    public int loadedCount() {
        return (int) this.members.stream().filter(PhysicsManagerBodyEntry::loaded).count();
    }

    public int storedCount() {
        return this.members.size() - loadedCount();
    }

    public double distance() {
        return this.members.stream()
                .mapToDouble(PhysicsManagerBodyEntry::distance)
                .filter(value -> value >= 0.0D)
                .min()
                .orElse(-1.0D);
    }

    public String distanceLabel() {
        final double value = distance();
        return value < 0.0D ? "—" : "%.1f m".formatted(value);
    }

    public String dimensionLabel() {
        final String first = this.members.getFirst().dimension();
        return this.members.stream().allMatch(entry -> first.equals(entry.dimension()))
                ? first
                : "Multiple dimensions";
    }

    public PhysicsManagerStaticState staticState() {
        return PhysicsManagerStaticState.of(this.members);
    }

    public PhysicsManagerBodyEntry largestBody() {
        return PhysicsManagerAlgorithms.largest(
                this.members,
                PhysicsManagerBodyEntry::volume,
                entry -> entry.uuid().toString()
        );
    }
}

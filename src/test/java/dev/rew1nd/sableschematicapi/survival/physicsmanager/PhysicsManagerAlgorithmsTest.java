package dev.rew1nd.sableschematicapi.survival.physicsmanager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class PhysicsManagerAlgorithmsTest {
    @Test
    void groupSignatureIgnoresMemberOrderButDetectsMembershipChanges() {
        final UUID group = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000003");

        final String forward = PhysicsManagerAlgorithms.groupSignature(
                "minecraft:overworld", group, List.of(first, second)
        );
        final String reverse = PhysicsManagerAlgorithms.groupSignature(
                "minecraft:overworld", group, List.of(second, first)
        );
        final String changed = PhysicsManagerAlgorithms.groupSignature(
                "minecraft:overworld", group, List.of(first)
        );

        assertEquals(forward, reverse);
        assertNotEquals(forward, changed);
    }

    @Test
    void distanceSortPlacesUnknownLastAndUsesStableId() {
        final List<Row> rows = new ArrayList<>(List.of(
                new Row("c", "Zulu", -1.0D, 1.0D, false),
                new Row("b", "Beta", 2.0D, 1.0D, false),
                new Row("a", "Alpha", 2.0D, 1.0D, false)
        ));
        rows.sort(PhysicsManagerAlgorithms.distanceComparator(Row::distance, Row::id));
        assertEquals(List.of("a", "b", "c"), rows.stream().map(Row::id).toList());
    }

    @Test
    void nameSortAndLargestBodyHaveDeterministicTies() {
        final List<Row> rows = new ArrayList<>(List.of(
                new Row("b", "same", 0.0D, 9.0D, false),
                new Row("a", "Same", 0.0D, 9.0D, true),
                new Row("c", "zulu", 0.0D, 2.0D, false)
        ));
        rows.sort(PhysicsManagerAlgorithms.nameComparator(Row::name, Row::id));
        assertEquals(List.of("a", "b", "c"), rows.stream().map(Row::id).toList());
        assertEquals("a", PhysicsManagerAlgorithms.largest(rows, Row::volume, Row::id).id());
    }

    @Test
    void staticAggregationCoversAllThreeStates() {
        assertEquals(
                PhysicsManagerAlgorithms.AggregateStatic.STATIC,
                PhysicsManagerAlgorithms.aggregateStatic(List.of(true, true), value -> value)
        );
        assertEquals(
                PhysicsManagerAlgorithms.AggregateStatic.DYNAMIC,
                PhysicsManagerAlgorithms.aggregateStatic(List.of(false, false), value -> value)
        );
        assertEquals(
                PhysicsManagerAlgorithms.AggregateStatic.MIXED,
                PhysicsManagerAlgorithms.aggregateStatic(List.of(true, false), value -> value)
        );
    }

    @Test
    void maxCornerIsExactAndVolumeUsesWorldAabbExtents() {
        assertEquals(
                new PhysicsManagerAlgorithms.Point(7.25D, 9.5D, -1.0D),
                PhysicsManagerAlgorithms.maxCorner(7.25D, 9.5D, -1.0D)
        );
        assertEquals(
                24.0D,
                PhysicsManagerAlgorithms.volume(1.0D, 2.0D, 3.0D, 3.0D, 5.0D, 7.0D)
        );
    }

    private record Row(String id, String name, double distance, double volume, boolean staticLocked) {
    }
}

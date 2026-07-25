package dev.rew1nd.sableschematicapi.survival.physicsmanager.client;

import dev.rew1nd.sableschematicapi.survival.physicsmanager.PhysicsManagerAlgorithms;

import java.util.Collection;

public enum PhysicsManagerStaticState {
    STATIC,
    DYNAMIC,
    MIXED;

    public static PhysicsManagerStaticState of(final Collection<PhysicsManagerBodyEntry> entries) {
        return valueOf(PhysicsManagerAlgorithms
                .aggregateStatic(entries, PhysicsManagerBodyEntry::staticLocked)
                .name());
    }

    public boolean nextTarget() {
        return this != STATIC;
    }
}

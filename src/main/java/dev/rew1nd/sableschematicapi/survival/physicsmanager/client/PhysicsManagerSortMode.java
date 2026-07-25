package dev.rew1nd.sableschematicapi.survival.physicsmanager.client;

public enum PhysicsManagerSortMode {
    DISTANCE("gui.sable_schematic_api.physics_manager.sort.distance"),
    NAME("gui.sable_schematic_api.physics_manager.sort.name");

    private final String translationKey;

    PhysicsManagerSortMode(final String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }
}

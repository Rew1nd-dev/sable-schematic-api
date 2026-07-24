package dev.rew1nd.sableschematicapi.survival.projector;

public enum ProjectorPhase {
    IDLE,
    VALIDATING,
    SCANNING_TERRAIN,
    PLACING,
    REVEALING,
    FAILED,
    COMPLETED;

    public boolean busy() {
        return this == VALIDATING || this == SCANNING_TERRAIN || this == PLACING || this == REVEALING;
    }

    public boolean admission() {
        return this == VALIDATING || this == SCANNING_TERRAIN || this == PLACING;
    }
}

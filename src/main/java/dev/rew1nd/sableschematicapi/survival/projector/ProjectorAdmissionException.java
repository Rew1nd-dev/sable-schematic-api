package dev.rew1nd.sableschematicapi.survival.projector;

public final class ProjectorAdmissionException extends Exception {
    private final String translationKey;

    public ProjectorAdmissionException(final String translationKey) {
        super(translationKey);
        this.translationKey = translationKey;
    }

    public ProjectorAdmissionException(final String translationKey, final Throwable cause) {
        super(translationKey, cause);
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }
}

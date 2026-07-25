package dev.rew1nd.sableschematicapi.api.honeywelder;

/** Server decision for a consumer-defined honey-welder action. */
public record HoneyWelderStartResult(boolean accepted, String message) {
    public HoneyWelderStartResult {
        message = message == null ? "" : message;
    }

    public static HoneyWelderStartResult accept() {
        return new HoneyWelderStartResult(true, "");
    }

    public static HoneyWelderStartResult reject(final String message) {
        return new HoneyWelderStartResult(false, message);
    }
}

package dev.hazel.livealarm;

/** Shared by user actions and the actual alarm trigger; never changes system permissions. */
public final class AlertPolicy {
    public enum Mode { NORMAL, SOUND_ONLY, BLOCKED }
    private AlertPolicy() {}
    public static Mode mode(boolean runtimeGranted, boolean notificationsEnabled,
                            int channelImportance, boolean soundConsent) {
        if (runtimeGranted && notificationsEnabled && channelImportance > 0) return Mode.NORMAL;
        return soundConsent ? Mode.SOUND_ONLY : Mode.BLOCKED;
    }
}

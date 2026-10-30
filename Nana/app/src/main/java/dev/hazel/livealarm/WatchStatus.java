package dev.hazel.livealarm;

/** Presentation of independently measured service, authorization and posted-notification state. */
public final class WatchStatus {
    private WatchStatus() {}
    /** `schedulePaused` comes first because it explains a service that is deliberately not
     *  running: outside the chosen schedule the watch is parked, which is not an interruption and
     *  must never be reported as one. */
    public static String resolve(boolean enabled,boolean running,boolean runtimeGranted,boolean appEnabled,
                                 int channelImportance,boolean groupBlocked,boolean queried,boolean registered,
                                 boolean schedulePaused){
        if(schedulePaused)return "paused";
        if(!running)return enabled?"interrupted":"stopped";
        if(!runtimeGranted||!appEnabled)return "permission_blocked";
        if(channelImportance==0||groupBlocked)return "channel_blocked";
        if(!queried)return "unknown";
        // Being registered with NotificationManager is not proof that an OEM draws it on screen.
        return registered?"registered":"not_posted";
    }
}

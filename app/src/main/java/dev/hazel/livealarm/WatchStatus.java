package dev.hazel.livealarm;

/** Presentation of independently measured service, authorization and posted-notification state. */
public final class WatchStatus {
    private WatchStatus() {}
    public static String resolve(boolean enabled,boolean running,boolean runtimeGranted,boolean appEnabled,
                                 int channelImportance,boolean groupBlocked,boolean queried,boolean registered){
        if(!running)return enabled?"interrupted":"stopped";
        if(!runtimeGranted||!appEnabled)return "permission_blocked";
        if(channelImportance==0||groupBlocked)return "channel_blocked";
        if(!queried)return "unknown";
        // Being registered with NotificationManager is not proof that an OEM draws it on screen.
        return registered?"registered":"not_posted";
    }
}

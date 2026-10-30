import dev.hazel.livealarm.WatchStatus;

public final class WatchStatusTests {
    private static int count;
    private static void check(String expected,String actual){count++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        // Service state must stay distinct from authorization, including an old registered notice.
        check("stopped",WatchStatus.resolve(false,false,false,false,0,false,true,true,false));
        check("interrupted",WatchStatus.resolve(true,false,true,true,2,false,true,true,false));
        check("permission_blocked",WatchStatus.resolve(true,true,false,false,2,false,true,true,false));
        check("permission_blocked",WatchStatus.resolve(true,true,true,false,2,false,true,true,false));
        check("permission_blocked",WatchStatus.resolve(true,true,false,true,2,false,true,true,false));
        check("channel_blocked",WatchStatus.resolve(true,true,true,true,0,false,true,true,false));
        check("channel_blocked",WatchStatus.resolve(true,true,true,true,2,true,true,true,false));
        check("unknown",WatchStatus.resolve(true,true,true,true,2,false,false,false,false));
        check("not_posted",WatchStatus.resolve(true,true,true,true,2,false,true,false,false));
        check("registered",WatchStatus.resolve(true,true,true,true,2,false,true,true,false));
        // Test-only foreground service, then stop; notification presence is never "always visible".
        check("registered",WatchStatus.resolve(false,true,true,true,2,false,true,true,false));
        check("permission_blocked",WatchStatus.resolve(false,true,false,false,2,false,true,false,false));
        check("stopped",WatchStatus.resolve(false,false,true,true,2,false,true,false,false));
        // Outside the schedule a stopped service is the intended state, so "paused" outranks every
        // other reading — including the notification ones, which describe a notice that is meant to
        // be gone. Reporting this as "interrupted" is the false alarm the state exists to prevent.
        check("paused",WatchStatus.resolve(true,false,true,true,2,false,true,false,true));
        check("paused",WatchStatus.resolve(true,false,false,false,0,false,false,false,true));
        check("paused",WatchStatus.resolve(true,true,true,true,2,false,true,true,true));
        System.out.println("PASS: "+count+" watch-status assertions");
    }
}

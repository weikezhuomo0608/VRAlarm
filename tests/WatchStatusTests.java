import dev.hazel.livealarm.WatchStatus;

public final class WatchStatusTests {
    private static int count;
    private static void check(String expected,String actual){count++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        // Service state must stay distinct from authorization, including an old registered notice.
        check("stopped",WatchStatus.resolve(false,false,false,false,0,false,true,true));
        check("interrupted",WatchStatus.resolve(true,false,true,true,2,false,true,true));
        check("permission_blocked",WatchStatus.resolve(true,true,false,false,2,false,true,true));
        check("permission_blocked",WatchStatus.resolve(true,true,true,false,2,false,true,true));
        check("permission_blocked",WatchStatus.resolve(true,true,false,true,2,false,true,true));
        check("channel_blocked",WatchStatus.resolve(true,true,true,true,0,false,true,true));
        check("channel_blocked",WatchStatus.resolve(true,true,true,true,2,true,true,true));
        check("unknown",WatchStatus.resolve(true,true,true,true,2,false,false,false));
        check("not_posted",WatchStatus.resolve(true,true,true,true,2,false,true,false));
        check("registered",WatchStatus.resolve(true,true,true,true,2,false,true,true));
        // Test-only foreground service, then stop; notification presence is never "always visible".
        check("registered",WatchStatus.resolve(false,true,true,true,2,false,true,true));
        check("permission_blocked",WatchStatus.resolve(false,true,false,false,2,false,true,false));
        check("stopped",WatchStatus.resolve(false,false,true,true,2,false,true,false));
        System.out.println("PASS: "+count+" watch-status assertions");
    }
}

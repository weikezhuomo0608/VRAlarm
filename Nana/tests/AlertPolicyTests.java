import dev.hazel.livealarm.AlertPolicy;

/** Permission lifecycle and channel regressions, independent of Android stubs. */
public final class AlertPolicyTests {
    private static int count;
    private static void mode(AlertPolicy.Mode expected,boolean runtime,boolean app,int importance,boolean consent,String label){
        count++;if(AlertPolicy.mode(runtime,app,importance,consent)!=expected)throw new AssertionError(label);
    }
    public static void main(String[] args){
        mode(AlertPolicy.Mode.BLOCKED,false,false,4,false,"a configured channel cannot substitute for notification permission");
        mode(AlertPolicy.Mode.SOUND_ONLY,false,false,4,true,"the Xiaomi denied state supports explicit sound consent");
        mode(AlertPolicy.Mode.NORMAL,true,true,4,true,"restoring permission resumes ordinary alarm notifications");
        mode(AlertPolicy.Mode.SOUND_ONLY,false,false,4,true,"later revocation preserves only the explicitly consented sound behavior");
        mode(AlertPolicy.Mode.BLOCKED,false,false,4,false,"opting out restores the block");
        mode(AlertPolicy.Mode.BLOCKED,true,false,4,false,"runtime-only OEM mismatch fails closed");
        mode(AlertPolicy.Mode.BLOCKED,false,true,4,false,"app-switch-only OEM mismatch fails closed");
        mode(AlertPolicy.Mode.SOUND_ONLY,false,true,4,true,"mismatch permits sound only with consent");
        mode(AlertPolicy.Mode.BLOCKED,true,true,0,false,"blocked alarm channel is respected");
        mode(AlertPolicy.Mode.SOUND_ONLY,true,true,0,true,"consent also covers a blocked alarm channel");
        mode(AlertPolicy.Mode.BLOCKED,true,true,-1,false,"missing alarm channel fails closed");
        mode(AlertPolicy.Mode.NORMAL,true,true,2,false,"a low-importance enabled channel must not discard app-played alarms");
        System.out.println("PASS: "+count+" notification and sound-consent policy assertions");
    }
}

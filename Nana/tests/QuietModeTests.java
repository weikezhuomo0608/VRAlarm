import dev.hazel.livealarm.QuietMode;

/**
 * The silent mode that keeps the ringing chain quiet without losing the reminder itself. The rule is
 * deliberately a deadline rather than a flag: a mode left on by mistake has to end on its own.
 */
public final class QuietModeTests {
    private static int count;
    private static void check(boolean condition,String label){count++;if(!condition)throw new AssertionError(label);}
    public static void main(String[] args){
        long now=1700000000000L;
        // Off means off, and a deadline that has passed is off too — the expired case is what
        // keeps a forgotten countdown from muting the next stream.
        check(!QuietMode.active(QuietMode.OFF,now),"an unset deadline is not silent");
        check(QuietMode.active(now+1,now),"a future deadline is silent");
        check(!QuietMode.active(now,now),"a deadline exactly at now has already passed");
        check(!QuietMode.active(now-1,now),"a past deadline is not silent");

        // The offered countdowns: 30 minutes lasts 30 minutes, not 29 and not 31.
        long half=QuietMode.until(now,30);
        check(half==now+30*60000L,"30 minutes is 30 minutes");
        check(QuietMode.active(half,now+30*60000L-1),"still silent one millisecond before the deadline");
        check(!QuietMode.active(half,now+30*60000L),"silent is over at the deadline");
        check(QuietMode.left(half,now)==30*60000L,"the countdown is reported as stored");
        check(QuietMode.left(half,now+31*60000L)==0,"an expired deadline has nothing left");
        check(QuietMode.until(now,60)==now+60*60000L,"one hour is one hour");
        check(QuietMode.until(now,120)==now+120*60000L,"two hours are two hours");

        // Switching off, and the "until I say so" choice, are two different things.
        check(QuietMode.until(now,0)==QuietMode.OFF,"a zero duration switches silence off");
        long manual=QuietMode.until(now,-1);
        check(manual==QuietMode.FOREVER,"a negative duration means until I say otherwise");
        check(QuietMode.active(manual,now+10L*365*24*3600*1000),"manual silence survives ten years");
        check(QuietMode.forever(manual,now),"manual silence is reported as having no countdown");
        check(!QuietMode.forever(half,now),"a countdown is not manual silence");
        check(!QuietMode.forever(QuietMode.OFF,now),"switched-off silence is not manual silence");

        // A deadline restored after a reboot keeps counting down from where it was.
        check(QuietMode.active(half,now+10*60000L),"a restored deadline stays silent");
        check(QuietMode.active(half,now+29*60000L),"a restored deadline is not reset by a reload");

        // Only the durations the interface offers may be stored; a stray number is refused before
        // it reaches the store.
        check(QuietMode.offered(0),"switching off is offered");
        check(QuietMode.offered(-1),"manual silence is offered");
        check(QuietMode.offered(30)&&QuietMode.offered(60)&&QuietMode.offered(120),"the three countdowns are offered");
        check(!QuietMode.offered(45),"an unlisted duration is refused");
        check(!QuietMode.offered(1440),"a whole day is not an offered duration");
        System.out.println("PASS: "+count+" quiet-mode assertions");
    }
}

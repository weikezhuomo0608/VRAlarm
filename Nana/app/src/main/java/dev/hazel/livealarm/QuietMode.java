package dev.hazel.livealarm;

/**
 * The mode that keeps an alarm usable in a meeting: the full-screen page, the notification and the
 * snooze are untouched, and only the ring and the vibration are held back.
 *
 * Stored as a deadline rather than a flag, so a mode switched on for a meeting ends by itself
 * instead of quietly swallowing the next stream. No Android types here, so the rule is testable on
 * its own.
 */
public final class QuietMode {
    public static final long OFF=0;
    /** "Until I turn it off" is a deadline far past any plausible clock. */
    public static final long FOREVER=Long.MAX_VALUE;
    /** The countdowns the interface offers, in minutes. */
    public static final int[] DURATIONS={30,60,120};
    /** Anything further away than this is read as "until I turn it off", not as a countdown. */
    public static final long FOREVER_THRESHOLD_MILLIS=5L*365*24*3600*1000;

    private QuietMode(){}

    /** Silent right now. A deadline exactly at `now` has already passed. */
    public static boolean active(long until,long now){return until!=OFF&&now<until;}

    /** How much of the countdown is left; zero when silence is off or already over. */
    public static long left(long until,long now){return active(until,now)?until-now:0;}

    /** Silence with no countdown, i.e. the user must switch it off themselves. */
    public static boolean forever(long until,long now){return left(until,now)>FOREVER_THRESHOLD_MILLIS;}

    /**
     * The deadline produced by a choice made now: 0 switches silence off, a negative duration lasts
     * until the user says otherwise, and anything else is a countdown in minutes.
     */
    public static long until(long now,int minutes){
        if(minutes==0)return OFF;
        if(minutes<0)return FOREVER;
        return now+minutes*60000L;
    }

    /** Whether the interface is allowed to ask for this duration; everything else is refused. */
    public static boolean offered(int minutes){
        if(minutes<=0)return true;
        for(int duration:DURATIONS)if(duration==minutes)return true;
        return false;
    }
}

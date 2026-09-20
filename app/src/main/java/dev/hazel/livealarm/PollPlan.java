package dev.hazel.livealarm;

/**
 * Pure timing rules for the poll cycle. Pure Java: no Android and no org.json,
 * so the arithmetic that used to hide inside GuardianService is testable.
 *
 * Multi-anchor polling made two of these numbers wrong: one cycle now performs
 * several requests, and any anchor can be the one that gets rate limited.
 */
public final class PollPlan {
    /** A single fetch may spend its 8 s connect plus 8 s read timeout before giving up. */
    public static final int FETCH_BUDGET_MS=17000;
    public static final int CYCLE_MARGIN_MS=8000;
    public static final int IDLE_GAP_SECONDS=180;
    public static final int MIN_GAP_SECONDS=5;
    public static final int MIN_BACKOFF_SECONDS=15, RATE_LIMIT_BACKOFF_SECONDS=120, MAX_BACKOFF_SECONDS=300;

    private PollPlan() {}

    /** The wake lock has to outlive every request in the cycle including one transient
     *  retry per anchor (server-side connection resets happen without any permission
     *  problem), so the budget is two fetches per anchor plus the cycle margin. */
    public static long wakeLockMillis(int anchors) {
        int count=anchors<1?1:anchors;
        return (long)count*FETCH_BUDGET_MS*2+CYCLE_MARGIN_MS;
    }

    /** Unchanged single-anchor formula: exponential backoff, capped at five minutes. */
    public static int backoffSeconds(int pollSeconds, boolean rateLimited, int failures) {
        int exponent=(failures<1?1:failures)-1;
        if (exponent>4) exponent=4;
        int floor=rateLimited?RATE_LIMIT_BACKOFF_SECONDS:MIN_BACKOFF_SECONDS;
        int base=pollSeconds>floor?pollSeconds:floor;
        long seconds=(long)base<<exponent;
        return (int)(seconds>MAX_BACKOFF_SECONDS?MAX_BACKOFF_SECONDS:seconds);
    }

    /** What the watch should be doing right now; see duty(). */
    public enum Duty { OFF, PAUSED, WATCHING }

    /**
     * The reminder window used to decide only whether a live stream would ring, while the loop
     * kept polling slowly around the clock. It now decides whether the watch runs at all: outside
     * the window nothing may leave the device, so the service parks itself and the next window
     * boundary is the only thing that brings it back.
     *
     * PAUSED is also what an unusable custom schedule produces — custom mode with every rule
     * switched off, or with no rule at all. Nothing is watching there either, and calling that
     * "paused" (rather than quietly looking healthy) is what lets the UI say so out loud.
     */
    public static Duty duty(boolean enabled, boolean allDay, boolean hasEnabledWindow, boolean insideWindow) {
        if (!enabled) return Duty.OFF;
        if (allDay) return Duty.WATCHING;
        if (!hasEnabledWindow) return Duty.PAUSED;
        return insideWindow ? Duty.WATCHING : Duty.PAUSED;
    }

    /**
     * pollSeconds describes one anchor, so time already spent polling is discounted:
     * otherwise the interval per anchor would grow with the size of the list.
     *
     * The `allowed == false` answer is now only a defensive one: outside the reminder window the
     * watch is paused outright (see duty()), so no cycle is ever planned there. It is kept because
     * a cycle that started inside the window can still finish after the window closed.
     */
    public static int gapSeconds(int pollSeconds, boolean allowed, int cycleMillis) {
        if (!allowed) return IDLE_GAP_SECONDS;
        int spent=cycleMillis<=0?0:(cycleMillis+999)/1000;
        int gap=pollSeconds-spent;
        return gap<MIN_GAP_SECONDS?MIN_GAP_SECONDS:gap;
    }

    /**
     * The pace outside the user's high-frequency windows, while that option is switched on. Polling
     * around the clock at a short interval is what empties the battery overnight, and a stream that
     * is noticed two minutes late still rings. This slows the interval down only: whether the watch
     * runs at all is duty()'s question, and these windows never widen it.
     */
    public static final int LOW_FREQUENCY_SECONDS=120;

    /**
     * The interval a cycle should plan for. Inside a high-frequency window the configured pace is
     * kept; outside them the pace is raised to the slow floor, so a setting faster than the floor
     * cannot make the slow hours expensive. With the option off this is exactly the configured
     * value — no existing install changes pace.
     */
    public static int effectivePollSeconds(int pollSeconds, boolean highFrequency, boolean insideHighWindow) {
        if (!highFrequency || insideHighWindow) return pollSeconds;
        return pollSeconds>LOW_FREQUENCY_SECONDS?pollSeconds:LOW_FREQUENCY_SECONDS;
    }

    /**
     * A scheduled start this near used to lift the idle gap, because outside the reminder window
     * the loop still crawled along at three minutes and a scheduled stream should not be noticed
     * late. Outside the window nothing is polled at all now (see duty()), so nothing is left to
     * lift, and this is kept as the reference definition of 「临近开播」 — the weekly page applies
     * the same thirty minutes in `ui-src/app.js` to decide which row reads 「正在直播」.
     */
    public static final int SOON_BEFORE_MINUTES=30, SOON_AFTER_MINUTES=30;
    /**
     * How early the weekly-schedule heads-up is armed. nextStart() subtracts it to produce the
     * alarm moment, and callers add it back to recover the start they display. Keeping it in one
     * place is what stops those two conversions from drifting apart — when they did, the heads-up
     * announced a start five minutes later than the weekly schedule actually said.
     */
    public static final long PRESTREAM_LEAD_MILLIS=5*60000L;
    /** weekdayZeroBased: 0=Monday; startOfDayMillis is today's 00:00 in the reminder zone. */
    public static boolean dueSoon(java.util.List<Schedule.Entry> entries,int weekdayZeroBased,long startOfDayMillis,long nowMillis){
        if(entries==null)return false;
        for(Schedule.Entry e:entries){
            if(!Schedule.on(e,weekdayZeroBased))continue;
            long start=startOfDayMillis+e.start*60000L;
            if(nowMillis>=start-SOON_BEFORE_MINUTES*60000L&&nowMillis<=start+SOON_AFTER_MINUTES*60000L)return true;
        }
        return false;
    }
    /** Earliest scheduled start minus lead, scanning the whole week ahead; 0 when none upcoming.
     *  Scanning all seven days matters: from Saturday, next Friday's slot is six days out, and
     *  a two-day window would leave its pre-stream alarm unarmed until the cycle happened to
     *  cross into the window. */
    public static long nextStart(java.util.List<Schedule.Entry> entries,int weekdayToday,long startOfTodayMillis,long nowMillis,long leadMillis){
        long best=0;
        for(int off=0;off<7;off++){
            int wd=(weekdayToday+off)%7;
            long dayStart=startOfTodayMillis+off*86400000L;
            for(Schedule.Entry e:entries){
                if(!Schedule.on(e,wd))continue;
                long at=dayStart+e.start*60000L-leadMillis;
                if(at>nowMillis&&(best==0||at<best))best=at;
            }
        }
        return best;
    }
    /** Floor for the safety net, so a short poll interval cannot turn it into a hot loop. */
    public static final int MIN_KEEPALIVE_SECONDS=90;

    /** Lower bound of the continuous-mode safety net: short intervals must not become a hot loop. */
    public static final int MIN_TURBO_NET_SECONDS=20;

    /**
     * When the safety net may fire in continuous mode. It sits just past the loop's own plan
     * (half an interval of slack), so a healthy loop always replaces it first — the net only
     * fires when the loop really stalled, such as while the phone is asleep. Three intervals
     * (the saver value) is useless there: Doze pushes allow-while-idle alarms to its own floor
     * of about nine minutes, which is exactly the gap users see.
     */
    public static int turboNetSeconds(int pollSeconds) {
        int gap=gapSeconds(pollSeconds,true,0);
        int slack=Math.max(15,gap/2);
        int seconds=gap+slack;
        return seconds<MIN_TURBO_NET_SECONDS?MIN_TURBO_NET_SECONDS:seconds;
    }
    public static int keepAliveSeconds(int pollSeconds, boolean allowed, boolean continuous) {
        return continuous?turboNetSeconds(pollSeconds):keepAliveSeconds(pollSeconds,allowed);
    }

    /**
     * How late the safety net may fire. It is armed at three times the interval the check loop
     * just scheduled for itself, which is long enough that a healthy loop always replaces it
     * first, and short enough that a stalled loop is noticed within a couple of minutes rather
     * than at the next fifteen-minute watchdog.
     */
    public static int keepAliveSeconds(int pollSeconds, boolean allowed) {
        int gap=gapSeconds(pollSeconds,allowed,0);
        long seconds=(long)gap*3;
        return (int)(seconds<MIN_KEEPALIVE_SECONDS?MIN_KEEPALIVE_SECONDS:seconds);
    }

    /**
     * While the device is dozing, the platform will not hand any app its allow-while-idle alarms
     * more often than about once every nine minutes — and once every fifteen when exact alarms
     * are not granted, which is the default for an app targeting a recent API level. A report
     * floor below that floor would describe a perfectly healthy watch as interrupted on every
     * single cycle of a dozing night, which is exactly when the reminder window is open.
     */
    public static final long ALLOW_WHILE_IDLE_FLOOR_MS=9*60000L;
    public static final long INTERRUPTION_FLOOR_MS=ALLOW_WHILE_IDLE_FLOOR_MS+60000L;

    /** What a late cycle actually proves. See classifyLate. */
    public enum Late { NONE, RESTARTED, DELAYED }

    /**
     * A cycle that started late is not by itself evidence that the watch stopped: the same
     * doze that delays the wake-up also delays every other explanation. Only these readings
     * are worth telling the user about:
     *
     *   RESTARTED — the service was created after the moment it had planned to run, so the
     *               process really was gone in between. This is the one honest "interrupted".
     *   DELAYED   — the service has been alive the whole time but the cycle is late while the
     *               phone is awake and in use, so something other than sleep held it up.
     *
     * A plan made outside the reminder window is silently dropped: the idle interval there is
     * deliberate, the phone is expected to sleep, and the boundary crossing would otherwise
     * turn a normal three-minute idle gap into a huge, meaningless lateness.
     */
    public static Late classifyLate(long lateMillis, boolean planWasInsideWindow, boolean insideWindowNow,
                                    boolean serviceRestartedAfterPlan, boolean deviceAwake) {
        if (!planWasInsideWindow || !insideWindowNow) return Late.NONE;
        if (lateMillis < INTERRUPTION_FLOOR_MS) return Late.NONE;
        if (serviceRestartedAfterPlan) return Late.RESTARTED;
        return deviceAwake ? Late.DELAYED : Late.NONE;
    }
}

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
        return duty(enabled,allDay,hasEnabledWindow,insideWindow,false);
    }
    /**
     * The same question with the per-anchor 考子闹钟: one anchor may ask to be watched around the
     * clock regardless of the windows. The loop has to be running for that anchor to be watched at
     * all, so this opens the schedule for the whole watch — which streams may still ring is asked
     * per anchor (see LiveGate's Schedule), and only the anchor that asked gets the wider answer.
     */
    public static Duty duty(boolean enabled, boolean allDay, boolean hasEnabledWindow, boolean insideWindow,
                            boolean anchorAllDay) {
        if (!enabled) return Duty.OFF;
        if (allDay || anchorAllDay) return Duty.WATCHING;
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
    /** The slow paces the interface offers, in seconds: how much battery the quiet hours may cost.
     *  The fastest of them is thirty seconds — a stream nobody announced is the whole point of this
     *  alarm, and the batched cycle (one request for every anchor) made the floor cheap to lower. */
    public static final int[] LOW_FREQUENCY_CHOICES={30,60,120,300};

    /**
     * The interval a cycle should plan for. Inside a high-frequency window the configured pace is
     * kept; outside them the pace is raised to the slow floor, so a setting faster than the floor
     * cannot make the slow hours expensive. With the option off this is exactly the configured
     * value — no existing install changes pace.
     *
     * The floor is a parameter because the user chooses it (two, three or five minutes); an
     * unusable value falls back to the default rather than producing a hot loop.
     */
    /** The same question with the default slow pace, for callers that have no configured one. */
    public static int effectivePollSeconds(int pollSeconds, boolean highFrequency, boolean insideHighWindow) {
        return effectivePollSeconds(pollSeconds,highFrequency,insideHighWindow,LOW_FREQUENCY_SECONDS);
    }
    public static int effectivePollSeconds(int pollSeconds, boolean highFrequency, boolean insideHighWindow,int lowSeconds) {
        return effectivePollSeconds(pollSeconds,highFrequency,insideHighWindow,lowSeconds,false);
    }
    /**
     * `outsideWindows` is true while the loop runs only because a 考子 anchor asked for it — the hours
     * outside the reminder windows. Nobody is expecting a reminder then, so the slow floor applies
     * without the user having to switch the pacing option on: a 考子 anchor still catches a surprise
     * stream, just not at the price of a full-pace night. A high-frequency window, or the phone being
     * in use, still counts as "the user asked for speed" and keeps the configured pace.
     */
    public static int effectivePollSeconds(int pollSeconds, boolean highFrequency, boolean insideHighWindow,int lowSeconds,boolean outsideWindows) {
        if (insideHighWindow) return pollSeconds;
        if (!highFrequency && !outsideWindows) return pollSeconds;
        int floor=lowSeconds>0?lowSeconds:LOW_FREQUENCY_SECONDS;
        return pollSeconds>floor?pollSeconds:floor;
    }
    /** Slack added to the pace when the safety net *is* the loop, so a healthy loop still replaces it. */
    public static final int NET_MARGIN_SECONDS=30;

    /**
     * How long until the safety-net alarm, for a loop planning `pollSeconds`.
     *
     * Inside the reminder windows the loop runs on its own, and in continuous mode the lock keeps the
     * phone awake for it, so the alarm only has to catch a loop that stalled — half an interval of
     * slack behind the plan (turboNetSeconds). Outside the windows the loop exists only because of a
     * 考子 anchor and the phone is allowed to sleep between checks: there the alarm is not a net
     * behind the loop, it *is* the loop, so it sits at the pace itself and the device sleeps in
     * between. The saver (non-continuous) value stays three intervals either way — it is how that
     * mode is defined.
     */
    public static int netSeconds(int pollSeconds,boolean continuous,boolean outsideWindows){
        if(!continuous) return keepAliveSeconds(pollSeconds,true);
        if(outsideWindows) return pollSeconds+NET_MARGIN_SECONDS;
        return turboNetSeconds(pollSeconds);
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
     * The default notice lead, used by entries that do not carry one of their own. It is no longer
     * applied anywhere else: nextOccurrence() returns the start and the arm moment together, and
     * both callers store what they were given. When the lead was subtracted in one place and added
     * back in another, the heads-up announced a start five minutes later than the schedule said.
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
    /**
     * The next scheduled moment and the moment to be told about it, as {startAt, armAt}; null when
     * nothing is coming up.
     *
     * Weekly rules are scanned over the coming seven days — from Saturday, next Friday's slot is six
     * days out, and a two-day window would leave its heads-up unarmed until a cycle happened to
     * cross into it. A one-off entry has exactly one moment, which may be further out than a week
     * (that is the point of a calendar), and arming that far ahead is harmless: every cycle re-arms
     * the alarm and the receiver ignores a fire that no longer matches the stored moment.
     *
     * Each entry may carry its own notice lead; 0 means the default. `armAt` is returned rather than
     * computed by the callers: the lead used to be subtracted in one place and added back in
     * another, and those two conversions drift apart the moment the lead stops being a constant.
     */
    public static long[] nextOccurrence(java.util.List<Schedule.Entry> entries,java.time.LocalDate today,
                                        long startOfTodayMillis,java.time.ZoneId zone,long nowMillis,long defaultLeadMillis){
        long bestStart=0,bestArm=0;
        if(entries==null||today==null)return null;
        for(Schedule.Entry e:entries){
            long start=0;
            java.time.LocalDate own=Schedule.dateOf(e);
            if(own!=null){
                if(own.isBefore(today))continue;
                start=own.atStartOfDay(zone).toInstant().toEpochMilli()+e.start*60000L;
            }else{
                for(int off=0;off<7;off++){
                    java.time.LocalDate day=today.plusDays(off);
                    if((e.days&(1<<(day.getDayOfWeek().getValue()-1)))==0)continue;
                    long candidate=startOfTodayMillis+off*86400000L+e.start*60000L;
                    if(candidate>nowMillis){start=candidate;break;}
                }
            }
            if(start<=nowMillis)continue;
            long lead=e.lead>0?e.lead*60000L:defaultLeadMillis;
            long arm=start-lead;
            // A start already inside its own notice window is not announced any more; the old
            // seven-day scan skipped it the same way.
            if(arm<=nowMillis)continue;
            if(bestArm==0||arm<bestArm){bestArm=arm;bestStart=start;}
        }
        return bestArm==0?null:new long[]{bestStart,bestArm};
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

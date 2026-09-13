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

    /** The wake lock has to outlive every request in the cycle, not just the first one. */
    public static long wakeLockMillis(int anchors) {
        int count=anchors<1?1:anchors;
        return (long)count*FETCH_BUDGET_MS+CYCLE_MARGIN_MS;
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

    /**
     * pollSeconds describes one anchor, so time already spent polling is discounted:
     * otherwise the interval per anchor would grow with the size of the list.
     */
    public static int gapSeconds(int pollSeconds, boolean allowed, int cycleMillis) {
        if (!allowed) return IDLE_GAP_SECONDS;
        int spent=cycleMillis<=0?0:(cycleMillis+999)/1000;
        int gap=pollSeconds-spent;
        return gap<MIN_GAP_SECONDS?MIN_GAP_SECONDS:gap;
    }

    /** A scheduled start this near lifts the idle gap, and the lift lingers briefly after it. */
    public static final int SOON_BEFORE_MINUTES=30, SOON_AFTER_MINUTES=30;
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
}

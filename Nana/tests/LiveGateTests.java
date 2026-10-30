import dev.hazel.livealarm.LiveGate;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

/**
 * When a stream goes live and nothing rings, the cause is one of a short list, and every entry
 * on it is a decision this gate made. These assertions pin each decision so the difference
 * between "by design" and "a defect" stays visible, and so the ring itself cannot regress
 * silently — a missing alarm is the one failure this app cannot afford.
 */
public final class LiveGateTests {
    private static int count;
    private static void check(boolean ok,String what){count++;if(!ok)throw new AssertionError(what);}
    private static final LiveGate.Schedule ALWAYS=t->true;
    private static final long MINUTE=60000L;

    private static LiveGate.State armed(long armedAt,boolean baseline){
        LiveGate.State s=new LiveGate.State();
        s.baseline=baseline;
        return s;
    }

    public static void main(String[] args){
        long now=1_700_000_000_000L;
        long streamStart=now-10*MINUTE;

        // The ordinary promise: nothing was live, then it is, and it rings. Marking the session
        // as notified is the caller's half of the contract (the service does it in ring()).
        LiveGate.State s=new LiveGate.State();
        check(!LiveGate.observe(s,false,0,now-2*MINUTE,now-3600_000L,false,ALWAYS).ring,"offline is not an alarm");
        check(LiveGate.observe(s,true,streamStart,now,now-3600_000L,false,ALWAYS).ring,"a new stream rings");

        // Dedup: the same session never rings twice, however many times it is observed.
        s.notified=s.session;
        check(!LiveGate.observe(s,true,streamStart,now+MINUTE,now-3600_000L,false,ALWAYS).ring,"the same session stays silent");
        check(!LiveGate.observe(s,true,streamStart,now+2*MINUTE,now-3600_000L,false,ALWAYS).ring,"and stays silent later too");

        // A genuinely later broadcast of the same anchor rings again, which is the whole point
        // of keying on the reported start rather than on the anchor.
        check(LiveGate.observe(s,true,streamStart+3600_000L,now+3600_000L,now-3600_000L,false,ALWAYS).ring,"a later broadcast rings");

        // A missing arm time must fail open. Filling it in with "now" is what made every stream
        // look like it began before the watch was armed, and with catch-up off that silenced the
        // app completely — the worst possible failure for an alarm.
        LiveGate.State unknown=new LiveGate.State();
        check(LiveGate.observe(unknown,true,streamStart,now,0,false,ALWAYS).ring,"an unknown arm time still rings");

        // By design: arming (or adding the anchor) while it was already live does not announce a
        // stream the user can already see, unless 补报 (catch-up) is on. This is the entry to look
        // for in the records: 「开启守候时已在播」.
        LiveGate.State armedMidStream=armed(now,true);
        check(!LiveGate.observe(armedMidStream,true,streamStart,now,now,false,ALWAYS).ring,"arming mid-stream stays quiet");
        LiveGate.State armedMidStreamCatchUp=armed(now,true);
        check(LiveGate.observe(armedMidStreamCatchUp,true,streamStart,now,now,true,ALWAYS).ring,"catch-up announces it on request");

        // The reminder window, with the default 01:00-06:00 rule.
        long day=1_700_000_000_000L;
        long night=day;
        LiveGate.Schedule hours=t->(t-day)%86_400_000L>=60*MINUTE&&(t-day)%86_400_000L<=360*MINUTE;
        long inWindow=day+120*MINUTE;
        long afterWindow=day+400*MINUTE;
        LiveGate.State inside=new LiveGate.State();
        check(LiveGate.observe(inside,true,inWindow-5*MINUTE,inWindow,inWindow-3600_000L,false,hours).ring,
            "a stream that starts inside the window rings");
        // A late detection that lands after the window closed is silent even with catch-up on.
        // Deliberate: the window is the user saying when they want to be told, and doze can push
        // a check past its end. Documented here because it is a plausible cause of a missed ring.
        LiveGate.State late=new LiveGate.State();
        check(!LiveGate.observe(late,true,inWindow-5*MINUTE,afterWindow,inWindow-3600_000L,true,hours).ring,
            "a check that lands after the window is silent");
        // A stream that began before the window and is still running when it opens is the case
        // catch-up exists for.
        LiveGate.State beforeWindow=new LiveGate.State();
        check(!LiveGate.observe(beforeWindow,true,inWindow-90*MINUTE,inWindow,inWindow-3600_000L,false,hours).ring,
            "an earlier start is ignored without catch-up");
        LiveGate.State beforeWindowCatchUp=new LiveGate.State();
        check(LiveGate.observe(beforeWindowCatchUp,true,inWindow-90*MINUTE,inWindow,inWindow-3600_000L,true,hours).ring,
            "and announced with catch-up");

        // The window is evaluated on the detection moment first: outside it nothing rings.
        LiveGate.State out=new LiveGate.State();
        check(!LiveGate.observe(out,true,afterWindow-5*MINUTE,afterWindow,afterWindow-3600_000L,true,hours).ring,
            "outside the window nothing rings");

        System.out.println("PASS: "+count+" live-gate assertions");
    }
}

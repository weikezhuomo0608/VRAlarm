import dev.hazel.livealarm.PollPlan;
import dev.hazel.livealarm.Schedule;

/** Poll cycle timing for one or many anchors, independent of Android. */
public final class PollPlanTests {
    private static int count;
    private static void check(boolean yes,String label){count++;if(!yes)throw new AssertionError(label);}

    public static void main(String[] args){
        // The single-anchor wake lock must keep the budget the service used before multi-anchor support.
        check(PollPlan.wakeLockMillis(1)==2*17000+8000,"one anchor budgets a retry on top of the fetch");
        check(PollPlan.wakeLockMillis(0)==2*17000+8000,"an empty list still budgets one fetch with retry");
        check(PollPlan.wakeLockMillis(-3)==2*17000+8000,"a negative count still budgets one fetch with retry");
        check(PollPlan.wakeLockMillis(16)==16*2*17000+8000,"sixteen anchors budget every fetch and its retry");
        check(PollPlan.wakeLockMillis(16)>PollPlan.wakeLockMillis(4),"the budget grows with the list");
        for(int n=1;n<=16;n++)check(PollPlan.wakeLockMillis(n)>=n*PollPlan.FETCH_BUDGET_MS,"the budget covers each fetch at size "+n);

        // Unchanged single-anchor backoff ladder, so no existing install changes behaviour.
        check(PollPlan.backoffSeconds(30,false,1)==30,"the first ordinary failure waits one poll interval");
        check(PollPlan.backoffSeconds(30,false,2)==60,"the second ordinary failure doubles");
        check(PollPlan.backoffSeconds(30,false,3)==120,"the third ordinary failure doubles again");
        check(PollPlan.backoffSeconds(30,false,9)==300,"a long failure streak stays capped at five minutes");
        check(PollPlan.backoffSeconds(30,true,1)==120,"a rate limited cycle starts at two minutes");
        check(PollPlan.backoffSeconds(30,true,2)==240,"a rate limited cycle doubles to four minutes");
        check(PollPlan.backoffSeconds(30,true,3)==300,"a rate limited cycle stays capped at five minutes");
        check(PollPlan.backoffSeconds(0,false,1)==15,"a tiny poll interval still backs off fifteen seconds");
        check(PollPlan.backoffSeconds(0,true,1)==120,"a tiny poll interval still respects the rate limit floor");
        check(PollPlan.backoffSeconds(120,false,1)==120,"a long poll interval is its own floor");
        check(PollPlan.backoffSeconds(30,true,1)>PollPlan.backoffSeconds(30,false,1),"rate limiting waits longer than an ordinary failure");
        int previous=0;
        for(int failures=1;failures<=8;failures++){
            int now=PollPlan.backoffSeconds(30,false,failures);
            check(now>=previous,"backoff never shrinks as failures accumulate");
            check(now<=PollPlan.MAX_BACKOFF_SECONDS,"backoff never exceeds the cap");
            previous=now;
        }

        check(PollPlan.gapSeconds(30,true,0)==30,"a fast cycle keeps the configured interval");
        check(PollPlan.gapSeconds(30,true,300)==29,"a sub-second cycle is rounded up to one second of spend");
        check(PollPlan.gapSeconds(30,true,1000)==29,"a one second cycle spends one second");
        check(PollPlan.gapSeconds(30,true,12000)==18,"time spent polling is discounted from the wait");
        check(PollPlan.gapSeconds(30,true,29000)==PollPlan.MIN_GAP_SECONDS,"a nearly full cycle still leaves the minimum gap");
        check(PollPlan.gapSeconds(30,true,90000)==PollPlan.MIN_GAP_SECONDS,"an overlong cycle never waits a negative interval");
        check(PollPlan.gapSeconds(30,false,0)==PollPlan.IDLE_GAP_SECONDS,"outside the reminder window the cycle stays slow");
        check(PollPlan.gapSeconds(30,false,99999)==PollPlan.IDLE_GAP_SECONDS,"outside the window the fast cycle is ignored");
        check(PollPlan.gapSeconds(30,true,12000)<PollPlan.gapSeconds(30,true,0),"a slower cycle shortens the remaining wait");

        // The safety net behind the check loop: late enough that a healthy loop always replaces
        // it, early enough that a stalled loop is noticed in minutes rather than at the watchdog.
        check(PollPlan.keepAliveSeconds(30,true)==90,"the default interval waits three cycles");
        check(PollPlan.keepAliveSeconds(15,true)==PollPlan.MIN_KEEPALIVE_SECONDS,"a fast interval still respects the floor");
        check(PollPlan.keepAliveSeconds(1,true)==PollPlan.MIN_KEEPALIVE_SECONDS,"an extreme interval cannot turn the net into a hot loop");
        check(PollPlan.keepAliveSeconds(60,true)==180,"a one minute interval waits three minutes");
        check(PollPlan.keepAliveSeconds(120,true)==360,"a two minute interval waits six minutes");
        check(PollPlan.keepAliveSeconds(30,false)==540,"outside the window the net is slower than the idle gap");
        check(PollPlan.keepAliveSeconds(30,true)>PollPlan.gapSeconds(30,true,0),"the net is always later than the cycle it guards");
        for(int poll:new int[]{15,30,60,120}){
            check(PollPlan.keepAliveSeconds(poll,true)>PollPlan.gapSeconds(poll,true,0),"the net outlives the cycle at "+poll+" s");
            check(PollPlan.keepAliveSeconds(poll,true)<=PollPlan.MAX_BACKOFF_SECONDS*4,"the net stays within minutes at "+poll+" s");
        }

        // 即将开播提速判定与周表预约时间计算。
        java.util.ArrayList<Schedule.Entry> plan=new java.util.ArrayList<>();
        plan.add(new Schedule.Entry("f",16,1200,-1,"")); // Friday 20:00
        long sod=0;
        check(PollPlan.dueSoon(plan,4,sod,(1200L-29)*60000L),"a start 29 minutes away is due soon");
        check(PollPlan.dueSoon(plan,4,sod,(1200L+30)*60000L),"the boost lingers 30 minutes past the start");
        check(!PollPlan.dueSoon(plan,4,sod,(1200L+31)*60000L),"31 minutes after the start it is no longer soon");
        check(!PollPlan.dueSoon(plan,3,sod,1200*60000L),"the wrong weekday never boosts");
        check(!PollPlan.dueSoon(null,4,sod,1200*60000L),"a null plan never boosts");
        // nextOccurrence() works in one consistent frame: `today` + its start-of-day + the zone.
        // The epoch's 1970-01-02 is a Friday, which is what the fixture above uses (days=16).
        java.time.ZoneId zone=java.time.ZoneId.of("UTC");
        long day=86400000L;
        java.time.LocalDate friday=java.time.LocalDate.of(1970,1,2);
        long sodFri=day*1;
        long nowF=(1200L-60)*60000L+sodFri;
        long[] at=PollPlan.nextOccurrence(plan,friday,sodFri,zone,nowF,10*60000L);
        check(at!=null&&at[0]==1200L*60000L+sodFri,"the occurrence reports the start itself");
        check(at[1]==at[0]-10*60000L,"the arm moment lands the lead before the start");
        check(PollPlan.nextOccurrence(plan,friday,sodFri,zone,at[1]+60000L,10*60000L)==null,
            "a start already inside its lead is not announced any more");
        java.time.LocalDate saturday=java.time.LocalDate.of(1970,1,3);
        long sodSat=saturday.atStartOfDay(zone).toInstant().toEpochMilli();
        long[] sat=PollPlan.nextOccurrence(plan,saturday,sodSat,zone,sodSat+19*3600000L,10*60000L);
        check(sat!=null&&sat[0]==sodSat+6*day+1200*60000L,"from Saturday, next Friday is six days out and still found");
        check(PollPlan.nextOccurrence(new java.util.ArrayList<Schedule.Entry>(),friday,sodFri,zone,0,300000L)==null,"an empty plan has no pre-stream time");
        long lead=PollPlan.PRESTREAM_LEAD_MILLIS;
        check(lead==300000L,"the default pre-stream lead is five minutes");
        check(PollPlan.nextOccurrence(plan,friday,sodFri,zone,nowF,lead)[1]==1200L*60000L+sodFri-lead,
            "an entry with no lead of its own uses the default");

        // 一次性日程：只有那一天，可以比一周更远；它自己的提前量优先于默认值；过了那一天就不再出现。
        java.util.ArrayList<Schedule.Entry> events=new java.util.ArrayList<>();
        events.add(new Schedule.Entry("b",0,1260,-1,"生日会","1970-01-20",60));
        long[] once=PollPlan.nextOccurrence(events,friday,sodFri,zone,nowF,lead);
        check(once!=null&&once[0]==(19L*1440+1260)*60000L,"a one-off event is found beyond the seven-day week");
        check(once[1]==once[0]-3600000L,"the entry's own lead wins over the default");
        check(PollPlan.nextOccurrence(events,java.time.LocalDate.of(1970,1,20),19*day,zone,19*day,lead)!=null,"the event is still upcoming on its own day");
        check(PollPlan.nextOccurrence(events,java.time.LocalDate.of(1970,1,21),20*day,zone,20*day,lead)==null,"once its day has passed the event is gone");
        java.util.ArrayList<Schedule.Entry> both=new java.util.ArrayList<>();
        both.add(events.get(0));
        both.add(new Schedule.Entry("f",16,1200,-1,""));
        long[] after=PollPlan.nextOccurrence(both,java.time.LocalDate.of(1970,1,22),21*day,zone,21*day,lead);
        check(after!=null&&after[0]==22*day+1200*60000L,"after the event its anchor's weekly slot takes over");
        check(Schedule.dateOf(new Schedule.Entry("x",0,600,-1,"","2026-02-30",0))==null,"an impossible date parses to nothing");

        // 迟到判定：一次迟到本身并不能说明守候停了。休眠会同时推迟唤醒与心跳，
        // 所以只有当进程真的被重建过、或者手机醒着却仍然迟到时，才值得告诉用户。
        long floor=PollPlan.INTERRUPTION_FLOOR_MS;
        check(floor>PollPlan.ALLOW_WHILE_IDLE_FLOOR_MS,"the report floor clears the platform's doze wake-up floor");
        check(PollPlan.ALLOW_WHILE_IDLE_FLOOR_MS>=9*60000L,"doze will not wake an app more often than about every nine minutes");
        check(PollPlan.classifyLate(floor-1,true,true,true,true)==PollPlan.Late.NONE,"just under the floor is never reported");
        check(PollPlan.classifyLate(floor+1,true,true,true,true)==PollPlan.Late.RESTARTED,"a service created after its own plan was killed");
        check(PollPlan.classifyLate(floor+1,true,true,false,true)==PollPlan.Late.DELAYED,"an alive service late while the phone is awake");
        check(PollPlan.classifyLate(floor+1,true,true,false,false)==PollPlan.Late.NONE,"a dozing phone explains any lateness");
        check(PollPlan.classifyLate(3*3600000L,false,true,false,true)==PollPlan.Late.NONE,"an idle plan outside the window is allowed to be hours late");
        check(PollPlan.classifyLate(3*3600000L,true,false,false,true)==PollPlan.Late.NONE,"nothing was expected to be watched outside the window");
        check(PollPlan.classifyLate(3*3600000L,false,false,true,false)==PollPlan.Late.NONE,"idle plan plus doze plus the wrong side of the boundary stays silent");
        check(PollPlan.classifyLate(0,true,true,true,true)==PollPlan.Late.NONE,"a cycle on time is not late at all");
        check(PollPlan.classifyLate(3*3600000L,true,true,true,false)==PollPlan.Late.RESTARTED,"a real restart is reported even if the phone is asleep now");

        // Outside the window there is no cycle left to pace: the watch parks itself (see duty()),
        // so the idle gap survives only as the defensive answer for a cycle that began inside and
        // finished after the window closed. What used to be "continuous mode keeps the configured
        // interval outside the window" is now "there is nothing outside the window to keep".
        check(PollPlan.gapSeconds(30,false,0)==PollPlan.IDLE_GAP_SECONDS,"the idle gap survives as the defensive answer");
        check(PollPlan.gapSeconds(15,false,5000)==PollPlan.IDLE_GAP_SECONDS,"the idle answer ignores how long that cycle took");
        check(PollPlan.gapSeconds(1,true,0)==PollPlan.MIN_GAP_SECONDS,"a one-second interval still cannot poll faster than the floor");
        check(PollPlan.duty(false,false,true,true)==PollPlan.Duty.OFF,"a watch the user switched off is off, not paused");
        check(PollPlan.duty(false,true,false,false)==PollPlan.Duty.OFF,"switched off wins over any schedule reading");
        check(PollPlan.duty(true,true,false,false)==PollPlan.Duty.WATCHING,"all-day mode never parks");
        check(PollPlan.duty(true,false,true,true)==PollPlan.Duty.WATCHING,"inside a window the watch runs");
        check(PollPlan.duty(true,false,true,false)==PollPlan.Duty.PAUSED,"outside the window the watch parks");
        check(PollPlan.duty(true,false,false,false)==PollPlan.Duty.PAUSED,"custom mode with no rule switched on is parked forever");
        check(PollPlan.duty(true,false,false,true)==PollPlan.Duty.PAUSED,"an impossible reading parks instead of watching");
        // 考子闹钟: one anchor may ask for round-the-clock watching, and the loop has to run for that
        // anchor to be watched at all — so it opens the schedule for the watch, not for every anchor.
        check(PollPlan.duty(true,false,true,false,true)==PollPlan.Duty.WATCHING,"a 考子闹钟 anchor keeps the watch running outside the window");
        check(PollPlan.duty(true,false,false,false,true)==PollPlan.Duty.WATCHING,"and even with no window defined at all");
        check(PollPlan.duty(true,false,false,false,false)==PollPlan.Duty.PAUSED,"the same reading parks once no anchor asks for it");
        check(PollPlan.duty(false,false,true,true,true)==PollPlan.Duty.OFF,"the master switch still wins over 考子闹钟");
        check(PollPlan.duty(true,true,false,false,true)==PollPlan.Duty.WATCHING,"the user's own all-day mode and an anchor's agree");
        check(PollPlan.keepAliveSeconds(30,true,true)==45,"continuous mode arms the net half an interval out");
        check(PollPlan.keepAliveSeconds(30,false,true)==45,"continuous mode ignores the window when arming the net");
        check(PollPlan.keepAliveSeconds(30,true,true)<PollPlan.keepAliveSeconds(30,true,false),"continuous mode is far sooner than the saver net");
        check(PollPlan.keepAliveSeconds(30,true,true)>PollPlan.gapSeconds(30,true,0),"the net is still later than the cycle it guards");
        check(PollPlan.keepAliveSeconds(1,true,true)>=PollPlan.MIN_TURBO_NET_SECONDS,"the continuous net cannot become a hot loop");
        check(PollPlan.keepAliveSeconds(30,true,true)<=PollPlan.MAX_BACKOFF_SECONDS,"the continuous net stays inside the doze floor's neighbourhood");

        // High-frequency windows: the configured pace inside them, the slow floor outside, and
        // nothing at all when the option is off.
        check(PollPlan.effectivePollSeconds(30,false,false)==30,"with the option off the pace never changes");
        check(PollPlan.effectivePollSeconds(30,false,true)==30,"with the option off a window changes nothing either");
        check(PollPlan.effectivePollSeconds(30,true,true)==30,"inside a high-frequency window the configured pace is kept");
        check(PollPlan.effectivePollSeconds(30,true,false)==PollPlan.LOW_FREQUENCY_SECONDS,"outside them the pace drops to the slow floor");
        check(PollPlan.effectivePollSeconds(15,true,false)==120,"a fifteen-second setting is slowed down to two minutes");
        check(PollPlan.effectivePollSeconds(60,true,false)==120,"a one-minute setting is slowed down to two minutes");
        check(PollPlan.effectivePollSeconds(120,true,false)==120,"a two-minute setting is already the floor");
        check(PollPlan.effectivePollSeconds(300,true,false)==300,"a pace slower than the floor is never sped up");
        check(PollPlan.LOW_FREQUENCY_SECONDS>PollPlan.MIN_GAP_SECONDS,"the slow floor is an interval, not the safety minimum");
        // The slow pace is the user's choice (30 s / 1 / 2 / 5 minutes) and is never faster than the floor.
        for(int choice:PollPlan.LOW_FREQUENCY_CHOICES){
            check(PollPlan.effectivePollSeconds(15,true,false,choice)==choice,"the chosen slow pace is used ("+choice+")");
            check(PollPlan.effectivePollSeconds(choice,true,false,choice)==choice,"a pace at the chosen floor stays there ("+choice+")");
            check(PollPlan.effectivePollSeconds(choice*2,true,false,choice)==choice*2,"a slower pace is never sped up ("+choice+")");
            check(PollPlan.effectivePollSeconds(15,true,true,choice)==15,"inside the window the chosen floor is irrelevant ("+choice+")");
            check(PollPlan.effectivePollSeconds(15,false,false,choice)==15,"with the option off the chosen floor is irrelevant ("+choice+")");
        }
        check(PollPlan.effectivePollSeconds(15,true,false,0)==PollPlan.LOW_FREQUENCY_SECONDS,"an unusable floor falls back to the default");
        // The hours a 考子 anchor keeps alive: the slow floor applies there on its own, whether or not
        // the pacing option is on, because nobody is expecting a reminder at 4 am.
        check(PollPlan.effectivePollSeconds(30,false,false,PollPlan.LOW_FREQUENCY_SECONDS,true)==PollPlan.LOW_FREQUENCY_SECONDS,
            "outside the reminder windows a 考子 hour is slowed without the pacing option");
        check(PollPlan.effectivePollSeconds(30,true,false,PollPlan.LOW_FREQUENCY_SECONDS,true)==PollPlan.LOW_FREQUENCY_SECONDS,
            "and the option being on changes nothing there");
        check(PollPlan.effectivePollSeconds(60,false,false,300,true)==300,"a one-minute setting is slowed to the chosen floor");
        check(PollPlan.effectivePollSeconds(600,false,false,120,true)==600,"a pace already slower than the floor is never sped up");
        check(PollPlan.effectivePollSeconds(30,false,true,120,true)==30,"a high-frequency window still means the user asked for speed");
        check(PollPlan.effectivePollSeconds(30,false,false,120,false)==30,"inside the windows nothing changes for anyone");
        check(PollPlan.effectivePollSeconds(30,false,false,0,true)==PollPlan.LOW_FREQUENCY_SECONDS,"an unusable floor falls back even in a 考子 hour");
        // The net's period is what the phone sleeps through: inside the windows it stays the stall net,
        // outside them it is the loop itself and must sit at the pace, not three intervals out.
        check(PollPlan.netSeconds(30,true,false)==PollPlan.keepAliveSeconds(30,true,true),"inside the windows the net is unchanged");
        check(PollPlan.netSeconds(120,true,true)==120+PollPlan.NET_MARGIN_SECONDS,"a 考子 hour arms the net one margin past the pace");
        check(PollPlan.netSeconds(120,true,true)<PollPlan.keepAliveSeconds(120,true,false),"and sooner than the saver net it replaces");
        check(PollPlan.netSeconds(120,true,true)>PollPlan.gapSeconds(120,true,0),"but never sooner than the cycle it carries");
        check(PollPlan.netSeconds(30,false,true)==PollPlan.keepAliveSeconds(30,true),"the saver value ignores the 考子 rule");
        check(PollPlan.netSeconds(30,false,false)==PollPlan.keepAliveSeconds(30,true),"and so does a window hour");
        check(PollPlan.NET_MARGIN_SECONDS>0&&PollPlan.NET_MARGIN_SECONDS<=60,"the margin is small enough to be a pace, not a pause");
        check(PollPlan.LOW_FREQUENCY_CHOICES.length==4&&PollPlan.LOW_FREQUENCY_CHOICES[0]==30
            &&PollPlan.LOW_FREQUENCY_CHOICES[3]==300,"the offered floors run from thirty seconds to five minutes");
        // The default must be one the interface offers, or a stored value could not be displayed.
        boolean offersDefault=false;for(int choice:PollPlan.LOW_FREQUENCY_CHOICES)if(choice==PollPlan.LOW_FREQUENCY_SECONDS)offersDefault=true;
        check(offersDefault,"the default floor is one of the offered ones");
        // A slowed cycle must still be far below the interruption floor, or every slow hour would be
        // reported as the watch having been interrupted.
        check(PollPlan.LOW_FREQUENCY_SECONDS*1000L<PollPlan.INTERRUPTION_FLOOR_MS,"a slow pace cannot look like an interruption");
        check(PollPlan.gapSeconds(PollPlan.effectivePollSeconds(15,true,false),true,0)==120,"an effective slow pace produces a two-minute gap");
        check(PollPlan.gapSeconds(PollPlan.effectivePollSeconds(15,true,true),true,0)==15,"an effective fast pace produces the configured gap");
        check(PollPlan.keepAliveSeconds(120,true,true)>PollPlan.LOW_FREQUENCY_SECONDS,"the net still fires after the slow cycle it guards");
        check(PollPlan.keepAliveSeconds(120,true,true)<=PollPlan.MAX_BACKOFF_SECONDS,"the slow net stays inside the doze neighbourhood");
        check(PollPlan.backoffSeconds(120,true,1)==120,"the failure ladder starts from the effective pace, not the configured one");
        System.out.println("PASS: "+count+" poll cycle timing assertions");
    }
}

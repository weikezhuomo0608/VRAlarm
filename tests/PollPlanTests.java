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
        long at=PollPlan.nextStart(plan,4,sod,(1200L-60)*60000L,10*60000L);
        check(at==(1200L-10)*60000L,"the pre-stream alarm lands lead minutes before the start");
        check(PollPlan.nextStart(plan,4,sod,(1200L-9)*60000L,10*60000L)==0,"a start already inside the lead is skipped today");
        long sat=PollPlan.nextStart(plan,5,sod,(1200L-60)*60000L,10*60000L);
        check(sat==(1440L*6+1200-10)*60000L,"from Saturday, next Friday is six days out and still found");
        check(PollPlan.nextStart(new java.util.ArrayList<Schedule.Entry>(),4,sod,0,300000L)==0,"an empty plan has no pre-stream time");

        System.out.println("PASS: "+count+" poll cycle timing assertions");
    }
}

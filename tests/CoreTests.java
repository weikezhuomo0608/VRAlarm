import dev.hazel.livealarm.*;
import java.time.*;
import java.util.*;

public class CoreTests {
    static int count=0;
    static void check(boolean value,String test){count++;if(!value)throw new AssertionError(test);}
    static long at(String s){return LocalDateTime.parse(s).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
    static TimeRules.Window w(int start,int end,int days){return new TimeRules.Window("test","test",start,end,days,true);}
    static boolean inside(String s,TimeRules.Window... rules){return TimeRules.contains(at(s),false,Arrays.asList(rules),ZoneId.of("Asia/Shanghai"));}
    public static void main(String[] args){
        TimeRules.Window night=w(60,360,127);
        check(!inside("2026-09-07T00:59:59",night),"before start");check(inside("2026-09-07T01:00:00",night),"inclusive start");check(inside("2026-09-07T05:59:59",night),"last second");check(!inside("2026-09-07T06:00:00",night),"exclusive end");
        TimeRules.Window monday=w(1380,360,1);
        check(inside("2026-09-07T23:00:00",monday),"Monday start");check(inside("2026-09-08T05:30:00",monday),"Tuesday belongs to Monday");check(!inside("2026-09-07T05:30:00",monday),"Monday morning belongs to Sunday");check(!inside("2026-09-08T06:00:00",monday),"overnight end");
        TimeRules.Window sunday=w(1380,60,64);check(inside("2026-09-07T00:30:00",sunday),"Sunday week wrap");
        TimeRules.Window day=w(60,60,1);check(inside("2026-09-08T00:59:59",day),"24h anchored interval");check(!inside("2026-09-08T01:00:00",day),"24h excludes end");
        check(!inside("2026-09-07T02:00:00"),"empty rules fail closed");
        check(TimeRules.contains(at("2026-09-07T09:00:00"),true,Collections.emptyList(),ZoneId.of("UTC")),"all day ignores empty list");
        check(inside("2026-09-07T05:30:00",night,w(300,600,127)),"overlap union");
        check(!inside("2026-09-07T02:00:00",new TimeRules.Window("off","off",0,0,127,false)),"disabled interval");
        long next=TimeRules.nextBoundary(at("2026-09-07T06:00:00"),false,Arrays.asList(night),ZoneId.of("Asia/Shanghai"));check(next==at("2026-09-08T01:00:00"),"next day boundary");
        check(TimeRules.nextBoundary(at("2026-09-08T06:00:00"),false,Arrays.asList(monday),ZoneId.of("Asia/Shanghai"))==at("2026-09-14T23:00:00"),"next week boundary");
        check(TimeRules.nextBoundary(at("2026-09-07T00:00:00"),true,Arrays.asList(night),ZoneId.of("UTC"))==0,"no alarm for all-day");
        check(!TimeRules.contains(at("2026-09-07T05:30:00"),false,Arrays.asList(night),ZoneId.of("Asia/Tokyo")),"Japan offset");
        ZoneId ny=ZoneId.of("America/New_York");long dst=LocalDateTime.parse("2026-03-08T03:15:00").atZone(ny).toInstant().toEpochMilli();check(TimeRules.contains(dst,false,Arrays.asList(w(60,240,127)),ny),"DST spring day");
        long fallback=ZonedDateTime.of(LocalDateTime.parse("2026-11-01T01:30:00"),ny).withLaterOffsetAtOverlap().toInstant().toEpochMilli();check(TimeRules.contains(fallback,false,Arrays.asList(w(60,180,127)),ny),"DST repeated hour");
        boolean rejected=false;try{w(1440,1,127);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject invalid minute");rejected=false;try{w(0,1,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject no days");
        // Exhaustively compare one week against a minute-level independent model.
        int[] starts={0,60,360,720,1380};int[] ends={0,60,360,1140,1439};
        for(int start:starts)for(int end:ends){TimeRules.Window rule=w(start,end,0b1010101);for(int minute=0;minute<10080;minute+=7){int d=minute/1440,t=minute%1440,anchor=d;boolean active;
            if(start<end)active=t>=start&&t<end;else{active=t>=start||t<end;if(t<end)anchor=(d+6)%7;if(start==end){active=true;if(t<start)anchor=(d+6)%7;}}
            boolean expected=active&&(rule.days&(1<<anchor))!=0;
            check(TimeRules.contains(at("2026-09-07T00:00:00")+minute*60000L,false,Arrays.asList(rule),ZoneId.of("Asia/Shanghai"))==expected,"weekly minute model");}}
        LiveGate.Schedule always=t->true;long now=at("2026-09-07T02:00:00"),armed=now-60000;
        LiveGate.State s=new LiveGate.State();check(!LiveGate.observe(s,true,now-3600000,now,armed,false,always).ring,"initial existing live not alerted");
        check(LiveGate.observe(s,true,now-3600000,now+30000,armed,true,always).ring,"opt-in catchup");s.notified=s.session;
        check(!LiveGate.observe(s,true,now-3600000,now+60000,armed,true,always).ring,"same session dedup");
        LiveGate.observe(s,false,0,now+90000,armed,true,always);LiveGate.observe(s,false,0,now+120000,armed,true,always);
        check(!LiveGate.observe(s,true,now+100000,now+150000,armed,true,always).ring,"short reconnect suppressed even with changed timestamp");
        check(!LiveGate.observe(s,true,now+100000,now+180000,armed,true,always).ring,"reconnect alias remains stable");
        LiveGate.observe(s,false,0,now+210000,armed,true,always);LiveGate.observe(s,false,0,now+240000,armed,true,always);
        check(LiveGate.observe(s,true,now+400000,now+420000,armed,true,always).ring,"genuine later broadcast alerts");
        s=new LiveGate.State();check(!LiveGate.observe(s,true,0,now,armed,false,always).ring,"unknown first observation fails closed");
        check(LiveGate.observe(s,true,0,now+30000,armed,true,always).ring,"unknown catchup allowed");s.notified=s.session;
        check(!LiveGate.observe(s,true,now-3600000,now+60000,armed,true,always).ring,"metadata recovery no duplicate");
        s=new LiveGate.State();LiveGate.observe(s,false,0,now,armed,false,always);check(LiveGate.observe(s,true,0,now+30000,armed,false,always).ring,"offline-to-live without timestamp");
        s=new LiveGate.State();LiveGate.Schedule hours=t->TimeRules.contains(t,false,Arrays.asList(night),ZoneId.of("Asia/Shanghai"));
        long before=at("2026-09-07T00:59:00"),after=at("2026-09-07T01:00:01");
        check(!LiveGate.observe(s,true,before,before,before-60000,false,hours).ring,"start outside window");check(!LiveGate.observe(s,true,before,after,before-60000,false,hours).ring,"strict window entry no catchup");check(LiveGate.observe(s,true,before,after,before-60000,true,hours).ring,"opt-in window entry catchup");
        s=new LiveGate.State();check(LiveGate.observe(s,true,now-10000,now,armed,false,hours).ring,"new live during allowed hours");
        s.notified=s.session;s.baseline=true;check(!LiveGate.observe(s,true,now-10000,now+30000,now+10000,true,hours).ring,"restart retains dedup");
        System.out.println("PASS: "+count+" schedule and broadcast-state assertions");
    }
}

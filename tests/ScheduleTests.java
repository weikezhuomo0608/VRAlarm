import dev.hazel.livealarm.Schedule;
import java.time.LocalDate;
import java.util.*;

/** Weekly schedule rules: validation, tolerant repair and weekday matching. */
public final class ScheduleTests {
    private static int count;
    private static void ok(boolean yes,String label){count++;if(!yes)throw new AssertionError(label);}
    private static void throwsMessage(Runnable r,String part,String label){
        count++;
        try{r.run();}catch(IllegalArgumentException e){
            if(!e.getMessage().contains(part))throw new AssertionError(label+" — got: "+e.getMessage());
            return;
        }
        throw new AssertionError(label+" — nothing thrown");
    }
    private static Schedule.Entry e(String id,int days,int start,int end,String note){return new Schedule.Entry(id,days,start,end,note);}

    public static void main(String[] args){
        // Strict validation for user writes.
        throwsMessage(()->Schedule.validate(null),"日程为空","null list is rejected");
        List<Schedule.Entry> one=new ArrayList<>();
        one.add(e("w1",127,1200,-1,"杂谈"));
        Schedule.validate(one);ok(true,"a simple entry validates");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(e("w1",0,1200,-1,"")))),"请为每条安排选择星期","days=0 is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(e("w1",128,1200,-1,"")))),"请为每条安排选择星期","days=128 is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(e("w1",127,1440,-1,"")))),"请为每条安排选择开始时间","start=1440 is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(e("w1",127,1200,1440,"")))),"结束时间无效","end=1440 is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(e("w1",127,1200,-2,"")))),"结束时间无效","end=-2 is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(e("bad id!",127,1200,-1,"")))),"编号无效","unsafe id is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(e("w1",127,1200,-1,""),new Schedule.Entry("w1",63,900,-1,"")))),"编号无效","duplicate ids are rejected");
        StringBuilder longNote=new StringBuilder();
        for(int i=0;i<41;i++)longNote.append("注");
        final String ln=longNote.toString();
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(e("w1",127,1200,-1,ln)))),"备注最多","a 41-char note is rejected");
        List<Schedule.Entry> many=new ArrayList<>();
        for(int i=0;i<17;i++)many.add(e("w"+i,127,600+i,-1,""));
        throwsMessage(()->Schedule.validate(many),"最多 16 条","a 17-entry list is rejected");
        List<Schedule.Entry> weeks=new ArrayList<>();
        for(int i=0;i<Schedule.MAX_PER_ANCHOR;i++)weeks.add(e("w"+i,127,600,-1,""));
        for(int i=0;i<Schedule.MAX_ONCE_PER_ANCHOR;i++)weeks.add(new Schedule.Entry("o"+i,0,600,-1,"","2026-12-"+String.format("%02d",(i%28)+1),0));
        Schedule.validate(weeks);ok(true,"a full weekly table plus a full calendar validates");
        List<Schedule.Entry> tooManyOnce=new ArrayList<>();
        for(int i=0;i<Schedule.MAX_ONCE_PER_ANCHOR+1;i++)tooManyOnce.add(new Schedule.Entry("o"+i,0,600,-1,"","2026-12-"+String.format("%02d",(i%28)+1),0));
        throwsMessage(()->Schedule.validate(tooManyOnce),"最多 "+Schedule.MAX_ONCE_PER_ANCHOR+" 条一次性安排","a calendar past its own allowance is rejected");

        // Tolerant repair for storage reads.
        ArrayList<Schedule.Entry> raw=new ArrayList<>();
        raw.add(e("ok",127,1200,1380,"游戏回"));
        raw.add(null);
        raw.add(e("bad days",0,600,-1,""));
        raw.add(e("dup",127,600,-1,""));
        raw.add(e("ok2",1,60,-1,ln+" trailing"));
        raw.add(e("bad end",127,600,-2,""));
        List<Schedule.Entry> fixed=Schedule.sanitize(raw);
        ok(fixed.size()==3,"sanitize keeps the three usable entries");
        ok(fixed.get(0).id.equals("ok")&&fixed.get(0).note.equals("游戏回"),"first entry survives intact");
        ok(fixed.get(1).id.equals("dup"),"duplicate id kept once");
        ok(fixed.get(2).note.length()==Schedule.MAX_NOTE,"an overlong note is trimmed, not dropped");
        ok(Schedule.sanitize(null).isEmpty(),"null sanitizes to empty");
        ok(Schedule.sanitize(new ArrayList<Schedule.Entry>()).isEmpty(),"empty sanitizes to empty");

        // One-off events: a real date, no weekday, and their own notice lead.
        Schedule.Entry weekly=new Schedule.Entry("f",16,1200,-1,"");
        Schedule.Entry event=new Schedule.Entry("b",0,1260,-1,"生日会","2026-09-30",60);
        ok(Schedule.oneOff(event)&&Schedule.dateOf(event).equals(LocalDate.of(2026,9,30)),"a dated entry is one-off");
        ok(!Schedule.oneOff(weekly)&&Schedule.dateOf(weekly)==null,"an entry without a date stays weekly");
        ok(Schedule.occursOn(event,LocalDate.of(2026,9,30)),"the event happens on its own day");
        ok(!Schedule.occursOn(event,LocalDate.of(2026,9,29))&&!Schedule.occursOn(event,LocalDate.of(2026,10,1)),"and on no other day");
        ok(Schedule.occursOn(weekly,LocalDate.of(2026,9,4))&&!Schedule.occursOn(weekly,LocalDate.of(2026,9,5)),"a weekly entry still matches by weekday");
        ok(Schedule.expired(event,LocalDate.of(2026,10,1))&&!Schedule.expired(event,LocalDate.of(2026,9,30)),"an event expires the day after");
        ok(!Schedule.expired(weekly,LocalDate.of(2030,1,1)),"a weekly entry never expires");
        Schedule.validate(new ArrayList<>(Arrays.asList(event)));ok(true,"a one-off entry validates");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(new Schedule.Entry("b",16,1260,-1,"","2026-09-30",0)))),
            "不再选择重复星期","a date together with weekday bits is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(new Schedule.Entry("b",0,1260,-1,"","2026-02-30",0)))),
            "日期无效","an impossible date is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(new Schedule.Entry("b",0,1260,-1,"","30/09/2026",0)))),
            "日期无效","a non-ISO date is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(new Schedule.Entry("b",0,1260,-1,"","2026-09-30",7)))),
            "提前提醒的选项无效","an unlisted lead is rejected");
        throwsMessage(()->Schedule.validate(new ArrayList<>(Arrays.asList(new Schedule.Entry("b",0,1260,-1,"","2026-09-30",-5)))),
            "提前提醒的选项无效","a negative lead is rejected");
        for(int choice:Schedule.LEAD_CHOICES)ok(Schedule.offersLead(choice),"every offered lead is accepted ("+choice+")");
        // Tolerant repair keeps a dated entry (with its lead) and drops a broken one.
        ArrayList<Schedule.Entry> mixed=new ArrayList<>();
        mixed.add(new Schedule.Entry("b",0,1260,-1,"生日会","2026-09-30",60));
        mixed.add(new Schedule.Entry("bad",16,1260,-1,"","2026-09-30",0));
        mixed.add(new Schedule.Entry("bad2",0,1260,-1,"","2026-13-01",0));
        mixed.add(new Schedule.Entry("plain",127,1200,-1,"","",0));
        List<Schedule.Entry> kept=Schedule.sanitize(mixed);
        ok(kept.size()==2,"sanitize keeps the dated entry and the weekly one");
        ok(kept.get(0).lead==60&&kept.get(0).date.equals("2026-09-30"),"the one-off entry keeps its date and lead");
        ok(kept.get(1).date.isEmpty()&&kept.get(1).days==127,"the weekly entry is untouched");
        ArrayList<Schedule.Entry> oddLead=new ArrayList<>();
        oddLead.add(new Schedule.Entry("b",0,1260,-1,"","2026-09-30",7));
        ok(Schedule.sanitize(oddLead).get(0).lead==0,"an unreadable lead falls back to the default");

        // Weekday matching: bit 0 = Monday.
        Schedule.Entry fri=new Schedule.Entry("f",16,1200,-1,"");
        ok(Schedule.on(fri,4)&&!Schedule.on(fri,5),"Friday-only entry matches bit 4");
        Schedule.Entry weekend=new Schedule.Entry("w",96,900,-1,"");
        ok(Schedule.on(weekend,5)&&Schedule.on(weekend,6)&&!Schedule.on(weekend,0),"weekend bits cover Sat+Sun");
        ok(!Schedule.on(null,3),"null entry never matches");

        System.out.println("PASS: "+count+" schedule assertions");
    }
}

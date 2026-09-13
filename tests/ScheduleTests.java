import dev.hazel.livealarm.Schedule;
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
        throwsMessage(()->Schedule.validate(null),"周表为空","null list is rejected");
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

        // Weekday matching: bit 0 = Monday.
        Schedule.Entry fri=new Schedule.Entry("f",16,1200,-1,"");
        ok(Schedule.on(fri,4)&&!Schedule.on(fri,5),"Friday-only entry matches bit 4");
        Schedule.Entry weekend=new Schedule.Entry("w",96,900,-1,"");
        ok(Schedule.on(weekend,5)&&Schedule.on(weekend,6)&&!Schedule.on(weekend,0),"weekend bits cover Sat+Sun");
        ok(!Schedule.on(null,3),"null entry never matches");

        System.out.println("PASS: "+count+" weekly schedule assertions");
    }
}

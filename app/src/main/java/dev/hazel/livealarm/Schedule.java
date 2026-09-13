package dev.hazel.livealarm;

import java.util.*;

/**
 * Weekly broadcast schedules shown on the timeline. Pure Java: no Android and no org.json,
 * so the validation rules stay testable like TimeRules and Anchors.
 *
 * A schedule is display-only: it never decides when the alarm may ring — that remains
 * TimeRules' job. End is optional; when set below start it runs into the next day.
 */
public final class Schedule {
    public static final int MAX_PER_ANCHOR=16, MAX_TOTAL=64, MAX_NOTE=40, MAX_ID=40;

    public static final class Entry {
        public final String id;
        public final int days,start,end;
        public final String note;
        /** end<0 means "no end time recorded"; the display then shows the start only. */
        public Entry(String id,int days,int start,int end,String note){
            this.id=id; this.days=days; this.start=start; this.end=end; this.note=note;
        }
    }

    private Schedule() {}

    private static boolean validId(String id){
        if(id==null||id.isEmpty()||id.length()>MAX_ID)return false;
        for(int i=0;i<id.length();i++){
            char c=id.charAt(i);
            boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-'||c=='_';
            if(!ok)return false;
        }
        return true;
    }

    /** Strict rules for a user-supplied list; throws a message the interface can show. */
    public static List<Entry> validate(List<Entry> list){
        if(list==null)throw new IllegalArgumentException("周表为空");
        if(list.size()>MAX_PER_ANCHOR)throw new IllegalArgumentException("每位主播最多 "+MAX_PER_ANCHOR+" 条周表安排");
        Set<String> ids=new HashSet<>();
        for(Entry e:list){
            if(e==null||!validId(e.id)||!ids.add(e.id))throw new IllegalArgumentException("周表条目编号无效");
            if(e.days<1||e.days>127)throw new IllegalArgumentException("请为每条安排选择星期");
            if(e.start<0||e.start>1439)throw new IllegalArgumentException("请为每条安排选择开始时间");
            if(e.end>1439||(e.end<0&&e.end!=-1))throw new IllegalArgumentException("结束时间无效");
            if(e.note!=null&&e.note.length()>MAX_NOTE)throw new IllegalArgumentException("备注最多 "+MAX_NOTE+" 个字");
        }
        return list;
    }

    /** Tolerant repair for data read back from storage; a bad entry is dropped, not fatal. */
    public static List<Entry> sanitize(List<Entry> raw){
        ArrayList<Entry> out=new ArrayList<>();
        if(raw!=null){
            Set<String> ids=new HashSet<>();
            for(Entry e:raw){
                if(e==null||!validId(e.id)||ids.contains(e.id))continue;
                if(e.days<1||e.days>127||e.start<0||e.start>1439)continue;
                if(e.end>1439||(e.end<0&&e.end!=-1))continue;
                String note=e.note==null?"":e.note;
                if(note.length()>MAX_NOTE)note=note.substring(0,MAX_NOTE);
                ids.add(e.id);out.add(new Entry(e.id,e.days,e.start,e.end,note));
                if(out.size()==MAX_PER_ANCHOR)break;
            }
        }
        return out;
    }

    /** weekdayZeroBased: 0=Monday … 6=Sunday, matching the interface's day bit. */
    public static boolean on(Entry e,int weekdayZeroBased){
        return e!=null&&weekdayZeroBased>=0&&weekdayZeroBased<7&&(e.days&(1<<weekdayZeroBased))!=0;
    }
}

package dev.hazel.livealarm;

import java.time.LocalDate;
import java.util.*;

/**
 * Broadcast schedules shown on the timeline. Pure Java: no Android and no org.json,
 * so the validation rules stay testable like TimeRules and Anchors.
 *
 * A schedule is display-only: it never decides when the alarm may ring — that remains
 * TimeRules' job. End is optional; when set below start it runs into the next day.
 *
 * An entry is either weekly (the original model: a weekday bitmask) or one-off (a calendar date —
 * birthdays, premieres, anything that happens once). The two are the same record with different
 * fields filled in, so nothing has to be migrated: an entry without a date behaves exactly as it
 * always did.
 */
public final class Schedule {
    public static final int MAX_PER_ANCHOR=16, MAX_TOTAL=64, MAX_NOTE=40, MAX_ID=40;
    /**
     * One-off entries get their own allowance. A calendar fills up (an event stays until the user
     * cleans it up), and it must not push next week's recurring table out of its sixteen slots.
     */
    public static final int MAX_ONCE_PER_ANCHOR=24;
    /** Notice offered per entry, in minutes; 0 means "use the global default". */
    public static final int[] LEAD_CHOICES={0,5,30,60,180};

    public static final class Entry {
        public final String id, note, date;
        public final int days,start,end,lead;
        /** end<0 means "no end time recorded"; the display then shows the start only.
         *  An empty date means weekly; a date means this one day only. */
        public Entry(String id,int days,int start,int end,String note){
            this(id,days,start,end,note,"",0);
        }
        public Entry(String id,int days,int start,int end,String note,String date,int lead){
            this.id=id; this.days=days; this.start=start; this.end=end; this.note=note;
            this.date=date==null?"":date; this.lead=lead;
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

    /**
     * The calendar day of a one-off entry, or null when the entry is weekly (or the text is not a
     * real date — 2026-02-30 parses to nothing, so a typo can never become a silent no-show).
     */
    public static LocalDate dateOf(String date){
        if(date==null||date.isEmpty())return null;
        try{return LocalDate.parse(date);}catch(java.time.DateTimeException bad){return null;}
    }
    public static LocalDate dateOf(Entry e){return e==null?null:dateOf(e.date);}
    public static boolean oneOff(Entry e){return dateOf(e)!=null;}
    /** Whether this entry happens on that day: one-off by its own date, weekly by its bit. */
    public static boolean occursOn(Entry e,LocalDate date){
        if(e==null||date==null)return false;
        LocalDate own=dateOf(e);
        if(own!=null)return own.equals(date);
        return (e.days&(1<<(date.getDayOfWeek().getValue()-1)))!=0;
    }
    /** A one-off entry whose day has passed. Weekly entries never expire — they keep coming round. */
    public static boolean expired(Entry e,LocalDate today){
        LocalDate own=dateOf(e);
        return own!=null&&today!=null&&own.isBefore(today);
    }
    public static boolean offersLead(int lead){
        for(int choice:LEAD_CHOICES)if(choice==lead)return true;
        return false;
    }

    /** Strict rules for a user-supplied list; throws a message the interface can show. */
    public static List<Entry> validate(List<Entry> list){
        if(list==null)throw new IllegalArgumentException("日程为空");
        Set<String> ids=new HashSet<>();
        int weekly=0,once=0;
        for(Entry e:list){
            if(e==null||!validId(e.id)||!ids.add(e.id))throw new IllegalArgumentException("日程条目编号无效");
            String text=e.date==null?"":e.date.trim();
            if(!text.isEmpty()&&dateOf(text)==null)throw new IllegalArgumentException("日期无效，请按 2026-09-30 的格式填写");
            if(!text.isEmpty()){
                // A date and a weekday mask would be two sources of truth for the same question.
                if(e.days!=0)throw new IllegalArgumentException("指定日期的安排不再选择重复星期");
                once++;
            }else{
                if(e.days<1||e.days>127)throw new IllegalArgumentException("请为每条安排选择星期");
                weekly++;
            }
            if(e.start<0||e.start>1439)throw new IllegalArgumentException("请为每条安排选择开始时间");
            if(e.end>1439||(e.end<0&&e.end!=-1))throw new IllegalArgumentException("结束时间无效");
            if(!offersLead(e.lead))throw new IllegalArgumentException("提前提醒的选项无效");
            if(e.note!=null&&e.note.length()>MAX_NOTE)throw new IllegalArgumentException("备注最多 "+MAX_NOTE+" 个字");
        }
        if(weekly>MAX_PER_ANCHOR)throw new IllegalArgumentException("每位主播最多 "+MAX_PER_ANCHOR+" 条每周安排");
        if(once>MAX_ONCE_PER_ANCHOR)throw new IllegalArgumentException("每位主播最多 "+MAX_ONCE_PER_ANCHOR+" 条一次性安排");
        if(list.size()>MAX_TOTAL)throw new IllegalArgumentException("日程最多 "+MAX_TOTAL+" 条");
        return list;
    }

    /** Tolerant repair for data read back from storage; a bad entry is dropped, not fatal. */
    public static List<Entry> sanitize(List<Entry> raw){
        ArrayList<Entry> out=new ArrayList<>();
        if(raw!=null){
            Set<String> ids=new HashSet<>();
            int weekly=0,once=0;
            for(Entry e:raw){
                if(e==null||!validId(e.id)||ids.contains(e.id))continue;
                String date=e.date==null?"":e.date.trim();
                boolean dated=!date.isEmpty();
                if(dated){
                    if(dateOf(date)==null||e.days!=0)continue;
                    if(once>=MAX_ONCE_PER_ANCHOR)continue;
                }else{
                    if(e.days<1||e.days>127)continue;
                    if(weekly>=MAX_PER_ANCHOR)continue;
                }
                if(e.start<0||e.start>1439)continue;
                if(e.end>1439||(e.end<0&&e.end!=-1))continue;
                String note=e.note==null?"":e.note;
                if(note.length()>MAX_NOTE)note=note.substring(0,MAX_NOTE);
                int lead=offersLead(e.lead)?e.lead:0;
                ids.add(e.id);
                out.add(new Entry(e.id,e.days,e.start,e.end,note,date,lead));
                if(dated)once++;else weekly++;
                if(out.size()==MAX_TOTAL)break;
            }
        }
        return out;
    }

    /** weekdayZeroBased: 0=Monday … 6=Sunday, matching the interface's day bit. */
    public static boolean on(Entry e,int weekdayZeroBased){
        return e!=null&&weekdayZeroBased>=0&&weekdayZeroBased<7&&(e.days&(1<<weekdayZeroBased))!=0;
    }
}

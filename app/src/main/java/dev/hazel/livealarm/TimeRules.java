package dev.hazel.livealarm;

import java.time.*;
import java.util.*;

/** Pure scheduling rules. End is exclusive; days belong to the start of a window. */
public final class TimeRules {
    public static final class Window {
        public final String id, name;
        public final int start, end, days;
        public final boolean enabled;
        public Window(String id, String name, int start, int end, int days, boolean enabled) {
            if (start < 0 || start > 1439 || end < 0 || end > 1439 || days < 1 || days > 127)
                throw new IllegalArgumentException("请选择有效的时间和重复日期");
            this.id=id; this.name=name; this.start=start; this.end=end; this.days=days; this.enabled=enabled;
        }
        public boolean on(LocalDate date) { return (days & (1 << (date.getDayOfWeek().getValue()-1))) != 0; }
        public ZonedDateTime begin(LocalDate date, ZoneId zone) { return date.atStartOfDay().plusMinutes(start).atZone(zone); }
        public ZonedDateTime finish(LocalDate date, ZoneId zone) { return date.plusDays(end<=start ? 1 : 0).atStartOfDay().plusMinutes(end).atZone(zone); }
    }
    public static boolean contains(long instant, boolean allDay, List<Window> windows, ZoneId zone) {
        if (allDay) return true;
        LocalDate date=Instant.ofEpochMilli(instant).atZone(zone).toLocalDate();
        for(Window w:windows) if(w.enabled) for(int offset=-1;offset<=0;offset++) {
            LocalDate d=date.plusDays(offset);
            if(w.on(d) && instant>=w.begin(d,zone).toInstant().toEpochMilli() && instant<w.finish(d,zone).toInstant().toEpochMilli()) return true;
        }
        return false;
    }
    public static long nextBoundary(long now, boolean allDay, List<Window> windows, ZoneId zone) {
        if(allDay) return 0;
        long best=Long.MAX_VALUE;
        LocalDate date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for(Window w:windows) if(w.enabled) for(int offset=-1;offset<=8;offset++) {
            LocalDate d=date.plusDays(offset); if(!w.on(d)) continue;
            long a=w.begin(d,zone).toInstant().toEpochMilli(), b=w.finish(d,zone).toInstant().toEpochMilli();
            if(a>now) best=Math.min(best,a); if(b>now) best=Math.min(best,b);
        }
        return best==Long.MAX_VALUE ? 0 : best;
    }
    public static ZoneId zone(String id) { return "device".equals(id) ? ZoneId.systemDefault() : ZoneId.of(id); }
}

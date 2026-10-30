import dev.hazel.livealarm.StreamHours;

import java.time.ZoneId;

/** Learning the busy hours from recorded stream starts: the histogram and the proposal on top. */
public final class StreamHoursTests {
    private static int count;
    private static void ok(boolean yes,String label){count++;if(!yes)throw new AssertionError(label);}
    private static final ZoneId UTC=ZoneId.of("UTC");

    /** A start at the given local hour/minute on an arbitrary day, in UTC. */
    private static long at(int hour,int minute){
        return java.time.LocalDate.of(2026,9,7).atStartOfDay(UTC).toInstant().toEpochMilli()
            +(hour*60L+minute)*60000L;
    }
    private static String text(int[][] windows){
        StringBuilder out=new StringBuilder();
        for(int[] w:windows)out.append(w[0]).append("-").append(w[1]).append(" ");
        return out.toString().trim();
    }

    public static void main(String[] args){
        // The histogram is a plain count of half hours, and it ignores junk.
        long[] starts={at(20,0),at(20,29),at(20,30),at(23,59),at(0,5),0,-5};
        int[] counts=StreamHours.histogram(starts,UTC);
        ok(counts[40]==2,"20:00 and 20:29 land in the same half hour");
        ok(counts[41]==1,"20:30 starts the next half hour");
        ok(counts[47]==1&&counts[0]==1,"23:59 and 00:05 are on opposite ends of the day, not merged");
        int total=0;for(int c:counts)total+=c;
        ok(total==5,"only usable timestamps are counted");
        ok(StreamHours.histogram(null,UTC).length==StreamHours.BUCKETS,"a missing sample still yields the full histogram");

        // Too little history says nothing at all — better than inventing hours from three streams.
        ok(StreamHours.suggest(counts,3)==null,"under the sample floor there is no proposal");
        ok(StreamHours.suggest(null,100)==null,"a missing histogram proposes nothing");
        ok(StreamHours.suggest(new int[10],100)==null,"a malformed histogram proposes nothing");

        // A clear evening pattern becomes one whole-hour window that covers it.
        long[] evenings=new long[20];
        for(int i=0;i<20;i++)evenings[i]=at(20+(i%3),i%2==0?5:35);
        int[] eveningCounts=StreamHours.histogram(evenings,UTC);
        int[][] one=StreamHours.suggest(eveningCounts,20);
        ok(one!=null&&one.length==1,"one busy stretch becomes one window, got "+text(one));
        ok(one[0][0]==20*60&&one[0][1]==23*60,"20:00-23:00 fits the observed hours exactly, got "+text(one));
        ok(StreamHours.coveragePercent(eveningCounts,20,one)==100,"the window covers every sample");

        // A quiet half hour in the middle does not split one evening in two.
        long[] lull=new long[12];
        for(int i=0;i<6;i++)lull[i]=at(20,i%2==0?0:30);
        for(int i=6;i<12;i++)lull[i]=at(i%2==0?21:22,i%2==0?30:0);   // 21:30 and 22:00, one quiet half hour at 21:00
        int[][] lullWindows=StreamHours.suggest(StreamHours.histogram(lull,UTC),12);
        ok(lullWindows!=null&&lullWindows.length==1,"a half-hour lull stays inside one window, got "+text(lullWindows));

        // Two separate habits stay two windows.
        long[] mixed=new long[16];
        for(int i=0;i<8;i++)mixed[i]=at(9,i%2);
        for(int i=8;i<16;i++)mixed[i]=at(21,i%2);
        int[][] two=StreamHours.suggest(StreamHours.histogram(mixed,UTC),16);
        ok(two!=null&&two.length==2,"morning and evening stay separate, got "+text(two));
        ok(two[0][0]==9*60&&two[0][1]==10*60,"the morning window is 09:00-10:00, got "+text(two));
        ok(two[1][0]==21*60&&two[1][1]==22*60,"the evening window is 21:00-22:00, got "+text(two));

        // More habits than the setting allows: the busiest four survive, in time order.
        long[] scattered=new long[30];
        int[] hours={5,10,13,16,19,22};
        for(int i=0;i<30;i++)scattered[i]=at(hours[i%hours.length],i%2);
        int[][] capped=StreamHours.suggest(StreamHours.histogram(scattered,UTC),30);
        ok(capped!=null&&capped.length==StreamHours.MAX_WINDOWS,"the proposal is capped at four windows, got "+text(capped));
        for(int i=1;i<capped.length;i++)ok(capped[i][0]>capped[i-1][0],"the windows come back in time order, got "+text(capped));

        // Late-night streams: the small hours are their own window, not glued to the evening.
        long[] late=new long[10];
        for(int i=0;i<5;i++)late[i]=at(23,i%2);
        for(int i=5;i<10;i++)late[i]=at(0,i%2);
        int[][] lateWindows=StreamHours.suggest(StreamHours.histogram(late,UTC),10);
        ok(lateWindows!=null&&lateWindows.length==2,"23:00 and 00:00 are separate windows, got "+text(lateWindows));
        ok(StreamHours.coveragePercent(StreamHours.histogram(late,UTC),10,lateWindows)==100,"both cover their own half of the sample");

        // The reported coverage is the honest number, not the target.
        long[] three=new long[10];
        for(int i=0;i<8;i++)three[i]=at(20,i%2);
        three[8]=at(3,0);three[9]=at(15,0);
        int[][] mostly=StreamHours.suggest(StreamHours.histogram(three,UTC),10);
        int percent=StreamHours.coveragePercent(StreamHours.histogram(three,UTC),10,mostly);
        ok(percent>=80&&percent<=100,"the coverage of a good fit is in the eighties or better, got "+percent);
        ok(StreamHours.coveragePercent(StreamHours.histogram(three,UTC),0,mostly)==0,"no samples means no percentage");
        ok(StreamHours.coveragePercent(null,10,mostly)==0,"a missing histogram has no percentage");

        System.out.println("PASS: "+count+" stream-hour assertions");
    }
}

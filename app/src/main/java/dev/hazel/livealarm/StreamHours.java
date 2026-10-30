package dev.hazel.livealarm;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;

/**
 * Learns when the anchors actually go live, so the high-frequency windows can be proposed instead of
 * guessed. Pure Java, and fed by the session starts that are already stored per anchor: nothing extra
 * is collected for this, nothing leaves the device, and the answer is explainable ("these hours cover
 * 82% of the last 30 days").
 */
public final class StreamHours {
    /** Half-hour resolution: finer than anyone reasons about, coarser than noise. */
    public static final int BUCKETS=48;
    /** Below this there is nothing to learn from, and a proposal would be a guess. */
    public static final int MIN_SAMPLES=5;
    /** How much of the observed load a proposal aims to cover. */
    public static final double COVERAGE=0.8;
    /** More than this and "high frequency hours" stops meaning anything. */
    public static final int MAX_WINDOWS=4;

    private StreamHours() {}

    /** How often a stream started in each half hour of the day, in the reminder's zone. */
    public static int[] histogram(long[] starts, ZoneId zone) {
        int[] counts=new int[BUCKETS];
        if(starts==null||zone==null)return counts;
        for(long at:starts){
            if(at<=0)continue;
            java.time.LocalTime time=Instant.ofEpochMilli(at).atZone(zone).toLocalTime();
            int bucket=(time.getHour()*60+time.getMinute())/30;
            if(bucket>=0&&bucket<BUCKETS)counts[bucket]++;
        }
        return counts;
    }

    /**
     * The fewest busy stretches that cover COVERAGE of the samples, as {startMinute,endMinute} pairs
     * on whole hours — the boundaries people actually think in. Null when the sample is too small to
     * say anything, so the caller can admit that instead of inventing hours.
     *
     * A single quiet half hour between two busy ones stays inside the same stretch: a lull in the
     * middle of an evening does not make it two evenings.
     */
    public static int[][] suggest(int[] counts,int total){
        if(counts==null||counts.length!=BUCKETS||total<MIN_SAMPLES)return null;
        int need=(int)Math.ceil(total*COVERAGE);
        Integer[] order=new Integer[BUCKETS];
        for(int i=0;i<BUCKETS;i++)order[i]=i;
        // Busiest first; ties keep the earlier hour, which reads better than an arbitrary pick.
        java.util.Arrays.sort(order,(a,b)->counts[b]!=counts[a]?counts[b]-counts[a]:a-b);
        boolean[] chosen=new boolean[BUCKETS];
        int collected=0;
        for(Integer bucket:order){
            if(collected>=need||counts[bucket]<=0)break;
            chosen[bucket]=true;collected+=counts[bucket];
        }
        ArrayList<int[]> runs=new ArrayList<>();
        ArrayList<Integer> weights=new ArrayList<>();
        int from=-1,last=-1,weight=0;
        for(int i=0;i<BUCKETS;i++){
            if(!chosen[i])continue;
            if(from<0){from=i;last=i;weight=counts[i];continue;}
            if(i-last>2){runs.add(new int[]{from,last});weights.add(weight);from=i;weight=counts[i];}
            else weight+=counts[i];
            last=i;
        }
        if(from>=0){runs.add(new int[]{from,last});weights.add(weight);}
        ArrayList<int[]> hours=new ArrayList<>();
        ArrayList<Integer> hourWeights=new ArrayList<>();
        for(int i=0;i<runs.size();i++){
            int[] run=runs.get(i);
            int startHour=run[0]/2, endHour=run[1]/2+1;
            hours.add(new int[]{startHour*60,Math.min(1440,endHour*60)});
            hourWeights.add(weights.get(i));
        }
        if(hours.size()>MAX_WINDOWS){
            // Keep the busiest stretches and let the rest stay on the slow pace; widening the
            // windows to cover everything would defeat the point of having them.
            Integer[] byWeight=new Integer[hours.size()];
            for(int i=0;i<byWeight.length;i++)byWeight[i]=i;
            java.util.Arrays.sort(byWeight,(a,b)->hourWeights.get(b)-hourWeights.get(a));
            ArrayList<int[]> keep=new ArrayList<>();
            for(int i=0;i<MAX_WINDOWS;i++)keep.add(hours.get(byWeight[i]));
            keep.sort((a,b)->a[0]-b[0]);
            hours=keep;
        }
        return hours.toArray(new int[0][]);
    }

    /** What share of the samples those windows really cover, in percent; 0 when there is nothing. */
    public static int coveragePercent(int[] counts,int total,int[][] windows){
        if(counts==null||counts.length!=BUCKETS||total<=0||windows==null)return 0;
        int covered=0;
        for(int[] window:windows){
            int from=Math.max(0,window[0])/30, to=Math.min(1440,window[1])/30;
            for(int bucket=from;bucket<to&&bucket<BUCKETS;bucket++)covered+=counts[bucket];
        }
        return (int)Math.round(covered*100.0/total);
    }
}

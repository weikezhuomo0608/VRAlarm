package dev.hazel.livealarm;

/** A local-only bond level: how much of this anchor's streaming this device has actually seen. */
public final class Bond {
    public static final class State {
        public final int level, percent;
        public final String title, next;
        public final long points, remain;
        State(int level,int percent,String title,String next,long points,long remain){
            this.level=level;this.percent=percent;this.title=title;this.next=next;this.points=points;this.remain=remain;
        }
    }
    /** Points run 40 per watched session plus one per watched minute. */
    public static final int POINTS_PER_SESSION=40;
    private static final long[] THRESHOLDS={0,200,600,1500,3000,6000};
    private static final String[] TITLES={"初见","边苔桑","常连","御守","神社打杂","小马娘同盟"};

    private Bond(){}

    public static State of(int sessions,long minutes){
        long safeMinutes=Math.max(0,minutes);
        long points=(long)Math.max(0,sessions)*POINTS_PER_SESSION+safeMinutes;
        int index=0;
        for(int i=0;i<THRESHOLDS.length;i++)if(points>=THRESHOLDS[i])index=i;
        boolean top=index==THRESHOLDS.length-1;
        long floor=THRESHOLDS[index];
        long ceiling=top?floor:THRESHOLDS[index+1];
        long span=ceiling-floor;
        int percent=top?100:(int)Math.min(100,(points-floor)*100/span);
        return new State(index+1,percent,TITLES[index],top?"":TITLES[index+1],points,top?0:ceiling-points);
    }
}

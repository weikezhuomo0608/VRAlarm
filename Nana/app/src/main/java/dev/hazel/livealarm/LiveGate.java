package dev.hazel.livealarm;

/** No Android dependencies: persisted broadcast deduplication and first-observation handling. */
public final class LiveGate {
    public static final class State {
        public String session="", notified="", logged="";
        public long started=0, firstSeen=0, offlineSince=0, sourceStart=0;
        public int offlineSamples=0;
        public boolean baseline=true, unknownBaseline=false;
    }
    public interface Schedule { boolean contains(long timestamp); }
    public static final class Decision {
        public final boolean ring;
        public final String reason;
        Decision(boolean ring,String reason){this.ring=ring;this.reason=reason;}
    }
    public static Decision observe(State s, boolean live, long start, long now, long armedAt, boolean catchUp, Schedule schedule) {
        boolean first=s.baseline; s.baseline=false;
        if(!live) {
            if(s.offlineSamples==0) s.offlineSince=now;
            s.offlineSamples=Math.min(2,s.offlineSamples+1);
            return new Decision(false,"offline");
        }
        String key;
        if(start>0) key="time:"+start;
        else if(!s.session.isEmpty() && !(s.offlineSamples>=2 && now-s.offlineSince>=120000)) key=s.session;
        else key="seen:"+now;
        // Recovering metadata or a short confirmed reconnect must not create a second alarm.
        if(start>0 && !s.session.isEmpty()) {
            if(start==s.sourceStart || (s.session.startsWith("seen:") && s.offlineSamples<2)
                || (s.offlineSamples>0 && now-s.offlineSince<120000)) key=s.session;
        }
        if(!key.equals(s.session)) {
            s.session=key; s.started=start; s.firstSeen=now; s.unknownBaseline=first && start<=0;
        } else if(s.started==0 && start>0) s.started=start;
        if(start>0)s.sourceStart=start;
        if(first && start<=0) s.unknownBaseline=true;
        s.offlineSamples=0; s.offlineSince=0;
        if(key.equals(s.notified)) return new Decision(false,"duplicate");
        if(!schedule.contains(now)) return new Decision(false,"outside");
        if(!catchUp) {
            if(s.started>0 && s.started<armedAt-1000) return new Decision(false,"already_live");
            if(s.started==0 && (s.unknownBaseline || s.firstSeen<armedAt)) return new Decision(false,"already_live");
            if(!schedule.contains(s.started>0?s.started:s.firstSeen)) return new Decision(false,"outside_start");
        }
        return new Decision(true, catchUp && (s.started==0 || s.started<armedAt) ? "catch_up" : "live_start");
    }
}

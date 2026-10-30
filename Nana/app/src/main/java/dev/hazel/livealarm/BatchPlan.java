package dev.hazel.livealarm;

import java.util.*;

/**
 * The decision half of the batched live-status poll: how many uids go in one request, and whether the
 * answer about one anchor may be believed. Pure Java and JSON-free on purpose — the parsing stays in
 * BiliApi, so this runs in the headless kernel tests with the other rule classes.
 */
public final class BatchPlan {
    /** Uids per request. The endpoint publishes no limit, so the chunk is deliberately small: nearly
     *  every install watches a handful of anchors, which is exactly one request. */
    public static final int CHUNK=20;

    /** What the batch answer said about one anchor. */
    public enum State { USABLE, MISSING, MISMATCH }

    private BatchPlan(){}

    /** The uid groups of one cycle. Anchors without a usable uid are left out: they cannot be asked
     *  in a batch at all, and their caller sends them down the single-anchor path. */
    public static long[][] chunks(long[] uids){
        ArrayList<Long> usable=new ArrayList<>();
        for(long u:uids)if(u>0)usable.add(u);
        int groups=(usable.size()+CHUNK-1)/CHUNK;
        long[][] out=new long[groups][];
        for(int g=0;g<groups;g++){
            int from=g*CHUNK,to=Math.min(usable.size(),from+CHUNK);
            out[g]=new long[to-from];
            for(int i=from;i<to;i++)out[g][i-from]=usable.get(i);
        }
        return out;
    }

    /**
     * Whether a batch row may stand in for one anchor's own check. A missing row means the endpoint
     * simply did not answer for that uid — which is NOT the same as "not live", and reading it that
     * way would look like the stream had ended. A row naming a different room cannot be attributed to
     * this anchor either. Both are answered by asking for that one anchor separately.
     */
    public static State judge(boolean present,long rowRoom,long wantRoom){
        if(!present)return State.MISSING;
        if(wantRoom<=0||rowRoom!=wantRoom)return State.MISMATCH;
        return State.USABLE;
    }
}

import dev.hazel.livealarm.BatchPlan;

/** The batched live-status poll: how a cycle is split into requests, and when one answer may be
 *  trusted for one anchor. The endpoint's own quirk — a uid it does not know is simply absent — is
 *  what most of these assertions are about. */
public final class BatchPlanTests {
    private static int count;
    private static void ok(boolean yes,String label){count++;if(!yes)throw new AssertionError(label);}

    public static void main(String[] args){
        ok(BatchPlan.chunks(new long[]{}).length==0,"an empty roster makes no request");
        ok(BatchPlan.chunks(new long[]{7}).length==1,"one anchor is one chunk");
        ok(BatchPlan.chunks(new long[]{7})[0].length==1,"and the chunk holds it");
        ok(BatchPlan.chunks(new long[]{0,7})[0].length==1,"an anchor without a uid is left out of the batch");
        ok(BatchPlan.chunks(new long[]{-5}).length==0,"a negative uid is not askable either");

        long[] twenty=new long[20];for(int i=0;i<20;i++)twenty[i]=i+1;
        ok(BatchPlan.chunks(twenty).length==1,"exactly the chunk size still fits one request");
        long[] twentyOne=new long[21];for(int i=0;i<21;i++)twentyOne[i]=i+1;
        ok(BatchPlan.chunks(twentyOne).length==2,"one more opens a second chunk");
        ok(BatchPlan.chunks(twentyOne)[1].length==1,"and the second chunk holds the remainder");
        ok(BatchPlan.chunks(twentyOne)[0][0]==1&&BatchPlan.chunks(twentyOne)[1][0]==21,"the order is kept");

        // The endpoint omits a uid it does not know, so "no row" means unknown, never offline.
        ok(BatchPlan.judge(false,0,1713546334)==BatchPlan.State.MISSING,"a missing row is unknown");
        ok(BatchPlan.judge(true,1713546334,1713546334)==BatchPlan.State.USABLE,"a matching room may be used");
        ok(BatchPlan.judge(true,999,1713546334)==BatchPlan.State.MISMATCH,"another room cannot be attributed here");
        ok(BatchPlan.judge(true,1713546334,0)==BatchPlan.State.MISMATCH,"without a stored room there is nothing to check");

        System.out.println("PASS: "+count+" batch-poll assertions");
    }
}

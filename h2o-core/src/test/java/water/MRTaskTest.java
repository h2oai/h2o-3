package water;

import org.junit.*;
import water.fvec.*;
import water.util.PrettyPrint;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MRTaskTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(5); }

  // test we reduce asap and do not produce more than tree_depth + P unreduced results
  @Test public void test_reductions(){
    for(int k = 0; k < 3; ++k) {
      Key[] keys = new Key[2048]; // should have at most 10 + H2O.NUMCPUS active results
      int depth = 11;
      int log = 1;
      while((1 << log) <= H2O.NUMCPUS)log++;
      log--;
      // maximum number of unreduced results given intended reduce strategy.
      // (it is exact only if NUMCPUS is power of 2, otherwise it's an upper bound.)
      // The max number of unreduced results it's depth of the tree per-thread
      // (Some levels of the tree are used on launching the threads - hence it's (log2(numtasks) - log2(numcpus)*numcpus.
      int max_unreduced_elems = (depth - log + 1)*H2O.NUMCPUS;
      for (int i = 0; i < keys.length; ++i)
        keys[i] = Key.make((byte) 1, Key.HIDDEN_USER_KEY, true, H2O.SELF);
      final AtomicInteger cntr = new AtomicInteger();
      final AtomicInteger maxCntr = new AtomicInteger();
      new MRTask() {
        public void map(Key k) {
          int cnt = cntr.incrementAndGet();
          int max = maxCntr.get();
          while (cnt > max) {
            maxCntr.compareAndSet(max, cnt);
            max = maxCntr.get();
          }
          try {
            Thread.sleep(1);
          } catch (InterruptedException e) {
          }
        }

        public void reduce(MRTask t) {
          cntr.decrementAndGet();
        }
      }.doAll(keys);
      int max_cnt = maxCntr.get();
      System.out.println("max cnt = " + max_cnt);
      int cnt = cntr.get();
      assertEquals("Number of reductions should be (numtasks - 1). We add 1 per map, subtract one per reduce, there should be 1 left, got " + cnt,1,cnt);
      assertTrue("too many unreduced results, should be <= " + max_unreduced_elems + " but was " + max_cnt, max_cnt <= max_unreduced_elems);
    }
  }
  // Test speed of calling 1M map calls
  @Test
  public void testMillionMaps() {
    // 
    final int iters = 20;
    // Brutal 1-row-per-chunk, forced to 1M chunks
    //final long nchunks = 1000000L;
    final long nchunks = 100000L; // Cheapen for daily use
    Vec zeros = Vec.makeCon(0.0,nchunks,0,true);
    // Warmup: 3 untimed iterations
    manyMaps(zeros);
    manyMaps(zeros);
    manyMaps(zeros);

    long sum=0, ssq=0;

    // Time a few iterations
    MRTask mrt = null;
    long start = System.currentTimeMillis();
    for( int i=0; i<iters; i++ ) {
      mrt = manyMaps(zeros);
      long now = System.currentTimeMillis();
      long deltams = now-start;
      //System.out.println("Time in ms for "+nchunks+" maps:"+PrettyPrint.msecs(deltams,false)+", "+(deltams*1e6/nchunks)+"ns/map");
      sum += deltams;
      ssq += deltams*deltams;
      start = now;
    }
    // uncomment to see the slowest-path through the MRTask tree.
    //System.out.println(mrt.profString());

    double avg = (double)sum/iters;
    // var = mean of squares - square of means
    double stddev = Math.sqrt((double)ssq/iters - avg*avg);
    double pct_stddev = stddev/avg;
    System.out.println("Avg map call: "+avg*1e6/nchunks+"ns/map, stddev for "+nchunks+" maps: +/-"+PrettyPrint.formatPct(pct_stddev));

    zeros.remove();
  }
  private static MRTask manyMaps(Vec vec) {
    return new MRTask() { 
      @Override public void map(Chunk cs[]) { }
    }.profile().doAll(vec);
  }
}


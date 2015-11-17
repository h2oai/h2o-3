package water;

import org.junit.*;
import water.fvec.Vec;
import water.fvec.Chunk;
import water.util.PrettyPrint;

public class MRTaskTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(5); }

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


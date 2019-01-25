package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import java.util.Random;
import water.Futures;
import water.TestUtil;
import water.Key;

public class VecStatsTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }
  @Test public void test() {
    Frame frame = null;
    try {
      Futures fs = new Futures();
      Random random = new Random();
      Vec[] vecs = new Vec[1];
      AppendableVec vec = new AppendableVec(Vec.newKey(), Vec.T_NUM);
      for( int i = 0; i < 2; i++ ) {
        NewChunk chunk = new NewChunk(vec, i);
        for( int r = 0; r < 1000; r++ )
          chunk.addNum(random.nextInt(1000));
        chunk.close(i, fs);
      }
      vecs[0] = vec.layout_and_close(fs);
      fs.blockForPending();
      frame = new Frame(Key.<Frame>make(), null, vecs);

      // Make sure we test the multi-chunk case
      vecs = frame.vecs();
      assert vecs[0].nChunks() > 1;
      long rows = frame.numRows();
      Vec v = vecs[0];
      double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, mean = 0, sigma = 0;
      for( int r = 0; r < rows; r++ ) {
        double d = v.at(r);
        if( d < min ) min = d;
        if( d > max ) max = d;
        mean += d;
      }
      mean /= rows;
      for( int r = 0; r < rows; r++ ) {
        double d = v.at(r);
        sigma += (d - mean) * (d - mean);
      }
      sigma = Math.sqrt(sigma / (rows - 1));

      double epsilon = 1e-9;
      assertEquals(max, v.max(), epsilon);
      assertEquals(min, v.min(), epsilon);
      assertEquals(mean, v.mean(), epsilon);
      assertEquals(sigma, v.sigma(), epsilon);
    } finally {
      if( frame != null ) frame.delete();
    }
  }

  @Test public void testPCTiles() {
    // Simplified version of tests in runit_quantile_1_golden.R. There we test probs=seq(0,1,by=0.01)
    Vec vec = vec(5 , 8 ,  9 , 12 , 13 , 16 , 18 , 23 , 27 , 28 , 30 , 31 , 33 , 34 , 43,  45,  48, 161);
    double[] pctiles = vec.pctiles();
    //System.out.println(java.util.Arrays.toString(pctiles));
    Assert.assertEquals(13.75,pctiles[4],1e-5);
    vec.remove();

    vec = vec(5 , 8 ,  9 , 9 , 9 , 16 , 18 , 23 , 27 , 28 , 30 , 31 , 31 , 34 , 43,  43,  43, 161);
    pctiles = vec.pctiles();
    //System.out.println(java.util.Arrays.toString(pctiles));
    Assert.assertEquals(10.75,pctiles[4],1e-5);
    vec.remove();

  }
}

package water.fvec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Futures;
import water.TestUtil;

/** Test for CBSChunk implementation.
 *
 * The objective of the test is to verify compression method, not the H2O environment.
 *
 * NOTE: The test is attempt to not require H2O infrastructure to run.
 * It tries to use Mockito (perhaps PowerMock in the future) to wrap
 * expected results. In this case expectation is little bit missused
 * since it is used to avoid DKV call.
 * */
public class CBSChunkTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  void testImpl(long[] ls, int[] xs, int expBpv, int expGap, int expClen, int expNA) {
    AppendableVec av = new AppendableVec(Vec.newKey());
    Futures fs = new Futures();
    Vec vv = av.close(fs);
    // Create a new chunk
    NewChunk nc = new NewChunk(av,0);
    nc._ls = ls;
    nc._xs = xs;
    nc._len = nc._len2 = ls.length;
    nc.type();                  // Compute rollups, including NA
    assertEquals(expNA, nc._naCnt);
    // Compress chunk
    Chunk cc = nc.compress();
    assert cc instanceof CBSChunk;
    cc._vec = av.close(new Futures());
    assertTrue( "Found chunk class "+cc.getClass()+" but expected " + CBSChunk.class, CBSChunk.class.isInstance(cc) );
    assertEquals(nc._len, cc._len);
    assertEquals(expGap, ((CBSChunk)cc).gap());
    assertEquals(expBpv, ((CBSChunk)cc).bpv());
    assertEquals(expClen, cc._mem.length - CBSChunk._OFF);
    // Also, we can decompress correctly
    for( int i=0; i<ls.length; i++ )
      if(xs[i]==0)assertEquals(ls[i], cc.at80(i));
      else assertTrue(cc.isNA0(i));
    vv.remove();
  }

  // Test one bit per value compression which is used
  // for data without NAs
  @Test public void test1BPV() {
    // Simple case only compressing into 4bits of one byte
    testImpl(new long[] {0,0,0,1},
             new int [] {0,0,0,0},
             1, 4, 1, 0);
    // Filling whole byte
    testImpl(new long[] {1,0,0,0,1,1,1,0},
             new int [] {0,0,0,0,0,0,0,0},
             1, 0, 1, 0);
    // Crossing the border of two bytes by 1bit
    testImpl(new long[] {1,0,0,0,1,1,1,0, 1},
             new int [] {0,0,0,0,0,0,0,0, 0},
             1, 7, 2, 0);
  }

  // Test two bits per value compression used for case with NAs
  // used for data containing NAs
  @Test public void test2BPV() {
   // Simple case only compressing 2*3bits into 1byte including 1 NA
   testImpl(new long[] {0,Long.MAX_VALUE,                  1},
            new int [] {0,Integer.MIN_VALUE,0},
            2, 2, 1, 1);
   // Filling whole byte, one NA
   testImpl(new long[] {1,Long.MAX_VALUE                ,0,1},
            new int [] {0,Integer.MIN_VALUE,0,0},
            2, 0, 1, 1);
   // crossing the border of two bytes by 4bits, one NA
   testImpl(new long[] {1,0,Long.MAX_VALUE,                1, 0,0},
            new int [] {0,0,Integer.MIN_VALUE,0, 0,0},
            2, 4, 2, 1);
   // Two full bytes, 5 NAs
   testImpl(new long[] {Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,1, 0,Long.MAX_VALUE,1,Long.MAX_VALUE},
            new int [] {Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,0, 0,Integer.MIN_VALUE,0,Integer.MIN_VALUE},
            2, 0, 2, 5);
  }
}

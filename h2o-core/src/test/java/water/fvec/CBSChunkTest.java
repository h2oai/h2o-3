package water.fvec;

import static org.testng.Assert.*;
import org.testng.AssertJUnit;
import org.testng.annotations.*;

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
  void testImpl(long[] ls, int[] xs, int expBpv, int expGap, int expClen, int expNA) {
    AppendableVec av = new AppendableVec(Vec.newKey());
    // Create a new chunk
    NewChunk nc = new NewChunk(av,0, ls, xs, null, null);
    nc.set_len(nc.set_len2(ls.length));
    nc.type();                  // Compute rollups, including NA
    assertEquals(expNA, nc.naCnt());
    // Compress chunk
    Chunk cc = nc.compress();
    assert cc instanceof CBSChunk;
    cc._vec = av.close(new Futures());
    AssertJUnit.assertTrue( "Found chunk class "+cc.getClass()+" but expected " + CBSChunk.class, CBSChunk.class.isInstance(cc) );
    assertEquals(nc.len(), cc.len());
    assertEquals(expBpv, ((CBSChunk)cc).bpv());
    assertEquals(expGap, ((CBSChunk)cc).gap());
    assertEquals(expClen, cc._mem.length - CBSChunk._OFF);
    // Also, we can decompress correctly
    for( int i=0; i<ls.length; i++ )
      if(xs[i]==0)assertEquals(ls[i], cc.at80(i));
      else assertTrue(cc.isNA0(i));

    // materialize the vector (prerequisite to free the memory)
    Futures fs = new Futures();
    Vec vv = av.close(fs);
    fs.blockForPending();
    vv.remove();
  }

  // Test one bit per value compression which is used
  // for data without NAs
  @Test public void test1BPV() {
    // Simple case only compressing into 4bits of one byte
    testImpl(new long[] {1,0,1,1},
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

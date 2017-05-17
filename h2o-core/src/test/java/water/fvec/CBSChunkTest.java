package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Futures;
import water.Scope;
import water.TestUtil;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  void testImpl(long[] ls, int[] xs, int expBpv, int expGap, int expClen, int expNA) {
    AppendableVec av = new AppendableVec(Vec.newKey(), Vec.T_NUM);
    // Create a new chunk
    NewChunk nc = new NewChunk(av,0, ls, xs, null, null);
    for(int i = 0; i < ls.length; ++i)
      if(ls[i] == Long.MIN_VALUE)
        nc.setNA_impl(i);
    nc.type();                  // Compute rollups, including NA
    assertEquals(expNA, nc.naCnt());
    // Compress chunk
    Chunk cc = nc.compress();
    assert cc instanceof CBSChunk;
    Futures fs = new Futures();
    cc._vec = av.layout_and_close(fs);
    fs.blockForPending();
    Assert.assertTrue("Found chunk class " + cc.getClass() + " but expected " + CBSChunk.class, CBSChunk.class.isInstance(cc));
    assertEquals(nc._len, cc._len);
    assertEquals(expBpv, ((CBSChunk)cc).bpv());
    assertEquals(expGap, ((CBSChunk)cc).gap());
    assertEquals(expClen, cc._mem.length - CBSChunk._OFF);
    // Also, we can decompress correctly
    for( int i=0; i<ls.length; i++ )
      if(ls[i]!=Long.MIN_VALUE)assertEquals(ls[i], cc.at8(i));
      else assertTrue(cc.isNA(i));

    // materialize the vector (prerequisite to free the memory)
    Vec vv = av.layout_and_close(fs);
    fs.blockForPending();
    vv.remove();
  }


  @Test
  public void testSet(){
    Scope.enter();
    // with NAs
    double [] x = new double[]{0,1,Double.NaN};
    double [] vals = new double[1024];
    Random rnd = new Random(54321);
    for(int i = 0; i < vals.length; ++i)
      vals[i] = x[rnd.nextInt(3)];

    Chunk c = Vec.makeVec(vals, Vec.VectorGroup.VG_LEN1.addVec()).chunkForChunkIdx(0);
    Chunk c2 = c.deepCopy();
    c2._vec = c._vec;
    assertTrue(c instanceof CBSChunk);
    for(int i = 0; i < vals.length; ++i) {
      assertEquals(vals[i], c.atd(i), 0);
      assertEquals(vals[i], c2.atd(i), 0);
    }
    for(int i = 0; i < vals.length; ++i) {
      c.set(i, vals[i] = x[rnd.nextInt(3)]);
      if(Double.isNaN(vals[i]))c2.setNA_impl(i); else c2.set(i, (long)vals[i]);
    }
    for(int i = 0; i < vals.length; ++i) {
      assertEquals(vals[i], c.atd(i), 0);
      assertEquals(vals[i], c2.atd(i), 0);
    }
    // without NAS
    for(int i = 0; i < vals.length; ++i)
      vals[i] = x[rnd.nextInt(2)];
    c = Vec.makeVec(vals, Vec.VectorGroup.VG_LEN1.addVec()).chunkForChunkIdx(0);
    c2 = c.deepCopy();
    c2._vec = c._vec;
    assertTrue(c instanceof CBSChunk);
    for(int i = 0; i < vals.length; ++i)
      assertEquals(vals[i],c.atd(i),0);
    for(int i = 0; i < vals.length; ++i) {
      c.set(i, vals[i] = x[rnd.nextInt(2)]);
      c2.set(i, (long)vals[i]);
    }
    for(int i = 0; i < vals.length; ++i) {
      assertEquals(vals[i], c.atd(i), 0);
      assertEquals(vals[i], c2.at8(i), 0);
    }
    // set some NAs
    int i = vals.length >> 2;
    int j = vals.length >> 1;
    c.setNA(i);
    c.set(j,Double.NaN);
    vals[j] = Double.NaN;
    vals[i] = Double.NaN;
    Assert.assertTrue(c.isNA(i));
    Assert.assertTrue(c.isNA(j));
    for(int k = 0; k < vals.length; ++k) {
      assertEquals(vals[k], c.atd(k), 0);
    }
    Scope.exit();
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
   testImpl(new long[] {0,Long.MIN_VALUE,                  1},
            new int [] {0,0,0},
            2, 2, 1, 1);
   // Filling whole byte, one NA
   testImpl(new long[] {1,Long.MIN_VALUE                ,0,1},
            new int [] {0,0,0,0},
            2, 0, 1, 1);
   // crossing the border of two bytes by 4bits, one NA
   testImpl(new long[] {1,0,Long.MIN_VALUE,                1, 0,0},
            new int [] {0,0,0,0, 0,0},
            2, 4, 2, 1);
   // Two full bytes, 5 NAs
   testImpl(new long[] {Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,1, 0,Long.MIN_VALUE,1,Long.MIN_VALUE},
            new int [] {0,0,0,0, 0,0,0,0},
            2, 0, 2, 5);
  }
  @Test public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      int[] vals = new int[]{0, 1, 0, 1, 0, 0, 1};
      if (l==1) nc.addNA();
      for (int v : vals) nc.addNum(v);
      nc.addNA();
      int len = nc.len();
      Chunk cc = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof CBSChunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(l + i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8_abs(l + i));
      Assert.assertTrue(cc.isNA(vals.length + l));
      Assert.assertTrue(cc.isNA_abs(vals.length + l));

      nc = new NewChunk(null, 0);
      cc.extractRows(nc, 0,len);
      Assert.assertEquals(vals.length+l+1, nc._sparseLen);
      Assert.assertEquals(vals.length+l+1, nc._len);

      if (l==1) {
        Assert.assertTrue(nc.isNA(0));
        Assert.assertTrue(nc.isNA_abs(0));
      }
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8(l + i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8_abs(l + i));
      Assert.assertTrue(nc.isNA(vals.length + l));
      Assert.assertTrue(nc.isNA_abs(vals.length + l));
      double[] densevals = new double[cc.len()];
      cc.getDoubles(densevals,0,cc.len());
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(cc.at8(i)==(int)densevals[i]);
      }

      Chunk cc2 = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc2 instanceof CBSChunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(l + i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8_abs(l + i));
      Assert.assertTrue(cc2.isNA(vals.length + l));
      Assert.assertTrue(cc2.isNA_abs(vals.length + l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    water.Key key = Vec.newKey();
    Vec vec = new Vec(key, Vec.ESPC.rowLayout(key,new long[]{0,15})).makeZero();
    int[] vals = new int[]{0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1};
    Vec.Writer w = vec.open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    Chunk cc = vec.chunkForChunkIdx(0);
    assert cc instanceof CBSChunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8_abs(i));

    int[] NAs = new int[]{1, 5, 2};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    for (int na : NAs) cc.setNA_abs(na);

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA_abs(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA_abs(notna));

    NewChunk nc = new NewChunk(null, 0);
    cc.extractRows(nc, 0,(int)vec.length());
    Assert.assertEquals(vals.length, nc._sparseLen);
    Assert.assertEquals(vals.length, nc._len);

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA_abs(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA_abs(notna));

    Chunk cc2 = nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc2 instanceof CBSChunk);
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA_abs(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA_abs(notna));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    vec.remove();
  }

  @Test public void testSparseAndVisitorInterface(){
    double [] vals = new double[1024];
    double [] valsNA = new double[1024];
    TreeSet<Integer> nzs = new TreeSet<>();
    Random rnd = new Random(54321);
    for(int i = 0; i < 512; i++) {
      int x = rnd.nextInt(vals.length);
      if(nzs.add(x)) {
        vals[x] = 1;
        valsNA[x] =   rnd.nextDouble() < .95?1:Double.NaN;
      }
    }
    int [] nzs_ary = new int[nzs.size()];
    int k = 0;
    for(Integer i:nzs)
      nzs_ary[k++] = i;
    SparseTest.makeAndTestSparseChunk(CBSChunk.class,vals,nzs_ary,false,false);
    SparseTest.makeAndTestSparseChunk(CBSChunk.class,valsNA,nzs_ary,false,false);
  }

}

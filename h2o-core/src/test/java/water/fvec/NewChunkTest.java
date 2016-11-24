package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Futures;
import water.TestUtil;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class NewChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  final int K = 1 + (int)(new Random().nextFloat() * (FileVec.DFLT_CHUNK_SIZE >> 4));
  private AppendableVec av;
  NewChunk nc;
  Chunk cc;
  Vec vec;

  private void pre() {
    av = new AppendableVec(Vec.newKey(), Vec.T_NUM);
    nc = new NewChunk(av, 0);
  }
  private void post() {
    cc = nc.compress();
    av._tmp_espc[0] = K; //HACK
    cc._start = 0; //HACK
    cc._cidx = 0; // HACK as well
    Futures fs = new Futures();
    vec = cc._vec = av.layout_and_close(fs);
    fs.blockForPending();
    assert(DKV.get(vec._key)!=null); //only the vec header is in DKV, the chunk is not
  }
  private void post_write() {
    cc.close(0, new Futures()).blockForPending();
  }
  void remove() {if(vec != null)vec.remove();}

  @Test public void testSparseDoubles(){
    NewChunk nc = new NewChunk(new double[]{Math.PI});
    int N = 1000;
    nc.addZeros(N);
    nc.addNum(Math.PI);
    Chunk c = nc.compress();
    assertEquals(Math.PI,c.atd(0),1e-16);
    for(int i = 1; i <= N; ++i)
      assertEquals(0,c.atd(i),1e-16);
    assertEquals(Math.PI,c.atd(N+1),1e-16);
  }

  @Test public void testSparseNAs() {
    NewChunk nc = new NewChunk(null, 0, true);
    nc.addNAs(128);
    assertTrue(nc.isSparseNA());
    for (int i = 0; i < 512; i++)
      nc.addUUID(i, i/2+i/3);
    assertFalse(nc.isSparseNA());
    Chunk c = nc.compress();
    assertEquals(128 + 512, c.len());
    for (int i = 0; i < 128; ++i)
      assertTrue("Expected a NA at " + i, c.isNA(i));
    for (int i = 0; i < 512; i++) {
      assertEquals(i, c.at16l(128 + i));
      assertEquals(i/2+i/3, c.at16h(128 + i));
    }
  }

  private static class NewChunkTestCpy extends NewChunk {
    NewChunkTestCpy(Vec vec, int cidx) {super(vec, cidx);}
    public NewChunkTestCpy() { super(null,0); }
    int mantissaSize() {return _ms._vals1 != null?1:_ms._vals4 != null?4:8;}
    int exponentSize() {return _xs._vals1 != null?1:_xs._vals4 != null?4:0;}
    int missingSize()  {return _missing == null?0:_missing.size();}
  }


  private void testIntegerChunk(long [] values, int mantissaSize) {
    Vec v = Vec.makeCon(0,0);
    // single bytes
    Chunk c;

    NewChunkTestCpy nc = new NewChunkTestCpy(v,4);
    for( long val : values )
      nc.addNum(val, 0);
    assertEquals(mantissaSize,nc.mantissaSize());
    assertEquals(0,nc.exponentSize());
    assertEquals(0,nc.missingSize());
    for(int i = 0; i < values.length; ++i)
      assertEquals(values[i],nc.at8_impl(i));
    for(int i = 0; i < values.length; i += 5)
      nc.setNA_impl(i);
    c = nc.compress();
    for(int i = 0; i < values.length; ++i) {
      if(i % 5 == 0)
        assertTrue(c.isNA(i));
      else
        assertEquals(values[i], c.at8_impl(i));
    }

    // test with exponent
    nc = new NewChunkTestCpy(v,4);
    for( long val : values )
      nc.addNum(val, -1);
    for(int i = 0; i < values.length; i += 5)
      nc.setNA_impl(i);
    assertEquals(1,nc.exponentSize());
    c = nc.compress();
    for(int i = 0; i < values.length; ++i) {
      if(i % 5 == 0)
        assertTrue(c.isNA(i));
      else
        assertEquals(values[i]*0.1, c.atd(i),1e-10);
    }
    // test switch to doubles
    nc = new NewChunkTestCpy(v,4);
    for( long val : values )
      nc.addNum(val, 0);
    for(int i = 0; i < values.length; i += 5)
      nc.setNA_impl(i);
    nc.addNum(Math.PI);
    c = nc.compress();
    for(int i = 0; i < values.length; ++i) {
      if(i % 5 == 0)
        assertTrue(c.isNA(i));
      else
        assertEquals(values[i], c.at8_impl(i));
    }
    assertEquals(Math.PI,c.atd(values.length),0);
    // test switch to sparse zero
    nc = new NewChunkTestCpy(v,4);
    for( long val : values )
      nc.addNum(val, 0);
    nc.addNA();
    nc.setNA_impl(0);
    int nzs = 0;
    for(int i = 1; i < values.length; i++) {
      if (i % 10 != 0) { nc.set_impl(i, 0); nzs++;}
    }
    int x = (nzs*8+1) - nc.len();
    if(x > 0)nc.addZeros(x);
    c = nc.compress();
    assertTrue(c.isSparseZero());
    assertTrue(c.isNA(0));
    assertTrue(c.isNA(values.length));
    for(int i = 10; i < values.length; i++)
      if(i % 10 == 0)
        assertEquals(values[i],c.atd(i),0);
      else
        assertEquals(0,c.atd(i),0);
    // test switch to sparse NAs
    nc = new NewChunkTestCpy(v,4);
    for( long val : values )
      nc.addNum(val, 0);
    nc.addNA();
    nc.setNA_impl(0);
    nzs = 0;
    for(int i = 1; i < values.length; i++) {
      if (i % 10 != 0) { nc.setNA_impl(i); nzs++;}
    }
    x = (nzs*8+1) - nc.len();
    if(x > 0)nc.addNAs(x);
    c = nc.compress();
    assertTrue(c.isSparseNA());
    assertTrue(c.isNA(0));
    assertTrue(c.isNA(values.length));
    for(int i = 10; i < values.length; i++)
      if(i % 10 == 0)
        assertEquals(values[i],c.atd(i),0);
      else
        assertTrue(c.isNA(i));
    v.remove();

    // Test dense -> sparse -> dense flip


  }
  private long [] ms1 = new long[] {-128,-64,-32,-16,-8,-4,-2,0,1,3,7,15,31,63,127};
  private long [] ms4 = new long[] {-128,-64,-32,-16,-8,-4,-2,0,1,3,7,15,31,63,127,255,511,1023};
  private long [] ms8 = new long[] {-128,-64,-32,-16,-8,-4,-2,0,1,3,7,15,31,63,127,255,511,1023,Long.MAX_VALUE >> 16};

  @Test public void testDenseMantissaSizes(){
    testIntegerChunk(ms1,1);
    testIntegerChunk(ms4,4);
    testIntegerChunk(ms8,8);
  }


  @Test public void testSparseDoubles2(){
    NewChunk nc = new NewChunk(null, 0, false);
    int N = 1000;
    nc.addZeros(N);
    nc.addNum(Math.PI);
    nc.addZeros(N);
    nc.addNum(Math.E);
    Chunk c = nc.compress();
    int i=0;
    for(;i<N;)     assertEquals(0,c.atd(i++),1e-16);
    assertEquals(i,c.nextNZ(-1));
    assertEquals(         Math.PI,c.atd(i++),1e-16);
    for(;i<2*N+1;) assertEquals(0,c.atd(i++),1e-16);
    assertEquals(i,c.nextNZ(c.nextNZ(-1)));
    assertEquals(         Math.E,c.atd(i  ),1e-16);

    nc = new NewChunk(null, 0, false);
    nc.addNum(Math.PI);
    nc.addNum(Double.MAX_VALUE);
    nc.addNum(Double.MIN_VALUE);
    nc.addZeros(5);
    nc.addNum(Math.E);
    nc.addZeros(1000000);
    c = nc.compress();
    assertEquals(0,c.nextNZ(-1));
    assertEquals(1,c.nextNZ(0));
    assertEquals(2,c.nextNZ(1));
    assertEquals(8,c.nextNZ(2));
    assertEquals(c.atd(0), Math.PI,1e-16);
    assertEquals(c.atd(8), Math.E,1e-16);

    // test flip from dense -> sparse0 -> desne
    nc = new NewChunk(null, 0, false);
    double [] rvals = new double[2*1024];
    nc.addNum(rvals[0] = Math.PI);
    nc.addNum(rvals[1] = Double.MAX_VALUE);
    nc.addNum(rvals[2] = Double.MIN_VALUE);
    nc.addZeros(5);
    nc.addNum(rvals[2+1+5] = Math.E);
    nc.addZeros(512);
    int off = nc._len;
    assertTrue(nc.isSparseZero());
    Random rnd = new Random();
    for(int j  = 0; j < 1024; ++j)
      nc.addNum(rvals[off+j] = rnd.nextDouble());
    assertTrue(!nc.isSparseZero());
    nc.addNA();
    c = nc.compress();
    assertEquals(1546,c._len);
    for(int j = 0; j < c._len-1; ++j)
      assertEquals(rvals[j],c.atd(j),0);

    // test flip from dense -> sparseNA -> desne
    nc = new NewChunk(null, 0, false);
    rvals = new double[2*1024];
    Arrays.fill(rvals,Double.NaN);
    nc.addNum(rvals[0] = Math.PI);
    nc.addNum(rvals[1] = Double.MAX_VALUE);
    nc.addNum(rvals[2] = Double.MIN_VALUE);
    nc.addNAs(5);
    nc.addNum(rvals[2+1+5] = Math.E);
    nc.addNAs(512);
    off = nc._len;
    assertTrue(nc.isSparseNA());
    for(int j  = 0; j < 1024; ++j)
      nc.addNum(rvals[off+j] = rnd.nextDouble());
    assertTrue(!nc.isSparseNA());
    nc.addNA();
    c = nc.compress();
    assertEquals(1546,c._len);
    for(int j = 0; j < c._len-1; ++j)
      if(Double.isNaN(rvals[j]))
        assertTrue(c.isNA(j));
      else
        assertEquals(rvals[j],c.atd(j),0);
  }
  /**
   * Constant Double Chunk - C0DChunk
   */
  @Test public void testC0DChunk_regular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4.32433);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(4.32433, cc.atd(k), Math.ulp(4.32433));
      Assert.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_PosInf() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.POSITIVE_INFINITY);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(Double.POSITIVE_INFINITY, cc.atd(k),0.0001);
      Assert.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NegInf() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.NEGATIVE_INFINITY);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(Double.NEGATIVE_INFINITY, cc.atd(k),0.0001);
      Assert.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NaN() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.NaN); //TODO: should this be disallowed?
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(Double.NaN, cc.atd(k), 0.0001);
      for (int k = 0; k < K; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateFromNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(K, nc._len);
      post();
      cc.set(K - 1, 342.34); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertEquals(342.34, cc.atd(K - 1),Math.ulp(342.34));
      Assert.assertTrue(! (cc.chk2() instanceof C0DChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(3.1415);
      assertEquals(K, nc._len);
      post();
      Assert.assertTrue(cc instanceof C0DChunk);
      cc.setNA(K - 1); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertEquals(3.1415, cc.atd(k), Math.ulp(3.1415));
      Assert.assertTrue(cc.isNA(K - 1));
      Assert.assertTrue(! (cc.chk2() instanceof C0DChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateToLarger() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(3.1415);
      assertEquals(K, nc._len);
      post();
      cc.set(K - 1, 9.9999); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertEquals(3.1415, cc.atd(k),Math.ulp(3.1415));
      Assert.assertEquals(9.9999, cc.atd(K - 1), Math.ulp(9.9999));
      Assert.assertTrue(! (cc.chk2() instanceof C0DChunk)); //no longer constant
    } finally { remove(); }
  }

  /**
   * Constant Long Chunk - C0LChunk
   */
  @Test public void testC0LChunk_zero() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(0,0); //handled as sparse
      assertEquals(K, nc._len);
      post();
      assertEquals(K, cc._len);
      for (int k = 0; k < K; ++k) Assert.assertEquals(0, cc.at8(k));
      Assert.assertTrue(cc instanceof C0LChunk);
    } finally { remove(); }
  }
  @Test public void testC0LChunk_regular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4,0);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(4, cc.at8(k));
      Assert.assertTrue(cc instanceof C0LChunk);
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateFromNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(K, nc._len);
      post();
      cc.set(K - 1, 342L); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertEquals(342L, cc.at8(K - 1));
      Assert.assertTrue(! (cc instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4,0);
      post();
      assertEquals(K, nc._len);
      cc.setNA(K - 1); //should inflate
      post_write();
      assertEquals(cc._len, K);
      for (int k = 0; k < K-1; ++k) Assert.assertEquals(4, cc.at8(k));
      Assert.assertTrue(cc.isNA(K - 1));
      Assert.assertTrue(!(cc.chk2() instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateRegular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(12345,0);
      assertEquals(K, nc._len);
      post();
      cc.set(K - 1, 0.1); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertEquals(12345, cc.at8(k));
      Assert.assertEquals(0.1, cc.atd(K - 1), Math.ulp(0.1));
      Assert.assertTrue(!(cc.chk2() instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }

  /**
   * 1 unsigned byte with NaN as 0xFF - C1Chunk
   */
  @Test public void testC1Chunk_regular() {
    try { pre();
      nc.addNA();
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      assertEquals(K, nc._len);
      post();
      Assert.assertTrue(cc.isNA_abs(0));
      for (int k = 1; k < K; ++k) Assert.assertEquals(k%254, cc.at8(k));
      Assert.assertTrue(cc instanceof C1Chunk);
    } finally { remove(); }
  }
  @Test public void testC1Chunk_inflateToLarger() {
    try { pre();
      nc.addNA();
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      post();
      assertEquals(K, nc._len);
      cc.set(K - 1, 256); //should inflate (bigger than max. capacity of 255)
      post_write();
      assertEquals(K, nc._len);
      Assert.assertTrue(cc.isNA_abs(0));
      for (int k = 1; k < K-1; ++k) Assert.assertEquals(k%254, cc.at8(k));
      Assert.assertEquals(256, cc.at8(K - 1));
      Assert.assertTrue(!(cc.chk2() instanceof C1Chunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC1Chunk_inflateInternalNA() {
    try { pre();
      nc.addNA();
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      post();
      assertEquals(K, nc._len);
      cc.set(K - 1, 255); //255 is internal NA, so it should inflate, since we're not trying to write a NA
      post_write();
      assertEquals(K, nc._len);
      Assert.assertTrue(cc.isNA_abs(0));
      for (int k = 1; k < K-1; ++k) Assert.assertEquals(k%254, cc.at8(k));
      Assert.assertEquals(255, cc.at8(K - 1));
      Assert.assertTrue(!(cc.chk2() instanceof C1Chunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testCXIChunk_setPostSparse() {
    try { pre();
      double extra = 3.5;
      nc.addZeros(K - 5);
      nc.addNum(extra);
      nc.addNum(0);
      nc.addNA();
      nc.addZeros(2);
      assertTrue(nc.isSparseZero());
      assertEquals(nc._sparseLen, 2);
      assertEquals(nc.sparseLenZero(), 2);
      assertEquals(nc.sparseLenNA(), K);

      for (int i = 0; i < K-5; i++) assertEquals(0, nc.atd(0), Math.ulp(0));
      assertEquals(extra, nc.atd(K-5), Math.ulp(extra));
      assertEquals(0, nc.atd(K-4), Math.ulp(0));
      assertEquals(Double.NaN, nc.atd(K-3), Math.ulp(Double.NaN));
      for (int i = K-2; i < K; i++) assertEquals(0, nc.atd(i), Math.ulp(0));

      post();
      cc.set(K-5, 0);
      post_write();
      assertEquals(K, nc._len);
      assertEquals(0,cc.atd(K-5),Math.ulp(0));
      assertTrue(cc.chk2() instanceof CXIChunk);

      for (int i = 0; i < K-3; i++) assertEquals(0, cc.atd(i), Math.ulp(0));
      assertEquals(Double.NaN, cc.atd(K-3), Math.ulp(Double.NaN));
      for (int i = K-2; i < K; i++) assertEquals(0, cc.atd(i), Math.ulp(0));
      assertEquals(1,cc.chk2().sparseLenZero());

    } finally { remove();}
  }

  @Test public void testCNAXDChunk_setPostSparse() {
    try {
      pre();
      double extra = 3.5;
      nc.addNAs(K - 5);
      nc.addNum(extra);
      nc.addNAs(2);
      nc.addZeros(2);
      assertTrue(nc.isSparseNA());
      assertEquals(nc._sparseLen, 3);
      assertEquals(nc.sparseLenZero(), K);
      assertEquals(nc.sparseLenNA(), 3);

      for (int i = 0; i < K - 5; i++) assertEquals(Double.NaN, nc.atd(i), Math.ulp(0));
      assertEquals(extra, nc.atd(K - 5), Math.ulp(extra));
      for (int i = K - 4; i < K -2; i++) assertEquals(Double.NaN, nc.atd(i), Math.ulp(0));
      for (int i = K - 2; i < K; i++) assertEquals(0, nc.atd(i), Math.ulp(0));

      post();
      cc.set(K - 3, 0);
      post_write();
      assertEquals(K, nc._len);
      assertEquals(0, cc.atd(K - 3), Math.ulp(0));
      assertTrue(cc.chk2() instanceof CNAXDChunk);

      for (int i = 0; i < K - 5; i++) assertEquals(Double.NaN, cc.atd(i), Math.ulp(0));
      assertEquals(extra, cc.atd(K - 5), Math.ulp(extra));
      assertEquals(Double.NaN, cc.atd(K-4), Math.ulp(0));
      for (int i = K - 3; i < K; i++) assertEquals(0, cc.atd(i), Math.ulp(0));
      assertEquals(4,cc.chk2().sparseLenNA());
    } finally {remove();}
  }

  @Test public void testSparseCat() {
    try {
      av = new AppendableVec(Vec.newKey(), Vec.T_CAT);
      nc = new NewChunk(av, 0);
      for (int k = 0; k < K; ++k) nc.addCategorical(0);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertTrue(!cc.isNA(k) && cc.at8(k)==0);
      Assert.assertTrue(cc instanceof C0LChunk);
    } finally { remove(); }
  }

  @Test public void testSetStrNull() {
    try {
      av = new AppendableVec(Vec.newKey(), Vec.T_STR);
      nc = new NewChunk(av, 0);
      nc.addStr("a");
      post();
      Assert.assertFalse(cc.isNA(0));
      cc.set(0, (String)null);
      Assert.assertTrue(cc.isNA(0));
    } finally { remove(); }
  }



  private static double []  test_seq = new double[]{
      2,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,3,0,0,0,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,2,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,10,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,3,3,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,2,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,10,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,3,0,0,0,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,3,0,0,1,0,0,0,0,0,0,0,0,2,0,0,3,0,0,0,0,0,0,0,0,0,3,32,0,0,0,17,6,2,0,0,0,0,0,0,0,0,67,0,0,0,0,0,0,0
  };

  @Test
  public void testSparseWithMissing() {
    // DOUBLES
    av = new AppendableVec(Vec.newKey(), Vec.T_NUM);
    nc = new NewChunk(av, 0);
    for (double d : test_seq)
      nc.addNum(d);
    Chunk c = nc.compress();
    for (int i = 0; i < test_seq.length; ++i) {
      if (Double.isNaN(test_seq[i]))
        Assert.assertTrue(c.isNA(i));
      else
        Assert.assertEquals("mismatch at line " + i + ": expected " + test_seq[i] + ", got " + c.atd(i), +test_seq[i], c.atd(i), 0);
    }
    // INTS
    av = new AppendableVec(Vec.newKey(), Vec.T_NUM);
    nc = new NewChunk(av, 0);
    for (double d : test_seq)
      if (Double.isNaN(d)) nc.addNA();
      else nc.addNum((int) d, 0);
    c = nc.compress();
    for (int i = 0; i < test_seq.length; ++i) {
      if (Double.isNaN(test_seq[i]))
        Assert.assertTrue(c.isNA(i));
      else
        Assert.assertEquals("mismatch at line " + i + ": expected " + test_seq[i] + ", got " + c.atd(i), +test_seq[i], c.atd(i), 0);
    }
  }

  @Test
  public void testSetUUID() {
    nc = new NewChunk(av, 0);
    nc.addUUID(123L, 456L);
    nc.addNA();
    assertFalse(nc.isNA(0));
    assertTrue(nc.isNA(1));
    nc.set_impl(0, 42L, Long.MIN_VALUE);
    nc.set_impl(1, 43L, Long.MAX_VALUE);
    assertEquals(42L, nc.at16l_impl(0));
    assertEquals(Long.MIN_VALUE, nc.at16h_impl(0));
    assertEquals(43L, nc.at16l_impl(1));
    assertEquals(Long.MAX_VALUE, nc.at16h_impl(1));
  }

  @Test
  public void testAddIllegalUUID() {
    nc = new NewChunk(av, 0);
    nc.addUUID(123L, 456L);
    nc.addNA();
    assertTrue(nc.isNA(1));
    assertTrue(nc.isNA2(1));
    try {
      nc.addUUID(C16Chunk._LO_NA, C16Chunk._HI_NA);
      assertTrue(nc.isNA(2));
      fail("Expected a failure on adding an illegal value");
    } catch(IllegalArgumentException iae) {
      // as expected
    }

    nc.addNum(Double.NEGATIVE_INFINITY);
    nc.addNum(Double.POSITIVE_INFINITY);
    nc.addNum(Double.NaN);
    nc.isNA(5);
  }

  @Test public void testAddIllegalDouble() {
    nc = new NewChunk(av, 0);
    nc.addNum(Math.PI);
    nc.addNum(Double.NEGATIVE_INFINITY);
    nc.addNum(Double.POSITIVE_INFINITY);
    nc.addNum(Double.NaN);
    assertFalse(nc.isNA(0));
    assertFalse(nc.isNA(1));
    assertFalse(nc.isNA(2));
    assertTrue(nc.isNA(3));
  }

}


package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Futures;
import water.TestUtil;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NewChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  final int K = 1 + (int)(new Random().nextFloat() * (FileVec.DFLT_CHUNK_SIZE >> 4));
  private AppendableVec av;
  NewChunkAry nc;
  ChunkAry cc;
  Vec vec;

  private void pre() {
    av = new AppendableVec(Vec.newKey(), Vec.T_NUM);
    nc = av.chunkForChunkIdx(0);
  }
  private void post() {
    Futures fs = new Futures();
    nc.close(fs);
    vec = av.close(fs);
    fs.blockForPending();
    assertTrue(DKV.get(vec._key)!=null); // the vec header is in DKV, chunk as well
    assertTrue(DKV.get(vec.chunkKey(0))!=null);
  }
  private void post_write() {
    cc.close(new Futures()).blockForPending();
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
    NewChunk nc = new NewChunk(Vec.T_NUM);
    nc.addNAs(128);
    assertTrue(nc.isSparseNA());
    for (int i = 0; i < 512; i++)
      nc.addUUID(i, i);
    assertFalse(nc.isSparseNA());
    ByteArraySupportedChunk c = (ByteArraySupportedChunk) nc.compress();
    assertEquals(128 + 512, c.len());
    for (int i = 0; i < 128; ++i)
      assertTrue(c.isNA(i));
    for (int i = 0; i < 512; i++) {
      assertEquals(i, c.at16l(128 + i));
      assertEquals(i, c.at16h(128 + i));
    }
  }

  private static class NewChunkTestCpy extends NewChunk {
    NewChunkTestCpy(byte t) {super(t);}
    public NewChunkTestCpy() { super(Vec.T_NUM); }
    int mantissaSize() {return _ms._vals1 != null?1:_ms._vals4 != null?4:8;}
    int exponentSize() {return _xs._vals1 != null?1:_xs._vals4 != null?4:0;}
    int missingSize()  {return _missing == null?0:_missing.size();}
  }

  
  private void testIntegerChunk(long [] values, int mantissaSize) {
    Vec v = Vec.makeCon(0,0);
    // single bytes
    Chunk c;

    NewChunkTestCpy nc = new NewChunkTestCpy();
    for( long val : values )
      nc.addNum(val, 0);
    assertEquals(mantissaSize,nc.mantissaSize());
    assertEquals(0,nc.exponentSize());
    assertEquals(0,nc.missingSize());
    for(int i = 0; i < values.length; ++i)
      assertEquals(values[i],nc.at8(i));
    for(int i = 0; i < values.length; i += 5)
      nc.setNA_impl(i);
    c = nc.compress();
    for(int i = 0; i < values.length; ++i) {
      if(i % 5 == 0)
        assertTrue(c.isNA(i));
      else
        assertEquals(values[i], c.at8(i));
    }

    // test with exponent
    nc = new NewChunkTestCpy();
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
    nc = new NewChunkTestCpy();
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
        assertEquals(values[i], c.at8(i));
    }
    assertEquals(Math.PI,c.atd(values.length),0);
    // test switch to sparse zero
    nc = new NewChunkTestCpy();
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
    nc = new NewChunkTestCpy();
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
    NewChunk nc = new NewChunk(Vec.T_NUM);
    int N = 1000;
    nc.addZeros(N);
    nc.addNum(Math.PI);
    nc.addZeros(N);
    nc.addNum(Math.E);
    int len = nc.len();
    ByteArraySupportedChunk c = (ByteArraySupportedChunk) nc.compress();
    ChunkAry ca = new ChunkAry(null,len,c);
    int i=0;
    for(;i<N;)     assertEquals(0,c.atd(i++),1e-16);
    Chunk.SparseNum sv = ca.sparseNum(0);
    assertEquals(i,sv.rowId());
    assertEquals(         Math.PI,sv.dval(),1e-16);
    for(;i<2*N+1;) assertEquals(0,c.atd(i++),1e-16);
    assertEquals(i,sv.nextNZ().rowId());
    assertEquals(         Math.E,sv.dval(),1e-16);
    nc = new NewChunk(Vec.T_NUM);
    nc.addNum(Math.PI);
    nc.addNum(Double.MAX_VALUE);
    nc.addNum(Double.MIN_VALUE);
    nc.addZeros(5);
    nc.addNum(Math.E);
    nc.addZeros(1000000);
    len = nc.len();
    c = (ByteArraySupportedChunk) nc.compress();
    ca = new ChunkAry(null,len,c);
    sv = ca.sparseNum(0);
    assertEquals(0,sv.rowId());
    assertEquals(1,sv.nextNZ().rowId());
    assertEquals(2,sv.nextNZ().rowId());
    assertEquals(8,sv.nextNZ().rowId());
    assertEquals(c.atd(0), Math.PI,1e-16);
    assertEquals(c.atd(8), Math.E,1e-16);

    // test flip from dense -> sparse0 -> desne
    nc = new NewChunk(Vec.T_NUM);
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
    c = (ByteArraySupportedChunk) nc.compress();
    assertEquals(1546,c.len());
    for(int j = 0; j < c.len()-1; ++j)
      assertEquals(rvals[j],c.atd(j),0);

    // test flip from dense -> sparseNA -> desne
    nc = new NewChunk(Vec.T_NUM);
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
    c = (ByteArraySupportedChunk) nc.compress();
    assertEquals(1546,c.len());
    for(int j = 0; j < c.len()-1; ++j)
      if(Double.isNaN(rvals[j]))
        assertTrue(c.isNA(j));
      else
        assertEquals(rvals[j],c.atd(j),0);
  }
  /**
   * Constant Double ByteArraySupportedChunk - C0DChunk
   */
  @Test public void testC0DChunk_regular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4.32433);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(4.32433, cc.atd(k), Math.ulp(4.32433));
      Assert.assertTrue(cc.getChunk(0) instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA(0);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertTrue(cc.getChunk(0) instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_PosInf() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.POSITIVE_INFINITY);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(Double.POSITIVE_INFINITY, cc.atd(k),0.0001);
      Assert.assertTrue(cc.getChunk(0) instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NegInf() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.NEGATIVE_INFINITY);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(Double.NEGATIVE_INFINITY, cc.atd(k),0.0001);
      Assert.assertTrue(cc.getChunk(0) instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NaN() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.NaN); //TODO: should this be disallowed?
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(Double.NaN, cc.atd(k), 0.0001);
      for (int k = 0; k < K; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertTrue(cc.getChunk(0) instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateFromNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA(0);
      assertEquals(K, nc._len);
      post();
      cc.set(K - 1, 342.34); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertEquals(342.34, cc.atd(K - 1),Math.ulp(342.34));
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(3.1415);
      assertEquals(K, nc._len);
      post();
      Assert.assertTrue(cc.getChunk(0) instanceof C0DChunk);
      cc.setNA(K - 1,0); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertEquals(3.1415, cc.atd(k), Math.ulp(3.1415));
      Assert.assertTrue(cc.isNA(K - 1));
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
    } finally { remove(); }
  }

  /**
   * Constant Long ByteArraySupportedChunk - C0LChunk
   */
  @Test public void testC0LChunk_zero() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addInteger(0,0); //handled as sparse
      assertEquals(K, nc._len);
      post();
      assertEquals(K, cc._len);
      for (int k = 0; k < K; ++k) Assert.assertEquals(0, cc.at8(k));
      Assert.assertTrue(cc.getChunk(0) instanceof C0LChunk);
    } finally { remove(); }
  }
  @Test public void testC0LChunk_regular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addInteger(0,4);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertEquals(4, cc.at8(k));
      Assert.assertTrue(cc.getChunk(0) instanceof C0LChunk);
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateFromNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA(0);
      assertEquals(K, nc._len);
      post();
      cc.set(K - 1, 342L); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertEquals(342L, cc.at8(K - 1));
      Assert.assertTrue(! (cc.getChunk(0) instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addInteger(0,4);
      post();
      assertEquals(K, nc._len);
      cc.setNA(K - 1,0); //should inflate
      post_write();
      assertEquals(cc._len, K);
      for (int k = 0; k < K-1; ++k) Assert.assertEquals(4, cc.at8(k));
      Assert.assertTrue(cc.isNA(K - 1));
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateRegular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addInteger(0,12345);
      assertEquals(K, nc._len);
      post();
      cc.set(K - 1, 0.1); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertEquals(12345, cc.at8(k));
      Assert.assertEquals(0.1, cc.atd(K - 1), Math.ulp(0.1));
    } finally { remove(); }
  }

  /**
   * 1 unsigned byte with NaN as 0xFF - C1Chunk
   */
  @Test public void testC1Chunk_regular() {
    try { pre();
      nc.addNA(0);
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      assertEquals(K, nc._len);
      post();
      Assert.assertTrue(cc.isNA(0));
      for (int k = 1; k < K; ++k) Assert.assertEquals(k%254, cc.at8(k));
    } finally { remove(); }
  }
  @Test public void testC1Chunk_inflateToLarger() {
    try { pre();
      nc.addNA(0);
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      post();
      assertEquals(K, nc._len);
      cc.set(K - 1, 256); //should inflate (bigger than max. capacity of 255)
      post_write();
      assertEquals(K, nc._len);
      Assert.assertTrue(cc.isNA(0));
      for (int k = 1; k < K-1; ++k) Assert.assertEquals(k%254, cc.at8(k));
      Assert.assertEquals(256, cc.at8(K - 1));
    } finally { remove(); }
  }
  @Test public void testC1Chunk_inflateInternalNA() {
    try { pre();
      nc.addNA(0);
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      post();
      assertEquals(K, nc._len);
      cc.set(K - 1, 255); //255 is internal NA, so it should inflate, since we're not trying to write a NA
      post_write();
      assertEquals(K, nc._len);
      Assert.assertTrue(cc.isNA(0));
      for (int k = 1; k < K-1; ++k) Assert.assertEquals(k%254, cc.at8(k));
      Assert.assertEquals(255, cc.at8(K - 1));
    } finally { remove(); }
  }
  @Test public void testCXIChunk_setPostSparse() {
    try { pre();
      double extra = 3.5;
      nc.addZeros(0,K - 5);
      nc.addNum(extra);
      nc.addNum(0);
      nc.addNA(0);
      nc.addZeros(0,2);
      assertTrue(nc.isSparseZero());
      assertEquals(2,nc.getChunk(0).len());
      assertEquals(2,nc.sparseLenZero());

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
      
      for (int i = 0; i < K-3; i++) assertEquals(0, cc.atd(i), Math.ulp(0));
      assertEquals(Double.NaN, cc.atd(K-3), Math.ulp(Double.NaN));
      for (int i = K-2; i < K; i++) assertEquals(0, cc.atd(i), Math.ulp(0));
      
    } finally { remove();}
  }
  
  @Test public void testCNAXDChunk_setPostSparse() {
    try {
      pre();
      double extra = 3.5;
      nc.addNAs(0,K - 5);
      nc.addNum(extra);
      nc.addNAs(0,2);
      nc.addZeros(0,2);
      assertTrue(nc.isSparseNA());
      assertEquals(3,nc.getChunk(0).len(), 3);
      assertEquals(nc.sparseLenZero(), K);
      assertEquals(3,nc.getChunk(0).len());

      for (int i = 0; i < K - 5; i++) assertEquals(Double.NaN, nc.atd(i), Math.ulp(0));
      assertEquals(extra, nc.atd(K - 5), Math.ulp(extra));
      for (int i = K - 4; i < K -2; i++) assertEquals(Double.NaN, nc.atd(i), Math.ulp(0));
      for (int i = K - 2; i < K; i++) assertEquals(0, nc.atd(i), Math.ulp(0));

      post();
      cc.set(K - 3, 0);
      post_write();
      assertEquals(K, nc._len);
      assertEquals(0, cc.atd(K - 3), Math.ulp(0));

      for (int i = 0; i < K - 5; i++) assertEquals(Double.NaN, cc.atd(i), Math.ulp(0));
      assertEquals(extra, cc.atd(K - 5), Math.ulp(extra));
      assertEquals(Double.NaN, cc.atd(K-4), Math.ulp(0));
      for (int i = K - 3; i < K; i++) assertEquals(0, cc.atd(i), Math.ulp(0));
    } finally {remove();}
  }
  
  @Test public void testSparseCat() {
    try {
      av = new AppendableVec(Vec.newKey(), Vec.T_CAT);
      nc = av.chunkForChunkIdx(0);
      for (int k = 0; k < K; ++k) nc.addInteger(0);
      assertEquals(K, nc._len);
      post();
      for (int k = 0; k < K; ++k) Assert.assertTrue(!cc.isNA(k) && cc.at8(k)==0);
      Assert.assertTrue(cc.getChunk(0) instanceof C0LChunk);
    } finally { remove(); }
  }


private static double []  test_seq = new double[]{
    2,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,3,0,0,0,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,2,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,10,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,3,3,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,2,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,10,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,3,0,0,0,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,3,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,3,0,0,1,0,0,0,0,0,0,0,0,2,0,0,3,0,0,0,0,0,0,0,0,0,3,32,0,0,0,17,6,2,0,0,0,0,0,0,0,0,67,0,0,0,0,0,0,0
};
@Test public void testSparseWithMissing(){
  // DOUBLES
  av = new AppendableVec(Vec.newKey(), Vec.T_NUM);
  nc = av.chunkForChunkIdx(0);
  for(double d:test_seq)
    nc.addNum(d);
  Chunk c = nc.getChunk(0).compress();
  for(int i =0 ; i < test_seq.length; ++i) {
    if(Double.isNaN(test_seq[i]))
      Assert.assertTrue(c.isNA(i));
    else
      Assert.assertEquals("mismatch at line " + i + ": expected " + test_seq[i] + ", got " + c.atd(i), +  test_seq[i],c.atd(i),0);
  }
  // INTS
  av = new AppendableVec(Vec.newKey(), Vec.T_NUM);
  nc = av.chunkForChunkIdx(0);
  for(double d:test_seq)
    if(Double.isNaN(d)) nc.addNA(0);
    else nc.addInteger((int)d);
  c = nc.getChunk(0).compress();
  for(int i =0 ; i < test_seq.length; ++i) {
    if(Double.isNaN(test_seq[i]))
      Assert.assertTrue(c.isNA(i));
    else
      Assert.assertEquals("mismatch at line " + i + ": expected " + test_seq[i] + ", got " + c.atd(i), +  test_seq[i],c.atd(i),0);
  }

}



}


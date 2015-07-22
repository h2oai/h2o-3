package water.fvec;

import org.junit.*;
import static org.junit.Assert.*;

import water.DKV;
import water.Futures;
import water.TestUtil;
import java.util.Random;

public class NewChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  final int K = 1 + (int)(new Random().nextFloat() * (FileVec.DFLT_CHUNK_SIZE >> 4));
  AppendableVec av;
  NewChunk nc;
  Chunk cc;
  Vec vec;

  void pre() {
    av = new AppendableVec(Vec.newKey());
    nc = new NewChunk(av, 0);
  }
  void post() {
    cc = nc.compress();
    av._tmp_espc[0] = K; //HACK
    cc._start = 0; //HACK
    cc._cidx = 0; // HACK as well
    Futures fs = new Futures();
    vec = cc._vec = av.close(fs);
    fs.blockForPending();
    assert(DKV.get(vec._key)!=null); //only the vec header is in DKV, the chunk is not
  }
  void post_write() {
    cc.close(0, new Futures()).blockForPending();
  }
  void remove() {
    vec.remove();
  }

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
  @Test public void testSparseDoubles2(){
    NewChunk nc = new NewChunk(null, 0, true);
    int N = 1000;
    nc.addZeros(N);
    nc.addNum(Math.PI);
    nc.addZeros(N);
    Chunk c = nc.compress();
    int i=0;
    for(;i<N;)     assertEquals(0,c.atd(i++),1e-16);
    assertEquals(         Math.PI,c.atd(i++),1e-16);
    for(;i<2*N+1;) assertEquals(0,c.atd(i++),1e-16);
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
      cc.set(K - 1, 342l); //should inflate
      post_write();
      assertEquals(K, nc._len);
      for (int k = 0; k < K-1; ++k) Assert.assertTrue(cc.isNA(k));
      Assert.assertEquals(342, cc.at8(K - 1));
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

}


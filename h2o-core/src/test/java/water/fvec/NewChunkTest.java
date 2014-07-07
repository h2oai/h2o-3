package water.fvec;

import org.testng.AssertJUnit;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import water.DKV;
import water.Futures;
import water.TestUtil;

import java.util.Random;

public class NewChunkTest extends TestUtil {
  final int K = 1 + (int)(new Random().nextFloat() * (water.fvec.Vec.CHUNK_SZ >> 4));
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
    Futures fs = new Futures();
    av._espc[0] = K; //HACK
    cc._start = 0; //HACK
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

  /**
   * Constant Double Chunk - C0DChunk
   */
  @Test public void testC0DChunk_regular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4.32433);
      assertEquals(K, nc.len());
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(4.32433, cc.at0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(K, nc.len());
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_PosInf() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.POSITIVE_INFINITY);
      assertEquals(K, nc.len());
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(Double.POSITIVE_INFINITY, cc.at0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NegInf() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.NEGATIVE_INFINITY);
      assertEquals(K, nc.len());
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(Double.NEGATIVE_INFINITY, cc.at0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NaN() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.NaN); //TODO: should this be disallowed?
      assertEquals(K, nc.len());
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(Double.NaN, cc.at0(k));
      for (int k = 0; k < K; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateFromNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(K, nc.len());
      post();
      cc.set0(K-1, 342.34); //should inflate
      post_write();
      assertEquals(K, nc.len());
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertEquals(342.34, cc.at0(K - 1));
      AssertJUnit.assertTrue(! (cc.chk2() instanceof C0DChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(3.1415);
      assertEquals(K, nc.len());
      post();
      cc.setNA0(K - 1); //should inflate
      post_write();
      assertEquals(K, nc.len());
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertEquals(3.1415, cc.at0(k));
      AssertJUnit.assertTrue(cc.isNA0(K-1));
      AssertJUnit.assertTrue(! (cc.chk2() instanceof C0DChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateToLarger() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(3.1415);
      assertEquals(K, nc.len());
      post();
      cc.set0(K-1, 9.9999); //should inflate
      post_write();
      assertEquals(K, nc.len());
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertEquals(3.1415, cc.at0(k));
      AssertJUnit.assertEquals(9.9999, cc.at0(K-1));
      AssertJUnit.assertTrue(! (cc.chk2() instanceof C0DChunk)); //no longer constant
    } finally { remove(); }
  }

  /**
   * Constant Long Chunk - C0LChunk
   */
  @Test public void testC0LChunk_zero() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(0);
      assertEquals(K, nc.len());
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(0, cc.at80(k));
      AssertJUnit.assertTrue(cc instanceof C0LChunk);
    } finally { remove(); }
  }
  @Test public void testC0LChunk_regular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4);
      assertEquals(K, nc.len());
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(4, cc.at80(k));
      AssertJUnit.assertTrue(cc instanceof C0LChunk);
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateFromNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(K, nc.len());
      post();
      cc.set0(K - 1, 342l); //should inflate
      post_write();
      assertEquals(K, nc.len());
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertEquals(342, cc.at80(K - 1));
      AssertJUnit.assertTrue(! (cc instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4);
      post();
      assertEquals(K, nc.len());
      cc.setNA0(K - 1); //should inflate
      post_write();
      assertEquals(cc.len(), K);
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertEquals(4, cc.at80(k));
      AssertJUnit.assertTrue(cc.isNA0(K-1));
      AssertJUnit.assertTrue(!(cc.chk2() instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateRegular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(12345);
      assertEquals(K, nc.len());
      post();
      cc.set0(K-1, 0.1); //should inflate
      post_write();
      assertEquals(K, nc.len());
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertEquals(12345, cc.at80(k));
      AssertJUnit.assertEquals(0.1, cc.at0(K - 1));
      AssertJUnit.assertTrue(!(cc.chk2() instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }

  /**
   * 1 unsigned byte with NaN as 0xFF - C1Chunk
   */
  @Test public void testC1Chunk_regular() {
    try { pre();
      nc.addNA();
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      assertEquals(K, nc.len());
      post();
      AssertJUnit.assertTrue(cc.isNA(0));
      for (int k = 1; k < K; ++k) AssertJUnit.assertEquals(k%254, cc.at80(k));
      AssertJUnit.assertTrue(cc instanceof C1Chunk);
    } finally { remove(); }
  }
  @Test public void testC1Chunk_inflateToLarger() {
    try { pre();
      nc.addNA();
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      post();
      assertEquals(K, nc.len());
      cc.set0(K - 1, 256); //should inflate (bigger than max. capacity of 255)
      post_write();
      assertEquals(K, nc.len());
      AssertJUnit.assertTrue(cc.isNA(0));
      for (int k = 1; k < K-1; ++k) AssertJUnit.assertEquals(k%254, cc.at80(k));
      AssertJUnit.assertEquals(256, cc.at80(K-1));
      AssertJUnit.assertTrue(!(cc.chk2() instanceof C1Chunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC1Chunk_inflateInternalNA() {
    try { pre();
      nc.addNA();
      for (int k = 1; k < K; ++k) nc.addNum(k%254);
      post();
      assertEquals(K, nc.len());
      cc.set0(K - 1, 255); //255 is internal NA, so it should inflate, since we're not trying to write a NA
      post_write();
      assertEquals(K, nc.len());
      AssertJUnit.assertTrue(cc.isNA(0));
      for (int k = 1; k < K-1; ++k) AssertJUnit.assertEquals(k%254, cc.at80(k));
      AssertJUnit.assertEquals(255, cc.at80(K-1));
      AssertJUnit.assertTrue(!(cc.chk2() instanceof C1Chunk)); //no longer constant
    } finally { remove(); }
  }

}


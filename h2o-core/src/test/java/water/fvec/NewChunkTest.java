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
      assertEquals(nc.len(), K);
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(4.32433, cc.at0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(nc.len(), K);
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_PosInf() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.POSITIVE_INFINITY);
      assertEquals(nc.len(), K);
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(Double.POSITIVE_INFINITY, cc.at0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NegInf() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.NEGATIVE_INFINITY);
      assertEquals(nc.len(), K);
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(Double.NEGATIVE_INFINITY, cc.at0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_NaN() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(Double.NaN); //TODO: should this be disallowed?
      assertEquals(nc.len(), K);
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(Double.NaN, cc.at0(k));
      for (int k = 0; k < K; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateFromNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(nc.len(), K);
      post();
      cc.set0(K-1, 342.34); //should inflate
      post_write();
      assertEquals(nc.len(), K);
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertEquals(342.34, cc.at0(K - 1));
      AssertJUnit.assertTrue(! (cc instanceof C0DChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(3.1415);
      assertEquals(nc.len(), K);
      post();
      cc.setNA0(K - 1); //should inflate
      post_write();
      assertEquals(nc.len(), K);
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertEquals(3.1415, cc.at0(k));
      AssertJUnit.assertTrue(cc.isNA0(K-1));
      AssertJUnit.assertTrue(! (cc instanceof C0DChunk)); //no longer constant
    } finally { remove(); }
  }

  /**
   * Constant Long Chunk - C0LChunk
   */
  @Test public void testC0LChunk_zero() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(0);
      assertEquals(nc.len(), K);
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(0, cc.at80(k));
      AssertJUnit.assertTrue(cc instanceof C0LChunk);
    } finally { remove(); }
  }
  @Test public void testC0LChunk_regular() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4);
      assertEquals(nc.len(), K);
      post();
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(4, cc.at80(k));
      AssertJUnit.assertTrue(cc instanceof C0LChunk);
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateFromNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNA();
      assertEquals(nc.len(), K);
      post();
      cc.set0(K - 1, 342l); //should inflate
      post_write();
      assertEquals(cc.len(), K);
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertEquals(342, cc.at80(K - 1));
      AssertJUnit.assertTrue(! (cc instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }
  @Test public void testC0LChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(4);
      post();
      assertEquals(nc.len(), K);
      cc.setNA0(K - 1); //should_inflate
      post_write();
      assertEquals(cc.len(), K);
      for (int k = 0; k < K-1; ++k) AssertJUnit.assertEquals(4, cc.at80(k));
      AssertJUnit.assertTrue(cc.isNA0(K-1));
      AssertJUnit.assertTrue(!(cc instanceof C0LChunk)); //no longer constant
    } finally { remove(); }
  }
}


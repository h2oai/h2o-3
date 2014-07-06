package water.fvec;

import org.testng.AssertJUnit;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Random;

public class NewChunkTest extends TestUtil {
  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  final int K = 1 + (int)new Random().nextFloat() * (water.fvec.Vec.CHUNK_SZ-2);

  AppendableVec av;
  NewChunk nc;
  water.fvec.Chunk cc;

  void pre() {
    av = new water.fvec.AppendableVec(water.fvec.Vec.newKey());
    nc = new water.fvec.NewChunk(av, 0);
  }
  void post() {
    cc = nc.compress();
    cc._vec = av.close(null);
  }
  void remove() {
    cc._vec.remove();
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
      nc.addNum(342.34);
      assertEquals(nc.len(), K+1);
      post();
      AssertJUnit.assertTrue(! (cc instanceof C0DChunk)); //no longer constant
      for (int k = 0; k < K; ++k) AssertJUnit.assertTrue(cc.isNA0(k));
      AssertJUnit.assertEquals(342.34, cc.at0(K));
    } finally { remove(); }
  }
  @Test public void testC0DChunk_inflateToNA() {
    try { pre();
      for (int k = 0; k < K; ++k) nc.addNum(3.1415);
      assertEquals(nc.len(), K);
      nc.addNA();
      assertEquals(nc.len(), K+1);
      post();
      AssertJUnit.assertTrue(! (cc instanceof C0DChunk)); //no longer constant
      for (int k = 0; k < K; ++k) AssertJUnit.assertEquals(3.1415, cc.at0(k));
      AssertJUnit.assertTrue(cc.isNA0(K));
    } finally { remove(); }
  }
}


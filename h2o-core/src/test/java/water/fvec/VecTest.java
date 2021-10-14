package water.fvec;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.util.Log;
import water.util.ReflectionUtils;

import static org.junit.Assert.*;
import static water.fvec.Vec.makeCon;
import static water.fvec.Vec.makeConN;
import static water.fvec.Vec.makeSeq;

/** This test tests stability of Vec API. */
public class VecTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  /** Test toCategoricalVec call to return correct domain. */
  @Test public void testToCategorical() {
    testToCategoricalDomainMatch(vec(0, 1, 0, 1), ar("0", "1"));
    testToCategoricalDomainMatch(vec(1, 2, 3, 4, 5, 6, 7), ar("1", "2", "3", "4", "5", "6", "7"));
    testToCategoricalDomainMatch(vec(0, 1, 2, 99, 4, 5, 6), ar("0", "1", "2", "4", "5", "6", "99"));
  }

  @Test public void testCalculatingDomainOnNumericalVecReturnsNull() {
    Vec vec = vec(0, 1, 0, 1);
    assertTrue("Should be numerical vector", vec.get_type_str().equals(Vec.TYPE_STR[Vec.T_NUM]));
    String[] domains = vec.domain();
    Assert.assertArrayEquals(null, domains);
    vec.remove();
  }

  @Test public void makeCopy() {
    Vec copyOfVec = null;
    Vec expected = null;
    try {
      Scope.enter();
      Vec originalVec = vec(1, 2, 3, 4, 5);
      copyOfVec = originalVec.makeCopy();
      Scope.untrack(copyOfVec._key);
      Scope.exit();
      expected = vec(1, 2, 3, 4, 5);
      assertVecEquals(expected, copyOfVec, 1e-5);
    } finally {
      if( copyOfVec !=null ) copyOfVec.remove();
      if( expected !=null ) expected.remove();
    }

  }

  private void testToCategoricalDomainMatch(Vec f, String[] expectedDomain) {
    Vec ef = null;
    try {
      ef = f.toCategoricalVec();
      String[] actualDomain = ef.domain();
      Assert.assertArrayEquals("toCategoricalVec call returns wrong domain!", expectedDomain, actualDomain);
    } finally {
      if( f !=null ) f .remove();
      if( ef!=null ) ef.remove();
    }
  }

  @Test public void testMakeConSeq() {
    Vec v;

    v = makeCon(0xCAFE,2*FileVec.DFLT_CHUNK_SIZE,false);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.espc().length == 3);
    assertTrue(
            v.espc()[0] == 0              &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE
    );
    v.remove(new Futures()).blockForPending();

    v = makeCon(0xCAFE,3*FileVec.DFLT_CHUNK_SIZE,false);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(3*FileVec.DFLT_CHUNK_SIZE-1) == 0xCAFE);
    assertTrue(v.espc().length == 4);
    assertTrue(
            v.espc()[0] == 0              &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE   &&
                    v.espc()[2] == FileVec.DFLT_CHUNK_SIZE*2
    );
    v.remove(new Futures()).blockForPending();

    v = makeCon(0xCAFE,3*FileVec.DFLT_CHUNK_SIZE+1,false);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(3*FileVec.DFLT_CHUNK_SIZE) == 0xCAFE);
    assertTrue(v.espc().length == 4);
    assertTrue(
            v.espc()[0] == 0              &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE   &&
                    v.espc()[2] == FileVec.DFLT_CHUNK_SIZE*2 &&
                    v.espc()[3] == FileVec.DFLT_CHUNK_SIZE*3+1
    );
    v.remove(new Futures()).blockForPending();

    v = makeCon(0xCAFE,4*FileVec.DFLT_CHUNK_SIZE,false);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(4*FileVec.DFLT_CHUNK_SIZE-1) == 0xCAFE);
    assertTrue(v.espc().length == 5);
    assertTrue(
            v.espc()[0] == 0              &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE   &&
                    v.espc()[2] == FileVec.DFLT_CHUNK_SIZE*2 &&
                    v.espc()[3] == FileVec.DFLT_CHUNK_SIZE*3
    );
    v.remove(new Futures()).blockForPending();
  }

  @Test public void testMakeSeq() {
    Vec v = makeSeq(3*FileVec.DFLT_CHUNK_SIZE, false);
    assertTrue(v.at(0) == 1);
    assertTrue(v.at(234) == 235);
    assertTrue(v.at(2*FileVec.DFLT_CHUNK_SIZE) == 2*FileVec.DFLT_CHUNK_SIZE+1);
    assertTrue(v.espc().length == 4);
    assertTrue(
            v.espc()[0] == 0 &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE &&
                    v.espc()[2] == FileVec.DFLT_CHUNK_SIZE * 2
    );
    v.remove(new Futures()).blockForPending();
  }

  @Test public void testMakeConStr() {
    Vec source = makeSeq(2 * FileVec.DFLT_CHUNK_SIZE, false);
    Vec con = source.makeCon(Vec.T_STR);
    // check rollup possible
    assertEquals(0d, con.base(), 0);
    // set each row unique value
    new MRTask() {
      @Override
      public void map(Chunk c) {
        long firstRow = c._vec.espc()[c.cidx()];
        for (int row = 0; row < c._len; row++) {
          c.set(row, "row_" + (firstRow + row));
        }
      }
    }.doAll(con);
    // set row values are correct strings
    for (int row = 0; row < con.length(); row++) {
      assertEquals(row + "th row has expected value", "row_" + row, con.stringAt(row));
    }
    // check rollup possible
    assertEquals(1, con.sparseRatio(), 0);
    source.remove(new Futures()).blockForPending();
    con.remove(new Futures()).blockForPending();
  }

  @Test public void testChunkForChunkIdxAfterVecUpdate() {
    try {
      Scope.enter();
      Vec v1 = Scope.track(makeSeq(2 * FileVec.DFLT_CHUNK_SIZE, false));
      Vec v2 = Scope.track(makeSeq(2 * FileVec.DFLT_CHUNK_SIZE, false));
      Chunk c0 = v1.chunkForChunkIdx(0);
      c0._vec = v2; // inject any vec into the cached POJO, this simulates a stale Vec reference
      Chunk c0c = v1.chunkForChunkIdx(0);
      assertSame(c0, c0c);
      // the vec reference was updated in the existing live object
      assertSame(v1, c0c._vec);
    } finally {
      Scope.exit();
    }
  }

  @Test public void testChunkForChunkIdxMRTask() {
    Assume.assumeTrue(H2O.getCloudSize() > 1);
    try {
      Scope.enter();
      Vec v = Scope.track(makeConN((long) 1e6, 16));
      // 1. Run an arbitrary MRTask to populate the POJO Chunk caches
      new MRTask() {
        @Override
        public void map(Chunk c) {
          if (c.vec().get_type() != Vec.T_NUM)
            throw new IllegalStateException("Expected a numeric Vec");
        }
      }.doAll(v);
      // 2. Install updated Vec in DKV
      v._type = Vec.T_TIME;
      DKV.put(v);
      // 3. Just for fun - show the state of DKV after the update (not needed for the test)
      printStoreInfo(v._key);
      // 4. Run another MRTask, we should get updated Vec on all nodes not the stale one
      new MRTask() {
        @Override
        public void map(Chunk c) {
          if (c.vec().get_type() != Vec.T_TIME)
            throw new IllegalStateException("Expected a time Vec");
        }
      }.doAll(v);
    } finally {
      Scope.exit();
    }
  }

  @Test public void testChunkIdxWithDeletedVec() {
    Key<Vec> k = null;
    try {
      Scope.enter();
      Vec v = Scope.track(makeConN((long) 1e6, 16));
      k = v._key;
      v.remove();
      v.chunkForChunkIdx(0);
      fail("chunkForChunkIdx didn't fail");
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg == null || k == null || !msg.startsWith("Missing chunk 0 for vector " + k.toString() + "; Vec info: is not in DKV;"))
        throw e;
      Log.warn(e);
    } finally {
      Scope.exit();
    }
  }

  private static void printStoreInfo(final Key<Vec> k) {
    GatherKeyInfoTask info = new GatherKeyInfoTask(k).doAllNodes();
    for (int i = 0; i < H2O.getCloudSize(); i++) {
      System.out.print(H2O.CLOUD._memary[i].getIpPortString());
      System.out.print(" self: ");
      System.out.print(checkMark(H2O.CLOUD._memary[i] == H2O.SELF));
      System.out.print(" home: ");
      System.out.print(checkMark(H2O.CLOUD._memary[i] == k.home_node()));
      System.out.print(" value: ");
      System.out.print(checkMark(info._hasVal[i]));
      System.out.print(" pojo: ");
      System.out.print(checkMark(info._hasPOJO[i]));
      System.out.println();
    }
  }

  private static String checkMark(boolean c) {
    return c ? "✓" : " ";  
  }
  
  static class GatherKeyInfoTask extends MRTask<GatherKeyInfoTask> {
    private final Key<Vec> _k;
    private boolean[] _hasVal;
    private boolean[] _hasPOJO;

    GatherKeyInfoTask(Key<Vec> k) {
      _k = k;
    }

    @Override
    public void setupLocal() {
      _hasVal = new boolean[H2O.getCloudSize()];
      _hasPOJO = new boolean[H2O.getCloudSize()];
      Value val = H2O.STORE.get(_k);
      if (val != null) {
        Vec localVec = ReflectionUtils.getFieldValue(val, "_pojo");
        _hasVal[H2O.SELF.index()] = true;
        _hasPOJO[H2O.SELF.index()] = localVec != null;
      }
    }

    @Override
    public void reduce(GatherKeyInfoTask mrt) {
      for (int i = 0; i < H2O.getCloudSize(); i++) {
        _hasVal[i] = _hasVal[i] || mrt._hasVal[i];
        _hasPOJO[i] = _hasPOJO[i] || mrt._hasPOJO[i];
      }
    }
  }

  @Test
  public void testIsConst() {
    Scope.enter();
    try {
      assertTrue(Scope.track(vec(0, 0)).isConst());
      assertFalse(Scope.track(vec(0, 1)).isConst());
      assertFalse(Scope.track(dvec(Double.NaN, Double.NaN)).isConst());
      // NAs are ignored by default
      assertTrue(Scope.track(dvec(Double.NaN, 1)).isConst());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testIsConstNA() {
    Scope.enter();
    try {
      assertTrue(Scope.track(vec(0, 0)).isConst(false));
      assertFalse(Scope.track(vec(0, 1)).isConst(false));
      assertFalse(Scope.track(dvec(Double.NaN, Double.NaN)).isConst(false));
      assertTrue(Scope.track(dvec(Double.NaN, 1)).isConst(false));

      assertTrue(Scope.track(vec(0, 0)).isConst(true));
      assertFalse(Scope.track(vec(0, 1)).isConst(true));
      assertFalse(Scope.track(dvec(Double.NaN, Double.NaN)).isConst(true));
      // NA is considered to be a value
      assertFalse(Scope.track(dvec(Double.NaN, 1)).isConst(true));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testIsBinary() {
    Scope.enter();
    try {
      assertTrue(Scope.track(vec(0, 1)).isBinary());
      // check only 0, 1 cases
      assertFalse(Scope.track(vec(0, 2)).isBinary());
      // const vector is ok
      assertTrue(Scope.track(vec(0)).isBinary());
      assertTrue(Scope.track(vec(1)).isBinary());
      // Test NAs
      assertFalse(Scope.track(dvec(Double.NaN, Double.NaN)).isBinary());
      // NAs are ignored by default
      assertTrue(Scope.track(dvec(Double.NaN, 1)).isBinary());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testIsBinaryOnes() {
    Scope.enter();
    try {
      assertTrue(Scope.track(vec(1, -1)).isBinaryOnes());
      // check only 0, 1 cases
      assertFalse(Scope.track(vec(1, 2)).isBinaryOnes());
      // const vector is ok
      assertTrue(Scope.track(vec(-1)).isBinaryOnes());
      assertTrue(Scope.track(vec(1)).isBinaryOnes());
      // Test Nas
      assertFalse(Scope.track(dvec(Double.NaN, Double.NaN)).isBinaryOnes());
      // NAs are ignored by default
      assertTrue(Scope.track(dvec(Double.NaN, 1)).isBinaryOnes());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testIsBinaryStrict() {
    Scope.enter();
    try {
      assertTrue(Scope.track(dvec(0, 1)).isBinary(true));
      assertTrue(Scope.track(dvec(-1, 1)).isBinary(true));
      assertFalse(Scope.track(dvec(0, 2)).isBinary(true));
      // const vector is not ok
      assertFalse(Scope.track(vec( -1)).isBinary(true));
      assertFalse(Scope.track(vec(1)).isBinary(true));
      assertFalse(Scope.track(vec(0)).isBinary(true));
      // Test Nas
      assertFalse(Scope.track(dvec(Double.NaN, Double.NaN)).isBinary(true));
      // NAs are ignored by default
      assertFalse(Scope.track(dvec(Double.NaN, 1)).isBinary(true));
      assertTrue(Scope.track(dvec(Double.NaN, 1, 0)).isBinary(true));
    } finally {
      Scope.exit();
    }
  }
  
}

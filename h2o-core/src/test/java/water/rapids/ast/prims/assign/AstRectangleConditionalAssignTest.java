package water.rapids.ast.prims.assign;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.MRTask;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AstRectangleConditionalAssignTest extends TestUtil {

  @BeforeClass static public void setup() { stall_till_cloudsize(1); }

  @Test public void testConditionalAssignNumber() {
    Frame fr = makeTestFrame();
    Vec expected = dvec(11.2, -1, 33.6, -1, 56.0);
    try {
      Val val = Rapids.exec("(tmp= py_1 (:= data -1 1 (== (cols_py data 4) \"a\")))");
      if (val instanceof ValFrame) {
        Frame fr2 = val.getFrame();
        assertVecEquals(expected, fr2.vec(1), 0.00001);
        fr2.remove();
      }
    } finally {
      fr.remove();
      expected.remove();
    }
  }

  @Test public void testConditionalAssignUUID() {
    UUID newUUID = UUID.randomUUID();
    Frame fr = makeTestFrame();
    Vec expected = uvec(new UUID(10, 1), newUUID, new UUID(30, 3), newUUID, new UUID(50, 5));
    try {
      Val val = Rapids.exec("(tmp= py_1 (:= data \"" + newUUID.toString() + "\" 2 (== (cols_py data 4) \"a\")))");
      if (val instanceof ValFrame) {
        Frame fr2 = val.getFrame();
        assertUUIDVecEquals(expected, fr2.vec(2));
        fr2.remove();
      }
    } finally {
      fr.remove();
      expected.remove();
    }
  }

  @Test public void testConditionalAssignString() {
    Frame fr = makeTestFrame();
    Vec expected = svec("row1", "tst", "row3", "tst", "row5");
    try {
      Val val = Rapids.exec("(tmp= py_1 (:= data \"tst\" 3 (== (cols_py data 4) \"a\")))");
      if (val instanceof ValFrame) {
        Frame fr2 = val.getFrame();
        assertStringVecEquals(expected, fr2.vec(3));
        fr2.remove();
      }
    } finally {
      fr.remove();
      expected.remove();
    }
  }

  @Test public void testConditionalAssignCategorical() {
    Frame fr = makeTestFrame();
    Vec expected = cvec(new String[]{"a", "b"}, "b", "b", "b", "b", "b");
    try {
      Val val = Rapids.exec("(tmp= py_1 (:= data \"b\" 4 (== (cols_py data 4) \"a\")))");
      if (val instanceof ValFrame) {
        Frame fr2 = val.getFrame();
        assertCatVecEquals(expected, fr2.vec(4));
        fr2.remove();
      }
    } finally {
      fr.remove();
      expected.remove();
    }
  }

  @Test public void testConditionalAssignDense() {
    Frame fr = makeTestFrame();
    Vec expected = ivec(-1, -1, -1, -1, -1);
    try {
      Val val = Rapids.exec("(tmp= py_1 (:= data -1 2 (> (cols_py data 0) 0)))");
      if (val instanceof ValFrame) {
        Frame fr2 = val.getFrame();
        assertVecEquals(expected, fr2.vec(2), 0.00001);
        fr2.remove();
      }
    } finally {
      fr.remove();
      expected.remove();
    }
  }

  @Test public void testWrongTypeAssignString() {
    Frame fr = makeTestFrame();
    try {
      Rapids.exec("(tmp= py_1 (:= data \"tst\" 1 (== (cols_py data 4) \"a\")))");
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException e) {
      assertEquals("Cannot assign value tst into a vector of type Numeric.", e.getMessage());
    } finally {
      fr.remove();
    }
  }

  @Test public void testWrongTypeAssignNum() {
    Frame fr = makeTestFrame();
    try {
      Rapids.exec("(tmp= py_1 (:= data 9 3 (== (cols_py data 4) \"a\")))");
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException e) {
      assertEquals("Cannot assign value 9.0 into a vector of type String.", e.getMessage());
    } finally {
      fr.remove();
    }
  }

  @Test public void testWrongCategoricalAssign() {
    Frame fr = makeTestFrame();
    try {
      Rapids.exec("(tmp= py_1 (:= data \"c\" 4 (== (cols_py data 4) \"a\")))");
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException e) {
      assertEquals("Cannot assign value c into a vector of type Enum.", e.getMessage());
    } finally {
      fr.remove();
    }
  }

  private Frame makeTestFrame() {
    Frame fr = null;
    Vec v = ivec(1, 2, 3, 4, 5);
    try {
      fr = new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          for (int i = 0; i < cs[0]._len; i++) {
            int r = (int) cs[0].atd(i);
            ncs[0].addNum(r);
            ncs[1].addNum(11.2 * r);
            ncs[2].addUUID(r, r * 10);
            ncs[3].addStr("row" + r);
            ncs[4].addCategorical(r % 2 == 0 ? 0 : 1);
          }
        }
      }.doAll(new byte[]{Vec.T_NUM, Vec.T_NUM, Vec.T_UUID, Vec.T_STR, Vec.T_CAT}, v).outputFrame(Key.make("data"),
              new String[]{"v1", "v2", "v3", "v4", "v5"},
              new String[][]{null, null, null, null, new String[]{"a", "b"}});
    } finally {
      v.remove();
    }
    assert fr != null;
    return fr;
  }

}

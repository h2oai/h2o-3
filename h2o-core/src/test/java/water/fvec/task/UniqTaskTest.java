package water.fvec.task;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class UniqTaskTest {
  
  @Test
  public void testAllUnique() {
    final int len = 1_000_000;
    Vec v = Vec.makeSeq(0, len);
    Vec result = null;
    try {
      UniqTask t = new UniqTask().doAll(v);
      double[] uniq = t.toArray();
      result = t.toVec();
      assertEquals(len, uniq.length);
      Vec.Reader reader = result.new Reader();
      for (int i = 0; i < len; i++) {
        assertEquals(uniq[i], reader.at(i), 0);
      }
      Arrays.sort(uniq);
      for (int i = 0; i < len; i++) {
        assertEquals(i, uniq[i], 0);
      }
    } finally {
      v.remove();
      if (result != null)
        result.remove();
    }
  }

  @Test
  public void testConstant() {
    final int len = 1_000_000;
    Vec v = Vec.makeZero(len);
    try {
      double[] uniq = new UniqTask().doAll(v).toArray();
      assertEquals(1, uniq.length);
    } finally {
      v.remove();
    }
  }

  @Test
  public void testNA() {
    Vec v = Vec.makeCon(Double.NaN, 1);
    assertTrue(v.isNA(0));
    try {
      double[] uniq = new UniqTask().doAll(v).toArray();
      assertEquals(1, uniq.length);
      assertTrue(Double.isNaN(uniq[0]));
    } finally {
      v.remove();
    }
  }

}

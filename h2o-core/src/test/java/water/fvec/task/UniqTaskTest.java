package water.fvec.task;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.fvec.Vec;
import water.rapids.ast.prims.mungers.AstGroup;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.IcedHashSet;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class UniqTaskTest extends TestCase {
  
  @Test
  public void testAllUnique() {
    final int expected = 1_000_000;
    Vec v = Vec.makeSeq(0, expected);
    try {
      IcedHashSet<AstGroup.G> uniq = new UniqTask().doAll(v)._uniq;
      assertEquals(expected, uniq.size());
    } finally {
      v.remove();
    }
  }

  @Test
  public void testConstant() {
    final int len = 1_000_000;
    Vec v = Vec.makeZero(len);
    try {
      IcedHashSet<AstGroup.G> uniq = new UniqTask().doAll(v)._uniq;
      assertEquals(1, uniq.size());
    } finally {
      v.remove();
    }
  }

  @Test
  public void testNA() {
    Vec v = Vec.makeCon(Double.NaN, 1);
    assertTrue(v.isNA(0));
    try {
      IcedHashSet<AstGroup.G> uniq = new UniqTask().doAll(v)._uniq;
      assertEquals(1, uniq.size());
    } finally {
      v.remove();
    }
  }

}

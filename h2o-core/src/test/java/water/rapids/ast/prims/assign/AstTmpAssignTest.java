package water.rapids.ast.prims.assign;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Rapids;
import water.rapids.Session;
import water.util.ArrayUtils;

import static org.junit.Assert.*;


/**
 */
public class AstTmpAssignTest extends TestUtil {

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test public void testDollarIds() {
    Frame f = null, v, w;
    try {
      Session sess = new Session();
      String expid1 = "id1~" + sess.id();
      f = ArrayUtils.frame(Key.<Frame>make(), ar("a", "b"), ard(1, -1), ard(2, 0), ard(3, 1));
      v = Rapids.exec("(, " + f._key + ")->$id1", sess).getFrame();
      w = DKV.get(expid1).get();
      assertArrayEquals(f._names, v._names);
      assertEquals(expid1, v._key.toString());
      assertEquals(expid1, new Env(sess).expand("$id1"));
      assertNotEquals(f._key, v._key);
      assertEquals(w, v);

      String expid2 = "foo~" + sess.id();
      Rapids.exec("(rename '$id1' '$foo')", sess);
      DKV.get(expid2).get();
      assertEquals(DKV.get(expid1), null);

      Rapids.exec("(rm $foo)", sess);
      assertEquals(DKV.get(expid2), null);
    } finally {
      if (f != null) f.delete();
    }
  }
}

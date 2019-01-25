package water.rapids.ast.prims.assign;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
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

  @Test public void assignSameId() {
    Scope.enter();
    try {
      Session session = new Session();
      String newid = new Env(session).expand("$frame1");
      Frame f = ArrayUtils.frame(Key.<Frame>make(), ar("a", "b"), ard(1, -1), ard(2, 0), ard(3, 1));
      Frame v = Rapids.exec("(tmp= $frame1 (, " + f._key + ")->$frame1)", session).getFrame();
      Frame w = DKV.get(newid).get();
      Scope.track(f, v);
      assertEquals(newid, v._key.toString());
      assertArrayEquals(f.names(), v.names());
      assertNotEquals(f._key, v._key);
      assertEquals(w, v);

      newid = new Env(session).expand("$f");
      v = Rapids.exec("(, (, $frame1)->$f)->$f", session).getFrame();
      Scope.track(v);
      assertEquals(newid, v._key.toString());

      newid = new Env(session).expand("$g");
      v = Rapids.exec("(colnames= (, $f)->$g [0 1] ['egg', 'ham'])->$g", session).getFrame();
      Scope.track(v);
      assertEquals(newid, v._key.toString());
      assertArrayEquals(new String[]{"egg", "ham"}, v.names());

    } finally {
      Scope.exit();
    }
  }
}

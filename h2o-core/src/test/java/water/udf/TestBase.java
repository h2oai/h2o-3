package water.udf;

import org.junit.After;
import org.junit.Before;
import water.Scope;
import water.TestUtil;
import water.fvec.NFSFileVec;
import water.fvec.Vec;

import java.io.File;

/**
 * All test functionality specific for udf (not actually), 
 * not kosher enough to be allowed for the general public
 */
public abstract class TestBase extends TestUtil {

  { // need this because -ea IS NOT ALWAYS set in intellij
    ClassLoader loader = getClass().getClassLoader();
    loader.setDefaultAssertionStatus(true);
  }

  abstract int cloudSize();
  
  @Before
  public void hi() {
    stall_till_cloudsize(cloudSize());
    Scope.enter();
  }

  @After
  public void bye() { Scope.exit(); }

  protected static Vec willDrop(Vec v) { return Scope.track(v); }

  protected static <T extends Vec.Holder> T willDrop(T vh) {
    Scope.track(vh.vec());
    return vh;
  }


  public static Vec loadFile(String fname) {
    File f = getFile(fname);
    return NFSFileVec.make(f);
  }

  public static File getFile(String fname) {
    File f = find_test_file_static(fname);
    checkFile(fname, f);
    return f;
  }

}

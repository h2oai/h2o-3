package water.udf;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import water.H2OStarter;
import water.Iced;
import water.Scope;
import water.TestUtil;
import water.fvec.NFSFileVec;
import water.fvec.Vec;

import java.io.File;
import java.lang.reflect.Method;
import static water.TestUtil.*;

/**
 * All test functionality specific for udf (not actually), 
 * not kosher enough to be allowed for the general public
 */
public class UdfTestBase extends TestUtil {

  {
    ClassLoader loader = getClass().getClassLoader();
    loader.setDefaultAssertionStatus(true);
  }
  
  int requiredCloudSize() { return 1; }
  
  @Before
  public void hi() {
    stall_till_cloudsize(requiredCloudSize());
    Scope.enter();
  }

  @After
  public void bye() { 
    Scope.exit(); 
  }

  protected static Vec willDrop(Vec v) { return Scope.track(v); }

  public static <T> T willDrop(T vh) {
    try { // using reflection so that Paula Bean's code is intact
      Method vec = vh.getClass().getMethod("vec");
      Scope.track((Vec)vec.invoke(vh));
    } catch (Exception e) {
      // just ignore
    }
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

  // the following code exists or else gradlew will complain; also, it checks assertions
  @Test
  public void testAssertionsEnabled() throws Exception {
    try {
      assert false : "Should throw";
      Assert.fail("Expected an assertion error");
    } catch(AssertionError ae) {
      // as designed
    }
  }

}

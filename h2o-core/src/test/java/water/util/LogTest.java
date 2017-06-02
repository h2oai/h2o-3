package water.util;

import org.junit.*;
import water.H2O;
import water.TestUtil;
import water.init.NetworkInit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class LogTest extends TestUtil {
  public LogTest() { super(1); }

  @Test public void testSetLevel() {
    // default log level is INFO
    Assert.assertEquals(Log.getCurrentLogLevel(), Log.Level.INFO);
    Log.setLevel(Log.Level.FATAL);
    Assert.assertEquals(Log.getCurrentLogLevel(), Log.Level.FATAL);
  }

  @Test public void testSetQuiet() {
    // default default quiet mode is false
    Assert.assertFalse(Log.getQuiet());
    Log.setQuiet(true);
    Assert.assertTrue(Log.getQuiet());
  }

  @Test public void testIsLoggerInitialized() {
    // logger is initialized after first logged messaged when we have SELF_ADDRESS

    // the logger is not initialized
    Assert.assertFalse(Log.isLoggerInitialized());

    // this logged message does not trigger the initialization since we don't
    // have SELF_ADDRESS set up
    Log.info("cached");

    simulateH2OStart();
    // the logger is still not yet initialized
    Assert.assertFalse(Log.isLoggerInitialized());

    // this logged message flushes previous logged messages as well and initializes the logging
    Log.info("test");

    Assert.assertTrue(Log.isLoggerInitialized());
  }

  

  private static void simulateH2OStart(){
    // set the ice dir
    try {
      // invoke the method using reflection, so it can remain private
      Method m = H2O.class.getDeclaredMethod("setIceRoot");
      m.setAccessible(true);
      m.invoke(null);

      m = H2O.class.getDeclaredMethod("setPID");
      m.setAccessible(true);
      m.invoke(null);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException("Can't happen, the setIceRoot method is available" + e);
    }
    // set the address
    H2O.SELF_ADDRESS = NetworkInit.findInetAddressForSelf();
  }
}

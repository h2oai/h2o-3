package water.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import water.H2O;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;

public class LogTest {

  @Test
  public void testSetLevel() {
    // default log level is INFO
    Assert.assertEquals(Log.getCurrentLogLevel(), Log.Level.INFO);
    Log.setLogLevel(Log.Level.FATAL);
    Assert.assertEquals(Log.getCurrentLogLevel(), Log.Level.FATAL);
  }

  @Test
  public void testSetQuiet() {
    // default default quiet mode is false
    Assert.assertFalse(Log.getQuiet());
    Log.setQuiet(true);
    Assert.assertTrue(Log.getQuiet());
  }

  @Test
  public void testIsLoggerInitialized() {
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

    // finally the logger is initialized
    Assert.assertTrue(Log.isLoggerInitialized());
  }

  @Test(expected = RuntimeException.class)
  public void testGetLogDirWithoutInit(){
    Log.getLogDir();
  }

  @Test
  public void testLogDirAfterInit(){
    simulateH2OStart();
    Log.info("Triggering init");
    Log.getLogDir();
  }

  @Test
  public void testIsLoggingEnabledFor(){
    Log.setLogLevel(Log.Level.INFO);
    Assert.assertFalse(Log.isLoggingFor(Log.Level.TRACE));
    Assert.assertTrue(Log.isLoggingFor(Log.Level.INFO));
    Assert.assertTrue(Log.isLoggingFor(Log.Level.WARN));
  }


  @Test
  public void testFlushStdout(){
    Log.info("first");
    Log.info("second");
    try {
      Field f = Log.class.getDeclaredField("initialMsgs");
      f.setAccessible(true);
      ArrayList<String> initialMsgs = (ArrayList<String>)f.get(null);
      Assert.assertEquals(initialMsgs.size(), 2); // there are 2 buffered messages
      Log.flushStdout();
      Assert.assertEquals(initialMsgs.size(), 0); // there are zero buffered messages
    } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new RuntimeException("Can't happen: " + e);
    }
  }

  @Test(expected = RuntimeException.class)
  public void testGetLogFileNamesFail(){
    // trying to get log file names without checking if the logging is initialized lead to
    // runtime exception
    Log.getLogFileNames();
  }

  @Test
  public void testGetLogFileNamesPositive(){
    simulateH2OStart();
    Log.info("Triggering init");
    Log.getLogFileNames();
  }

  @Test(expected = RuntimeException.class)
  public void testGetLogFilePathFailed(){
    // trying to get log file path without checking if the logging is initialized lead to
    // runtime exception
    Log.getLogFilePath(Log.Level.INFO);
  }

  @Test
  public void testGetLogFilePath(){
    simulateH2OStart();
    Log.info("Triggering init");
    String path = Log.getLogFilePath(Log.Level.INFO);
    try {
      Method m = Log.class.getDeclaredMethod("getLogFileNamePrefix");
      m.setAccessible(true);
      m.invoke(null);
      Assert.assertEquals(path, Log.getLogDir() + File.separator + m.invoke(null) + "-3-INFO.log");

    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException("Can't happen: " + e);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetLevelFromStringFailed(){
    Log.Level.fromString("invalid");
  }

  @Test
  public void testGetLevelFromString(){
    Assert.assertEquals(Log.Level.fromString("debug"), Log.Level.DEBUG);
  }

  private static void simulateH2OStart(){
    // set the ice dir
    try {
      // invoke the method using reflection, so it can remain private
      Method m = H2O.class.getDeclaredMethod("setIceRoot");
      m.setAccessible(true);
      m.invoke(null);
      
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException("Can't happen: " + e);
    }
    // set the address
    H2O.SELF_ADDRESS = InetAddress.getLoopbackAddress();
  }

  @Before
  public void setBackDefaults(){
    try {
      Field f = Log.class.getDeclaredField("logDir");
      f.setAccessible(true);
      f.set(null, null);

      f = Log.class.getDeclaredField("initialMsgs");
      f.setAccessible(true);
      f.set(null, new ArrayList<String>());

      f = Log.class.getDeclaredField("logger");
      f.setAccessible(true);
      f.set(null, null);

    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Can't happen: " + e);
    }
    H2O.SELF_ADDRESS = null;
    H2O.ICE_ROOT = null;
    //H2O.PID = -1L;
  }
}

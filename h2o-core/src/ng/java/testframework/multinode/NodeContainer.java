package testframework.multinode;

import java.io.File;
import java.lang.reflect.Method;
import java.net.*;

import water.util.Log;

/**
 * Creates a node either in a new JVM or in-process using a separate class loader.
 */
public class NodeContainer extends Thread {
  private final int _nodeNum;
  private final String[] _args;
  private final boolean _multiJvm;
  private final URLClassLoader _initialClassLoader, _classLoader;

  private volatile Process _process;
  private volatile boolean _runShutdownHook = false;

  /**
   * ShutdownHook for new JVM case.
   */
  private class ShutdownHook extends Thread {
    private final Process _p;
    public ShutdownHook(Process p) {
      _p = p;
    }
    public void run() {
      try {
        _p.destroy();
      }
      catch (Exception xe) {}
    }
  }

  public NodeContainer(int nodeNum, String[] args, boolean multiJvm) {
    super("NodeContainer");
    _nodeNum = nodeNum;
    _args = args;
    _multiJvm = multiJvm;
    _initialClassLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
    URL[] _classpath = _initialClassLoader.getURLs();
    _classLoader = new URLClassLoader(_classpath, null);
  }

  public void run() {
    if (_runShutdownHook) {
      System.out.println("Calling destroy() for node " + _nodeNum);
      _process.destroy();
      return;
    }

    if (_multiJvm) {
      // Create a node in a new JVM.

      // Calculate Xmx.  Round up to the next gigabyte.
      long ONE_GB = 1024L * 1024L * 1024L;
      long maxMemory = Runtime.getRuntime().maxMemory();
      long gb = (maxMemory + (ONE_GB - 1)) / ONE_GB;

      // Calculate Classpath
      StringBuilder sb = new StringBuilder();
      URL[] classpath = _initialClassLoader.getURLs();
      for (int i = 0; i < classpath.length; i++) {
        if (i > 0) {
          sb.append(":");
        }
        URL url = classpath[i];
        String s = url.toString();
        if (! s.startsWith("file:/")) {
          System.out.println("ERROR: NodeContainer url does not start with file:/ (" + s + ")");
          System.exit(1);
        }
        String s2 = s.substring("file:".length());
        sb.append(s2);
      }

      File buildDirectory = new File("build");
      if (! buildDirectory.exists()) {
        boolean success = buildDirectory.mkdir();
        if (! success) {
          System.out.println("ERROR: NodeContainer mkdir of build directory failed");
          System.exit(1);
        }
      }

      // System.out.println("java -ea -Xmx" + Xmx + " -cp " + sb + " water.H2O");
      String Xmx = "-Xmx" + gb + "g";
      ProcessBuilder pb = new ProcessBuilder("java", "-ea", Xmx, "-cp", sb.toString(), "water.H2O");
      pb.redirectErrorStream(true);
      pb.redirectOutput(new File("build/jvm_output_" + _nodeNum));
      try {
        _process = pb.start();
        _runShutdownHook = true;
        Runtime.getRuntime().addShutdownHook(new ShutdownHook(_process));
      }
      catch (Exception e) {
        System.out.println("ERROR: NodeContainer pb.start() failed");
        System.exit(1);
      }
    }
    else {
      // Create a node in-process using a separate class loader.
      assert Thread.currentThread().getContextClassLoader() == _initialClassLoader;
      Thread.currentThread().setContextClassLoader(_classLoader);

      try {
        Class<?> c = _classLoader.loadClass("water.H2O");
        Method method = c.getMethod("main", String[].class);
        method.setAccessible(true);
        method.invoke(null, (Object) _args);
      } catch (Exception e) {
        throw Log.throwErr(e);
      } finally {
        Thread.currentThread().setContextClassLoader(_initialClassLoader);
      }
    }
  }
}

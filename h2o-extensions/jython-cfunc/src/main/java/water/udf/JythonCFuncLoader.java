package water.udf;

import org.python.core.Py;
import org.python.core.PySystemState;

/**
 * Custom function loader, which can instantiate
 * functions written in Python.
 *
 * The provider internally uses Jython.
 *
 * Note: Jython caches the loaded python programs. That means
 * changing underlying function definition (i.e, Python code) is not
 * reflected!
 */
public class JythonCFuncLoader extends CFuncLoader {

  @Override
  public String getLang() {
    return "python";
  }

  @Override
  public <F> F load(String jfuncName, Class<? extends F> targetKlazz, ClassLoader classLoader) {
    int idxLastDot = jfuncName.lastIndexOf('.');
    String module = jfuncName.substring(0, idxLastDot);
    String clsName = jfuncName.substring(idxLastDot+1);

    ClassLoader savedCtxCl = Thread.currentThread().getContextClassLoader();
    PySystemState savedSystemState = Py.getSystemState(); // Get a system state for the current thread
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      PySystemState newSystemState = new PySystemState();
      newSystemState.setClassLoader(classLoader);
      Py.setSystemState(newSystemState); // Assign a new system state with a specific classloader to the current thread.
      return new JythonObjectFactory(targetKlazz, module, clsName).createObject();
    } finally {
      Py.setSystemState(savedSystemState);
      Thread.currentThread().setContextClassLoader(savedCtxCl);
    }
  }
}

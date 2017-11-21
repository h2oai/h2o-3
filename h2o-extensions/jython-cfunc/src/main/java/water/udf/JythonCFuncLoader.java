package water.udf;

import org.python.core.PySystemState;

/**
 * Custom function loader, which cna instantiate
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

    PySystemState pySystemState = new PySystemState();
    ClassLoader savedCtxCl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      return new JythonObjectFactory(pySystemState, targetKlazz, module, clsName).createObject();
    } finally {
      Thread.currentThread().setContextClassLoader(savedCtxCl);
    }
  }
}

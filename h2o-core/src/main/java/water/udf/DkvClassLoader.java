package water.udf;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import water.H2O;
import water.Key;
import water.Value;
import water.persist.PersistFS;
import water.util.Log;

/**
 * An URI-based classloader which use content of K/V
 * to search and load classes.
 *
 * Note: the classloader is using disk and a temporary
 * storage for K/V values!
 */
class DkvClassLoader extends URLClassLoader {

  public DkvClassLoader(CFuncRef cFuncRef, ClassLoader parent) {
    this(cFuncRef.keyName, parent);
  }

  public DkvClassLoader(String jarKeyName, ClassLoader parent) {
    this(Key.make(jarKeyName), parent);
  }
  
  public DkvClassLoader(Key jarKey, ClassLoader parent) {
    super(new URL[] {}, parent);
    Value v = water.DKV.get(jarKey);
    // Get local persistent layer and use it to save
    // content of K/V
    PersistFS persistFS = (PersistFS) H2O.getPM().getIce();
    File f = persistFS.getFile(v);
    // Optimistic delete
    f.deleteOnExit();
    try {
      Log.debug("DkvClassLoader: saving " + v + " into " + f.getAbsolutePath());
      H2O.getPM().store(Value.ICE, v);
      addURL(f.toURI().toURL());
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}

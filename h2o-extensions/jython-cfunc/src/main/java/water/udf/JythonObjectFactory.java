package water.udf;

import org.python.core.Py;
import org.python.core.PyArray;
import org.python.core.PyClass;
import org.python.core.PyObject;
import org.python.core.PySystemState;

public class JythonObjectFactory {

  private final Class interfaceType;
  private final PyObject klass;

  // Constructor obtains a reference to the importer, module, and the class name
  public JythonObjectFactory(Class interfaceType, String moduleName, String className) {
    this.interfaceType = interfaceType;
    PyObject importer = Py.getSystemState().getBuiltins().__getitem__(Py.newString("__import__"));
    PyObject module = importer.__call__(new PyObject[] {Py.newString(moduleName),  PyArray.zeros(1, String.class)}, new String[] {"fromlist"} );
    // Reload module definition - this is important to enable iterative updates of function definitions
    // from interactive environments
    module = org.python.core.__builtin__.reload(module);
    klass = module.__getattr__(className);
  }
  
  // All of the followng methods return
  // a coerced Jython object based upon the pieces of information
  // that were passed into the factory. The differences are
  // between them are the number of arguments that can be passed
  // in as arguents to the object.

  public <T> T createObject() {
    return (T) klass.__call__().__tojava__(interfaceType);
  }
}

package water.api;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import water.DKV;
import water.H2O;
import water.H2O.H2OCountedCompleter;
import water.Iced;
import water.Key;
import water.Keyed;
import water.Value;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2OKeyWrongTypeArgumentException;
import water.util.Log;
import water.util.ReflectionUtils;
import water.util.annotations.IgnoreJRERequirement;

public class Handler extends H2OCountedCompleter {
  protected Handler( ) { super(); }
  protected Handler( Handler completer ) { super(completer); }

  private long _t_start, _t_stop; // Start/Stop time in ms for the serve() call

  public static Class<? extends Schema> getHandlerMethodInputSchema(Method method) {
     return (Class<? extends Schema>)ReflectionUtils.findMethodParameterClass(method, 1);
  }

  public static Class<? extends Schema> getHandlerMethodOutputSchema(Method method) {
    return (Class<? extends Schema>)ReflectionUtils.findMethodOutputClass(method);
  }

  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  final Schema handle(int version, Route route, Properties parms) throws Exception {
    Class<? extends Schema> handler_schema_class = getHandlerMethodInputSchema(route._handler_method);
    Schema schema = Schema.newInstance(handler_schema_class);

    if (null == schema)
      throw H2O.fail("Failed to instantiate Schema of class: " + handler_schema_class + " for route: " + route);

    // If the schema has a real backing class fill from it to get the default field values:
    Class<? extends Iced> iced_class = schema.getImplClass();
    if (iced_class != Iced.class) {
      Iced defaults = schema.createImpl();
      schema.fillFromImpl(defaults);
    }

    // Fill from http request params:
    schema = schema.fillFromParms(parms);
    if (null == schema)
      throw H2O.fail("fillFromParms returned a null schema for version: " + version + " in: " + this.getClass() + " with params: " + parms);

    // Run the Handler in the Nano Thread (nano does not grok CPS!)
    // NOTE! The handler method is free to modify the input schema and hand it back.
    _t_start = System.currentTimeMillis();
    Schema result = null;
    try {
      result = (Schema)route._handler_method.invoke(this, version, schema);
    }
    // Exception throws out of the invoked method turn into InvocationTargetException
    // rather uselessly.  Peel out the original exception & throw it.
    catch( InvocationTargetException ite ) {
      Throwable t = ite.getCause();
      if( t instanceof RuntimeException ) throw (RuntimeException)t;
      if( t instanceof Error ) throw (Error)t;
      throw new RuntimeException(t);
    }
    _t_stop  = System.currentTimeMillis();

    // Version-specific unwind from the Iced back into the Schema
    return result;
  }

  @Override final protected void compute2() {
    throw H2O.fail();
  }

  @IgnoreJRERequirement
  protected StringBuffer markdown(Handler handler, int version, StringBuffer docs, String filename) {
    // TODO: version handling
    StringBuffer sb = new StringBuffer();
    Path path = Paths.get(filename);
    try {
      sb.append(Files.readAllBytes(path));
    }
    catch (IOException e) {
      Log.warn("Caught IOException trying to read doc file: ", path);
    }
    if (null != docs)
      docs.append(sb);
    return sb;
  }

  public static <T extends Keyed> T getFromDKV(String param_name, String key, Class<T> klazz) {
    return getFromDKV(param_name, Key.make(key), klazz);
  }
  public static <T extends Keyed> T getFromDKV(String param_name, Key key, Class<T> klazz) {
    if (null == key)
      throw new H2OIllegalArgumentException(param_name, "Handler.getFromDKV()", key);

    Value v = DKV.get(key);
    if (null == v)
      throw new H2OKeyNotFoundArgumentException(param_name, key.toString());

    Iced ice = v.get();
    if (! (klazz.isInstance(ice)))
      throw new H2OKeyWrongTypeArgumentException(param_name, key.toString(), klazz, ice.getClass());

    return (T) ice;
  }
}

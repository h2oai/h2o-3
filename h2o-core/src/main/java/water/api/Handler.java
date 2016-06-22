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

public class Handler extends H2OCountedCompleter<Handler> {

  public static Class<? extends Schema> getHandlerMethodInputSchema(Method method) {
     return (Class<? extends Schema>)ReflectionUtils.findMethodParameterClass(method, 1);
  }

  public static Class<? extends Schema> getHandlerMethodOutputSchema(Method method) {
    return (Class<? extends Schema>)ReflectionUtils.findMethodOutputClass(method);
  }

  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  Schema handle(int version, Route route, Properties parms) throws Exception {
    Class<? extends Schema> handler_schema_class = getHandlerMethodInputSchema(route._handler_method);
    Schema schema = Schema.newInstance(handler_schema_class);

    // If the schema has a real backing class fill from it to get the default field values:
    Class<? extends Iced> iced_class = schema.getImplClass();
    if (iced_class != Iced.class) {
      Iced defaults = schema.createImpl();
      schema.fillFromImpl(defaults);
    }

    // Fill from http request params:
    schema = schema.fillFromParms(parms);
    if (schema == null)
      throw H2O.fail("fillFromParms returned a null schema for version: " + version + " in: " + this.getClass() + " with params: " + parms);

    // NOTE! The handler method is free to modify the input schema and hand it back.
    Schema result = null;
    try {
      route._handler_method.setAccessible(true);
      result = (Schema)route._handler_method.invoke(this, version, schema);
    }
    // Exception thrown out of the invoked method turn into InvocationTargetException
    // rather uselessly.  Peel out the original exception & throw it.
    catch( InvocationTargetException ite ) {
      Throwable t = ite.getCause();
      if( t instanceof RuntimeException ) throw (RuntimeException)t;
      if( t instanceof Error ) throw (Error)t;
      throw new RuntimeException(t);
    }

    // Version-specific unwind from the Iced back into the Schema
    return result;
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
    if (docs != null)
      docs.append(sb);
    return sb;
  }

  public static <T extends Keyed> T getFromDKV(String param_name, String key, Class<T> klazz) {
    return getFromDKV(param_name, Key.make(key), klazz);
  }

  public static <T extends Keyed> T getFromDKV(String param_name, Key key, Class<T> klazz) {
    if (key == null)
      throw new H2OIllegalArgumentException(param_name, "Handler.getFromDKV()", "null");

    Value v = DKV.get(key);
    if (v == null)
      throw new H2OKeyNotFoundArgumentException(param_name, key.toString());

    try {
      return klazz.cast(v.get());
    } catch (ClassCastException e) {
      throw new H2OKeyWrongTypeArgumentException(param_name, key.toString(), klazz, v.get().getClass());
    }
  }
}

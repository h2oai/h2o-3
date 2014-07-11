package water.api;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import water.H2O;
import water.H2O.H2OCountedCompleter;
import water.Iced;
import water.api.RequestServer.Route;

public abstract class Handler<I extends Iced, S extends Schema<I,S>> extends H2OCountedCompleter {
  protected Handler( ) { super(); }
  protected Handler( Handler completer ) { super(completer); }

  private long _t_start, _t_stop; // Start/Stop time in ms for the serve() call

  /** Dumb Version->Schema mapping */
  abstract protected S schema(int version);
  abstract protected int min_ver();
  abstract protected int max_ver();

  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  final Schema handle(int version, Route route, Properties parms) throws Exception {

    if( !(min_ver() <= version && version <= max_ver()) ) // Version check!
      return new HttpErrorV1(new IllegalArgumentException("Version "+version+" is not in range V"+min_ver()+"-V"+max_ver()));

    // Make a version-specific Schema; primitive-parse the URL into the Schema,
    // then fill the Iced from the versioned Schema.
    S schema = schema(version);
    if (null == schema)
      throw H2O.fail("Failed to find a schema for version: " + version + " in: " + this.getClass());

    // Fill a Schema from the request params
    schema = schema.fillFromParms(parms);
    if (null == schema)
      throw H2O.fail("fillFromParms returned a null schema for version: " + version + " in: " + this.getClass() + " with params: " + parms);

    // Fill an impl object from the schema
    final I i = schema.createImpl();  // NOTE: it's ok to get a null implementation object
                                      // (as long as handler_method knows what to do with it).

    // Run the Handler in the Nano Thread (nano does not grok CPS!)
    _t_start = System.currentTimeMillis();
    Schema result = null;
    try { result = (Schema)route._handler_method.invoke(this, version, i); }
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
}

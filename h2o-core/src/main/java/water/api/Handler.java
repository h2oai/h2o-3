package water.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import water.H2O.H2OCountedCompleter;
import water.api.RequestServer.Route;

public abstract class Handler<H extends Handler<H,S>,S extends Schema<H,S>> extends H2OCountedCompleter {
  protected Handler( ) { super(); }
  protected Handler( Handler completer ) { super(completer); }

  private long _t_start, _t_stop; // Start/Stop time in ms for the serve() call

  /** Dumb Version->Schema mapping */
  abstract protected S schema(int version);
  abstract protected int min_ver();
  abstract protected int max_ver();

  private static final Properties NO_PROPERTIES = new Properties();

  protected int version = -1; // allow handlers to know the version
  protected Route route = null; // allow handlers to know the route, for error messages and such (TODO: remove?)
  protected Properties parms = NO_PROPERTIES;

  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  final Schema handle(int version, Route route, Properties parms) throws Exception {
    this.version = version;
    this.route = route;
    this.parms = parms;

    if( !(min_ver() <= version && version <= max_ver()) ) // Version check!
      return new HTTP500V1(new IllegalArgumentException("Version "+version+" is not in range V"+min_ver()+"-V"+max_ver()));

    // Make a version-specific Schema; primitive-parse the URL into the Schema,
    // fill the Handler from the versioned Schema.
    S s = schema(version).fillFrom(parms).fillInto((H)this); // Version-specific Schema

    // Run the Handler in the Nano Thread (nano does not grok CPS!)
    _t_start = System.currentTimeMillis();
    try { route._handler_method.invoke(this); }
    // Exception throws out of the invoked method turn into InvocationTargetException
    // rather uselessly.  Peel out the original exception & throw it.
    catch( InvocationTargetException ite ) {
      Throwable t = ite.getCause();
      if( t instanceof RuntimeException ) throw (RuntimeException)t;
      if( t instanceof Error ) throw (Error)t;
      throw new RuntimeException(t);
    }
    _t_stop  = System.currentTimeMillis();

    // Version-specific unwind from the Handler back into the Schema
    return s.fillFrom((H)this);
  }


}

package water.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import water.H2O.H2OCountedCompleter;
import water.schemas.HTTP500V1;
import water.schemas.Schema;

public abstract class Handler<H extends Handler<H,S>,S extends Schema<H,S>> extends H2OCountedCompleter {
  public Handler( ) { super(); }
  public Handler( Handler completer ) { super(completer); }

  private long _t_start, _t_stop; // Start/Stop time in ms for the serve() call

  /** Default supported versions: Version 2 onwards, not Version 1.  Override
   *  in child handlers to e.g. support V1. */
  protected int min_ver() { return 2; }
  protected int max_ver() { return Integer.MAX_VALUE; }

  /** Dumb Version->Schema mapping */
  abstract protected S schema(int version);

  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  protected final Schema handle(int version, Method meth, Properties parms) throws Exception {
    if( !(min_ver() <= version && version <= max_ver()) ) // Version check!
      return new HTTP500V1(new IllegalArgumentException("Version "+version+" is not in range V"+min_ver()+"-V"+max_ver()));

    // Make a version-specific Schema; primitive-parse the URL into the Schema,
    // fill the Handler from the versioned Schema.
    S s = schema(version).fillFrom(parms).fillInto((H)this); // Version-specific Schema


    // Run the Handler in the Nano Thread (nano does not grok CPS!)
    _t_start = System.currentTimeMillis();
    try { meth.invoke(this); }
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

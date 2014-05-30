package water.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import water.H2O.H2OCountedCompleter;
import water.H2O;
import water.schemas.HTTP500V1;
import water.schemas.Schema;
import water.util.Log;

public abstract class Handler<H extends Handler<H,S>,S extends Schema<H,S>> extends H2OCountedCompleter {
  private long _t_start, _t_stop; // Start/Stop time in ms for the serve() call

  /** Default supported versions: Version 2 onwards, not Version 1.  Override
   *  in child handlers to e.g. support V1. */
  protected int min_ver() { return 2; }
  protected int max_ver() { return Integer.MAX_VALUE; }

  /** Dumb Version->Schema mapping */
  abstract protected S schema(int version);

  protected final Schema handle(int version, Method meth, Properties parms) throws IllegalAccessException, InvocationTargetException {
    if( !(min_ver() <= version && version <= max_ver()) ) // Version check!
      return new HTTP500V1(new IllegalArgumentException("Version "+version+" is not in range V"+min_ver()+"-V"+max_ver()));

    // Make a version-specific Schema; primitive-parse the URL into the Schema,
    // fill the Handler from the versioned Schema.
    S s = schema(version).fillFrom(parms).fillInto((H)this); // Version-specific Schema


    // Run the Handler in the Nano Thread (nano does not grok CPS!)
    _t_start = System.currentTimeMillis();
    meth.invoke(this);
    _t_stop  = System.currentTimeMillis();

    // Version-specific unwind from the Handler back into the Schema
    return s.fillFrom((H)this);
  }


}

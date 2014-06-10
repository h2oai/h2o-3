package water.api;

import java.util.Arrays;
import water.H2O;

class HTTP500V1 extends Schema {
  final String error;
  final String stackTrace;

  HTTP500V1( Exception e ) { 
    error = e.getClass().getSimpleName()+": "+e.getMessage();
    stackTrace = Arrays.toString(e.getStackTrace());
  }
  @Override protected HTTP500V1 fillInto( Handler h ) { throw H2O.fail(); }
  @Override protected HTTP500V1 fillFrom( Handler h ) { throw H2O.fail(); }
}

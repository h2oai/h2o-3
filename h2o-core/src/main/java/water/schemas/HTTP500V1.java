package water.schemas;

import java.util.Arrays;
import water.H2O;
import water.api.Handler;

public class HTTP500V1 extends Schema {
  final String error;
  final String stackTrace;

  public HTTP500V1( Exception e ) { 
    error = e.getClass().getSimpleName()+": "+e.getMessage();
    stackTrace = Arrays.toString(e.getStackTrace());
  }
  @Override public HTTP500V1 fillInto( Handler h ) { throw H2O.fail(); }
  @Override public HTTP500V1 fillFrom( Handler h ) { throw H2O.fail(); }
}

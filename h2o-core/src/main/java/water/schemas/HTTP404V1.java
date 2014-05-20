package water.schemas;

import java.util.Arrays;
import water.H2O;
import water.api.Handler;

public class HTTP404V1 extends Schema {
  final String error;

  public HTTP404V1( String url ) { 
    error = url;
  }
  @Override public HTTP404V1 fillInto( Handler h ) { throw H2O.fail(); }
  @Override public HTTP404V1 fillFrom( Handler h ) { throw H2O.fail(); }
}

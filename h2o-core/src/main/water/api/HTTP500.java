package water.api;

import java.util.Properties;

public class HTTP500 extends Request {
  Properties _parms;
  @Override protected Response checkArguments(Properties parms) { _parms = parms; return null; }
  @Override protected Response serve() { return throwIAE("Unknown request: "+_parms); }
}

package water.api;

import java.util.Properties;

public class HTTP404 extends Request {
  Properties _parms;
  @Override public Response checkArguments(Properties parms) { _parms = parms; return null; }
  @Override public Response serve() { return throwIAE("Unknown parms: "+_parms); }
}

package water.api;

import java.util.*;
import water.*;
import water.api.RequestServer.API_VERSION;

public class Cloud extends Request {
  // Inputs
  // NONE!!!

  // Output
  private H2ONode _nodes[];

  @Override public API_VERSION[] supportedVersions() { return SUPPORTS_V1_V2; }

  // MAPPING from URI ==> POJO
  // 
  // THIS IS THE WRONG ARCHITECTURE....  either this call should be auto-gened
  // (auto-gen moves parms into local fields & complains on errors) or 
  // it needs to move into an explicit Schema somehow.
  @Override public Response checkArguments(Properties parms) {
    Enumeration<String> e = (Enumeration<String>)parms.propertyNames(); 
    return e.hasMoreElements() ? throwIAE("unknown parameter: "+e.nextElement()) : null;
  }


  @Override public Response serve() {
    _nodes = H2O.CLOUD.members();

    return new Response("Cloud");
  }
}


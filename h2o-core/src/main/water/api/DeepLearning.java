package water.api;

import hex.deeplearning.*;
import java.util.*;
import water.*;
import water.api.RequestServer.API_VERSION;
import water.util.RString;

public class DeepLearning extends Request {
  // Inputs
  private Key _src; // Key holding final value after job is removed

  // Output
  private Key _job; // Boolean read-only value; exists==>running, not-exists==>canceled/removed

  @Override protected API_VERSION[] supportedVersions() { return SUPPORTS_ONLY_V2; }

  // MAPPING from URI ==> POJO
  // 
  // THIS IS THE WRONG ARCHITECTURE....  either this call should be auto-gened
  // (auto-gen moves parms into local fields & complains on errors) or 
  // it needs to move into an explicit Schema somehow.
  @Override protected Response checkArguments(Properties parms) {
    for( Enumeration<String> e = (Enumeration<String>)parms.propertyNames(); e.hasMoreElements(); ) {
      String prop = e.nextElement();
      if( false ) {
      } else if( prop.equals("src") ) { 
        _src = Key.make(parms.getProperty(prop));
      } else {
        return throwIAE("unknown parameter: "+prop);
      }
    }
    if( _src == null ) return throwIAE("Missing 'hex'");
    return null;                // Happy happy
  }

  @Override protected Response serve() {
    hex.deeplearning.DeepLearning dl = new hex.deeplearning.DeepLearning(Key.make("DeepLearn_Model"));
    dl.source = DKV.get(_src).get();
    dl.classification = true;
    dl.response = dl.source.lastVec();
    dl.execImpl();
    DeepLearningModel dlm = DKV.get(dl.dest()).get();
    return new Response("Deep Learning done: "+dl._key+", "+dlm.error());
  }
}

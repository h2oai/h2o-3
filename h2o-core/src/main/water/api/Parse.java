package water.api;

import java.util.Properties;
import water.*;
import water.api.RequestServer.API_VERSION;
import water.util.RString;

public class Parse extends Request {
  private Key _job; // Boolean read-only value; exists==>running, not-exists==>canceled/removed
  private Key _hex; // Key holding final value after job is removed
  private boolean _delete_on_done = true;
  private boolean _blocking = true;

  @Override protected API_VERSION[] supportedVersions() { return SUPPORTS_V1_V2; }

  // MAPPING from URI ==> POJO
  // 
  // THIS IS THE WRONG ARCHITECTURE....  either this call should be auto-gened
  // (auto-gen moves parms into local fields & complains on errors) or 
  // it needs to move into an explicit Schema somehow.
  @Override protected Response checkArguments(Properties parms) {
    //for( Enumeration<String> e = (Enumeration<String>)parms.propertyNames(); e.hasMoreElements(); ) {
    //  String prop = e.nextElement();
    //  if( prop.equals("path") ) {
    //    _path = parms.getProperty("path");
    //  } else {
    //    return new Response(new IllegalArgumentException("unknown parameter: "+prop));
    //  }
    //}
    //return _path == null ? new Response(new IllegalArgumentException("Missing 'path'")) : null;
    throw H2O.unimpl();
  }


  @Override protected Response serve() {
    //PSetup p = _source.value();
    //CustomParser.ParserSetup setup = p != null?p._setup._setup:new CustomParser.ParserSetup();
    //setup._singleQuotes = _sQuotes.value();
    //destination_key = Key.make(_dest.value());
    //// Make a new Setup, with the 'header' flag set according to user wishes.
    //Key[] keys = p._keys.toArray(new Key[p._keys.size()]);
    //Job parseJob = ParseDataset2.forkParseDataset(destination_key, keys, setup, delete_on_done.value());
    //job_key = parseJob.self();
    //// Allow the user to specify whether to block synchronously for a response or not.
    //if (_blocking.value()) {
    //  parseJob.get(); // block until the end of job
    //  assert Job.isEnded(job_key) : "Job is still running but we already passed over its end. Job = " + job_key;
    //}
    //return Progress2.redirect(this,job_key,destination_key);
    throw H2O.unimpl();
  }

  //public static String link(String k, String content) {
  //  RString rs = new RString("<a href='Parse2.query?source_key=%key'>%content</a>");
  //  rs.replace("key", k.toString());
  //  rs.replace("content", content);
  //  return rs.toString();
  //}

}

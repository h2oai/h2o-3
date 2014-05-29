package water.api;

import java.util.*;
import water.*;
import water.schemas.ParseV2;
import water.util.RString;

public class Parse extends Handler {
  // Inputs
  private Key _hex; // Key holding final value after job is removed
  private Key[] _srcs;          // Source keys
  private boolean _delete_on_done = true;
  private boolean _blocking = true;

  // Output
  private Key _job; // Boolean read-only value; exists==>running, not-exists==>canceled/removed

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  @Override protected void GET() {
    water.parser.ParseDataset2.parse(_hex,_srcs);
    throw H2O.unimpl();
    //return new Response("<a href=/2/Inspect.html?src="+_hex+">Parse done!</a>");
    //return new Response("<a href=/2/DeepLearning.html?src="+_hex+">Parse done!</a>");
  }

  //public static String link(String k, String content) {
  //  RString rs = new RString("<a href='Parse2.query?source_key=%key'>%content</a>");
  //  rs.replace("key", k.toString());
  //  rs.replace("content", content);
  //  return rs.toString();
  //}

  // Parse Schemas are at V2
  @Override protected ParseV2 schema(int version) { return new ParseV2(); }
}

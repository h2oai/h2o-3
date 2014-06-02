package water.api;

import water.*;
import water.schemas.ParseV2;
import water.util.RString;

public class Parse extends Handler<Parse,ParseV2> {
  // Inputs
  public Key _hex;              // Key holding final value after job is removed
  public Key[] _srcs;           // Source keys
  public boolean _delete_on_done = true;
  public boolean _blocking = true;
  
  // Output
  public Key _job; // Boolean read-only value; exists==>running, not-exists==>canceled/removed

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  // Entry point for parsing.
  protected void parse() {
    water.parser.ParseDataset2.parse(_hex,_srcs);
    //return new Response("<a href=/2/DeepLearning.html?src="+_hex+">Parse done!</a>");
  }

  // Parse Schemas are at V2
  @Override protected ParseV2 schema(int version) { return new ParseV2(); }
}

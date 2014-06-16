package water.api;

import water.*;
import water.parser.ParseDataset2;

class ParseHandler extends Handler<ParseHandler,ParseV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // Inputs
  Key _hex;                     // Key holding final value after job is removed
  Key[] _srcs;                  // Source keys
  boolean _delete_on_done = true;
  boolean _blocking = true;
  
  // Output
  ParseDataset2 _job; // Boolean read-only value; exists==>running, not-exists==>canceled/removed

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override protected void compute2() { throw H2O.fail(); }

  // Entry point for parsing.
  void parse() {
    _job = water.parser.ParseDataset2.startParse(_hex,_srcs);
  }

  // Parse Schemas are at V2
  @Override protected ParseV2 schema(int version) { return new ParseV2(); }
}

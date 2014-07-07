package water.api;

import water.*;
import water.api.ParseHandler.Parse;
import water.parser.ParseSetup;
import water.parser.*;

class ParseHandler extends Handler<Parse,ParseV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  protected static final class Parse extends Iced {
    // Inputs
    Key _hex;                     // Key holding final value after job is removed
    Key[] _srcs;                  // Source keys
    ParserType _pType;            // CSV vs XLS vs Auto
    byte _sep;                    // Field separator, -1 for auto
    int _ncols;                   // Number of columns to expect
    boolean _singleQuotes;        // Single quotes a valid char, or not
    String[] _columnNames;        // Column names to use
    int _checkHeader;             // Parse 1st line as header, or not.

    boolean _delete_on_done = true;
    boolean _blocking = true;

    // Output
    ParseDataset2 _job; // Boolean read-only value; exists==>running, not-exists==>canceled/removed
  }

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override protected void compute2() { throw H2O.fail(); }

  // Entry point for parsing.
  ParseV2 parse(int version, Parse parse) {
    ParseSetup setup = new ParseSetup(true,0,null,parse._pType,parse._sep,parse._ncols,parse._singleQuotes,parse._columnNames,null,parse._checkHeader);
    parse._job = water.parser.ParseDataset2.startParse2(parse._hex,parse._srcs,parse._delete_on_done,setup);
    return schema(version).fillFromImpl(parse);
  }

  // Parse Schemas are at V2
  @Override protected ParseV2 schema(int version) { return new ParseV2(); }
}

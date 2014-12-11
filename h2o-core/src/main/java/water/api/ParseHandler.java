package water.api;

import water.Job;
import water.Key;
import water.parser.ParseSetup;

class ParseHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // Entry point for parsing.
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ParseV2 parse(int version, ParseV2 parse) {
    ParseSetup setup = new ParseSetup(true, 0, 0, null, parse.pType, parse.sep, parse.ncols, parse.singleQuotes, parse.columnNames, parse.domains, null, parse.checkHeader, null);

    Key[] srcs = new Key[parse.srcs.length];
    for (int i = 0; i < parse.srcs.length; i++)
      srcs[i] = parse.srcs[i].key.key();

    // TODO: add JobBase:
    parse.job = (JobV2)Schema.schema(version, Job.class).fillFromImpl(water.parser.ParseDataset2.startParse2(parse.hex.key(), srcs, parse.delete_on_done, setup));
    return parse;
  }
}

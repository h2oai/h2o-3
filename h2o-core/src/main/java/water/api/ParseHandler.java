package water.api;

import water.DKV;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.parser.ParseDataset;
import water.parser.ParseSetup;

class ParseHandler extends Handler {
  // Entry point for parsing.
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ParseV3 parse(int version, ParseV3 parse) {
    ParseSetup setup = new ParseSetup(parse.parse_type, parse.separator, parse.single_quotes, parse.check_header, parse.number_columns, delNulls(parse.column_names), ParseSetup.strToColumnTypes(parse.column_types), parse.domains, parse.na_strings, null, parse.chunk_size);

    Key[] srcs = new Key[parse.source_frames.length];
    for (int i = 0; i < parse.source_frames.length; i++)
      srcs[i] = parse.source_frames[i].key();

    // TODO: add JobBase:
    parse.job = (JobV3)Schema.schema(version, Job.class).fillFromImpl(ParseDataset.parse(parse.destination_frame.key(), srcs, parse.delete_on_done, setup, parse.blocking));
    if( parse.blocking ) {
      Frame fr = DKV.getGet(parse.destination_frame.key());
      parse.rows = fr.numRows();
    }
    return parse;
  }

  String[] delNulls(String[] names) {
    if (names == null) return null;
    for(int i=0; i < names.length; i++)
      if (names[i].equals("null")) names[i] = null;
    return names;
  }
}

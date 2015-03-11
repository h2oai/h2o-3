package water.api;

import water.DKV;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.api.KeyV1.VecKeyV1;

class ParseHandler extends Handler {
  // Entry point for parsing.
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ParseV2 parse(int version, ParseV2 parse) {
    ParseSetup setup = new ParseSetup(true, 0, 0, null, parse.parse_type, parse.separator, parse.single_quotes, parse.check_header, parse.number_columns, parse.column_names, ParseSetup.strToColumnTypes(parse.column_types), parse.domains, parse.na_strings, null, parse.chunk_size);

    Key[] srcs = new Key[parse.source_keys.length];
    for (int i = 0; i < parse.source_keys.length; i++)
      srcs[i] = parse.source_keys[i].key();

    // TODO: add JobBase:
    parse.job = (JobV2)Schema.schema(version, Job.class).fillFromImpl(ParseDataset.parse(parse.destination_key.key(), srcs, parse.delete_on_done, setup, parse.blocking));
    if( parse.blocking ) {
      Frame fr = DKV.getGet(parse.destination_key.key());
      parse.rows = fr.numRows();
      if( parse.remove_frame ) {
        Key[] keys = fr.keys();
        if(keys != null && keys.length > 0) {
          parse.vec_keys = new VecKeyV1[keys.length];
          for (int i = 0; i < keys.length; i++)
            parse.vec_keys[i] = new VecKeyV1(keys[i]);
        }
        // parse.vecKeys = new VecKeyV1(fr.keys());
        fr.restructure(new String[0],new Vec[0]);
        fr.delete();
      }
    }
    return parse;
  }
}

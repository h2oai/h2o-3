package water.api;

import water.DKV;
import water.Key;
import water.parser.ParseSetup;
import water.util.PojoUtils;

import java.util.Arrays;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
public class ParseSetupHandler extends Handler {

  public ParseSetupV2 guessSetup(int version, ParseSetupV2 p) {
    Key[] fkeys = new Key[p.source_keys.length];
    for(int i=0; i < p.source_keys.length; i++) {
      fkeys[i] = p.source_keys[i].key();
      if (DKV.get(fkeys[i]) == null) throw new IllegalArgumentException("Key not loaded: "+ p.source_keys[i]);
    }

    ParseSetup ps = ParseSetup.guessSetup(fkeys, new ParseSetup(p));

    // TODO: ParseSetup throws away the srcs list. . .
    PojoUtils.copyProperties(p, ps, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[] { "destination_key", "source_keys", "column_types"});
    p.destination_key = ParseSetup.hex(p.source_keys[0].toString());
    if( p.check_header==1 ) p.data = Arrays.copyOfRange(p.data,1,p.data.length-1); // Drop header from the preview data

    // Fill in data type names for each column.
    p.column_types = ps.getColumnTypeStrings();

    return p;
  }
}

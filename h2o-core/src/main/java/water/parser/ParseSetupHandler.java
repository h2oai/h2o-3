package water.parser;

import water.DKV;
import water.api.Handler;
import water.Key;
import water.util.PojoUtils;

import java.util.Arrays;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
public class ParseSetupHandler extends Handler {

  public ParseSetupV2 guessSetup(int version, ParseSetupV2 p) {
    Key[] fkeys = new Key[p.srcs.length];
    for(int i=0; i < p.srcs.length; i++) {
      fkeys[i] = p.srcs[i].key();
      if (DKV.get(fkeys[i]) == null) throw new IllegalArgumentException("Key not loaded: "+ p.srcs[i]);
    }
    ParseSetup ps = ParseSetup.guessSetup(fkeys, new ParseSetup(p.singleQuotes, p.checkHeader));

    // TODO: ParseSetup throws away the srcs list. . .
    PojoUtils.copyProperties(p, ps, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[] { "hex", "srcs" });

    p.columnNames = ps._columnNames == null ? ParseDataset.genericColumnNames(p.ncols) : ps._columnNames;
    p.hexName = ParseSetup.hex(p.srcs[0].toString());
    if( p.checkHeader==1 ) p.data = Arrays.copyOfRange(p.data,1,p.data.length-1); // Drop header from the preview data

    // Fill in data type names for each column.
    if (ps._ctypes != null) {
      p.columnDataTypes = new String[ps._ctypes.length];
      for (int i = 0; i < ps._ctypes.length; i++) {
        p.columnDataTypes[i] = ps._ctypes[i].toString();
      }
    }

    return p;
  }

  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
}

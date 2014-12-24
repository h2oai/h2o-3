package water.parser;

import water.DKV;
import water.api.Handler;
import water.util.PojoUtils;

import java.util.Arrays;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
public class ParseSetupHandler extends Handler {

  public ParseSetupV2 guessSetup(int version, ParseSetupV2 p) {
    if( DKV.get(p.srcs[0].key()) == null ) throw new IllegalArgumentException("Key not loaded: "+p.srcs[0]);
    byte[] bits = ZipUtil.getFirstUnzippedBytes(ParseDataset.getByteVec(p.srcs[0].key()));
    ParseSetup ps = ParseSetup.guessSetup(bits, p.singleQuotes, p.checkHeader);

    // TODO: ParseSetup throws away the srcs list. . .
    PojoUtils.copyProperties(p, ps, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[] { "hex", "srcs" });

    p.columnNames = ps._columnNames == null ? ParseDataset.genericColumnNames(p.ncols) : ps._columnNames;
    p.hexName = ParseSetup.hex(p.srcs[0].toString());
    if( p.checkHeader==1 ) p.data = Arrays.copyOfRange(p.data,1,p.data.length-1); // Drop header from the preview data

    return p;
  }

  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
}

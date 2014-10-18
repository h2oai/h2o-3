package water.parser;

import water.DKV;
import water.api.Handler;

import java.util.Arrays;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
public class ParseSetupHandler extends Handler<ParseSetup,ParseSetupV2> {

  // back to the handler!
  public ParseSetupV2 guessSetup(int version, ParseSetup p ) {
    if( DKV.get(p._srcs[0]) == null ) throw new IllegalArgumentException("Key not loaded: "+p._srcs[0]);
    byte[] bits = ZipUtil.getFirstUnzippedBytes(ParseDataset2.getByteVec(p._srcs[0]));
    ParseSetup ps = ParseSetup.guessSetup(bits, p._singleQuotes, p._checkHeader);
    // Update in-place
    assert ps._checkHeader != 0; // Need to fill in the guess
    p._checkHeader = ps._checkHeader;
    p._hexName = ParseSetup.hex(p._srcs[0].toString());
    p._pType = ps._pType;
    p._sep = ps._sep;
    p._ncols = ps._ncols;
    p._columnNames = ps._columnNames == null ? ParseDataset2.genericColumnNames(p._ncols) : ps._columnNames;
    p._data = ps._data;
    if( p._checkHeader==1 ) p._data = Arrays.copyOfRange(p._data,1,p._data.length-1); // Drop header from the preview data
    p._isValid = ps._isValid;
    p._invalidLines = ps._invalidLines;
    return schema(version).fillFromImpl(p);
  }

  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  // ParseSetup Schemas are at V2
  @Override protected ParseSetupV2 schema(int version) { return new ParseSetupV2(); }
}

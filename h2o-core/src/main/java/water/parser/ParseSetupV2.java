package water.parser;

import water.Key;
import water.api.API;
import water.api.KeyV1.FrameKeyV1;
import water.api.Schema;
import water.util.DocGen.HTML;

import java.util.Arrays;

public class ParseSetupV2 extends Schema<ParseSetup,ParseSetupV2> {

  // Input fields
  @API(help="Source keys",required=true, direction=API.Direction.INPUT)
  public FrameKeyV1[] srcs;

  @API(help="Check header: 0 means guess, +1 means 1st line is header not data, -1 means 1st line is data not header",direction=API.Direction.INOUT)
  public int checkHeader;

  @API(help="Single quotes",direction=API.Direction.INPUT)
  public boolean singleQuotes;

  // Output fields
  @API(help="Suggested name", direction=API.Direction.OUTPUT)
  public String hexName;

  @API(help="Parser Type", direction=API.Direction.OUTPUT, values = {"AUTO", "ARFF", "XLS", "XLSX", "CSV", "SVMLight"})
  public ParserType pType;

  @API(help="Field separator", direction=API.Direction.OUTPUT)
  public byte sep;

  @API(help="Number of columns", direction=API.Direction.OUTPUT)
  public int ncols;

  @API(help="Column Names",direction=API.Direction.OUTPUT)
  public String[] columnNames;

  @API(help="Column Data Types",direction=API.Direction.OUTPUT)
  public String[] columnTypes;

  @API(help="Sample Data", direction=API.Direction.OUTPUT)
  public String[][] data;

  @API(help="The initial parse is sane", direction=API.Direction.OUTPUT)
  boolean isValid;

  @API(help="Number of broken/invalid lines found", direction=API.Direction.OUTPUT)
  long invalidLines;

  @API(help="Number of header lines found", direction=API.Direction.OUTPUT)
  long headerlines;

  @API(help="Size of individual parse tasks", direction=API.Direction.OUTPUT)
  int chunkSize;


  //==========================
  // Helper so ImportV1 can link to ParseSetupV2
  static public String link(String[] keys) {
    return "ParseSetup?srcs="+Arrays.toString(keys);
  }


  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("ParseSetup");
    if (null != srcs && srcs.length > 0) {
      Key[] srcs_key = new Key[srcs.length];
      for (int i = 0; i < srcs.length; i++)
        srcs_key[i] = srcs[i].key();
      ab.href("Parse", srcs[0].toString(), water.api.ParseV2.link(srcs_key, hexName, pType, sep, ncols, checkHeader, singleQuotes, columnNames));
    } else {
      Key[] srcs_key = new Key[srcs.length];
      for (int i = 0; i < srcs.length; i++)
        srcs_key[i] = srcs[i].key();
      ab.href("Parse", "unknown", water.api.ParseV2.link(srcs_key, hexName, pType, sep, ncols, checkHeader, singleQuotes, columnNames));
    }
    ab.putA( "srcs", srcs);
    ab.putStr( "hexName", hexName);
    ab.putEnum("pType",pType);
    ab.put1("sep",sep);
    ab.put4("ncols",ncols);
    ab.putZ("singleQuotes",singleQuotes);
    ab.putAStr("columnNames",columnNames);
    ab.putAAStr("data",data);
    return ab;
  }
}

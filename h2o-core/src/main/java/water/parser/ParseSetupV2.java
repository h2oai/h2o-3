package water.parser;

import water.Key;
import water.api.API;
import water.api.Schema;
import water.util.DocGen.HTML;

import java.util.Arrays;

public class ParseSetupV2 extends Schema<ParseSetup,ParseSetupV2> {

  // Input fields
  @API(help="Source keys",required=true, direction=API.Direction.INPUT)
  public Key[] srcs;

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

  @API(help="Sample Data", direction=API.Direction.OUTPUT)
  public String[][] data;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public ParseSetup createImpl() {
    ParseSetup p = new ParseSetup();
    p._srcs = srcs;
    p._checkHeader = checkHeader;
    p._singleQuotes = singleQuotes;
    return p;
  }

  // Version&Schema-specific filling from the handler
  @Override public ParseSetupV2 fillFromImpl(ParseSetup p) {
    srcs = p._srcs;
    hexName = p._hexName;
    pType = p._pType;
    sep = p._sep;
    ncols = p._ncols;
    columnNames = p._columnNames;
    data = p._data;
    return this;
  }

  //==========================
  // Helper so ImportV1 can link to ParseSetupV2
  static public String link(String[] keys) {
    return "ParseSetup?srcs="+Arrays.toString(keys);
  }


  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("ParseSetup");
    if (null != srcs && srcs.length > 0)
      ab.href("Parse",srcs[0].toString(),water.api.ParseV2.link(srcs,hexName,pType,sep,ncols,checkHeader,singleQuotes,columnNames));
    else
      ab.href("Parse","unknown",water.api.ParseV2.link(srcs,hexName,pType,sep,ncols,checkHeader,singleQuotes,columnNames));
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

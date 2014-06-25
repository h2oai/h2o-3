package water.parser;

import java.io.File;
import java.util.Arrays;
import water.*;
import water.api.API;
import water.api.Schema;
import water.parser.ParserType;
import water.util.DocGen.HTML;

public class ParseSetupV2 extends Schema<ParseSetupHandler,ParseSetupV2> {

  // Input fields
  @API(help="Source keys",required=true)
  public Key[] srcs;

  @API(help="Check header: 0 means guess, +1 means 1st line is header not data, -1 means 1st line is data not header",direction=API.Direction.INOUT)
  public int checkHeader;

  @API(help="Single quotes",direction=API.Direction.INPUT)
  public boolean singleQuotes;

  // Output fields
  @API(help="Suggested name", direction=API.Direction.OUTPUT)
  public String hexName;

  @API(help="Parser Type")
  public ParserType pType;

  @API(help="Field separator")
  public byte sep;

  @API(help="Number of columns")
  public int ncols;

  @API(help="Column Names",direction=API.Direction.OUTPUT)
  public String[] columnNames;

  @API(help="Sample Data")
  public String[][] data;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected ParseSetupV2 fillInto( ParseSetupHandler h ) {
    h._srcs = srcs;
    h._checkHeader = checkHeader;
    h._singleQuotes = singleQuotes;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override protected ParseSetupV2 fillFrom( ParseSetupHandler h ) {
    hexName = h._hexName;
    pType = h._pType;
    sep = h._sep;
    ncols = h._ncols;
    columnNames = h._columnNames;
    data = h._data;
    return this;
  }

  //==========================
  // Helper so ImportV1 can link to ParseSetupV2
  static public String link(String[] keys) {
    return "ParseSetup?srcs="+Arrays.toString(keys);
  }


  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("ParseSetup");
    ab.href("Parse",srcs[0].toString(),water.api.ParseV2.link(srcs,hexName,pType,sep,ncols,singleQuotes,columnNames));
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

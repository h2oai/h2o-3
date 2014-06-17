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

  // Output fields
  @API(help="Result name")
  public String hexName;

  @API(help="Parser Type")
  public ParserType pType;

  @API(help="Field separator")
  public byte sep;

  @API(help="Number of columns")
  public int ncols;

  @API(help="Single quotes")
  public boolean singleQuotes;

  @API(help="Column Names")
  public String[] columnNames;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected ParseSetupV2 fillInto( ParseSetupHandler h ) {
    h._srcs = srcs;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override protected ParseSetupV2 fillFrom( ParseSetupHandler h ) {
    hexName = h._hexName;
    pType = h._pType;
    sep = h._sep;
    ncols = h._ncols;
    singleQuotes = h._singleQuotes;
    columnNames = h._columnNames;
    return this;
  }

  //==========================
  // Helper so ImportV1 can link to ParseSetupV2
  static public String link(String[] keys) {
    return "ParseSetup?srcs="+Arrays.toString(keys);
  }
}

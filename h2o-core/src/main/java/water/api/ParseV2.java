package water.api;

import java.util.Arrays;
import water.*;
import water.util.DocGen.HTML;
import water.parser.ParserType;

public class ParseV2 extends Schema<ParseHandler,ParseV2> {

  // Input fields
  @API(help="Final hex key name",required=true)
  Key hex;

  @API(help="Source keys",required=true,dependsOn={"hex"})
  Key[] srcs;

  @API(help="Parser Type",dependsOn={"srcs"})
  ParserType pType;

  @API(help="separator",dependsOn={"srcs"})
  byte sep;

  @API(help="ncols",dependsOn={"srcs"})
  int ncols;

  @API(help="single Quotes",dependsOn={"srcs"})
  boolean singleQuotes;

  @API(help="Column Names",dependsOn={"srcs"})
  String[] columnNames;

  @API(help="Delete input key after parse")
  boolean delete_on_done;

  @API(help="Block until the parse completes (as opposed to returning early and requiring polling")
  boolean blocking;

  // Output fields
  @API(help="Job Key")
  Key job;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected ParseV2 fillInto( ParseHandler h ) {
    h._hex = hex;
    h._srcs = srcs;
    h._pType = pType;
    h._sep = sep;
    h._ncols = ncols;
    h._singleQuotes = singleQuotes;
    h._columnNames = columnNames;
    h._delete_on_done = delete_on_done;
    h._blocking = blocking;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override protected ParseV2 fillFrom( ParseHandler h ) {
    job = h._job._key;
    return this;
  }

  //==========================

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Parse Started");
    String url = JobPollV2.link(job);
    return ab.href("Poll",url,url);
  }

  // Helper so ParseSetup can link to Parse
  public static String link(Key[] srcs, String hexName, ParserType pType, byte sep, int ncols, boolean singleQuotes, String[] columnNames) {
    return "Parse?srcs="+Arrays.toString(srcs)+
      "&hex="+hexName+
      "&pType="+pType+
      "&sep="+sep+
      "&ncols="+ncols+
      "&singleQuotes="+singleQuotes+
      "&columnNames="+Arrays.toString(columnNames)+
      "";
  }
}

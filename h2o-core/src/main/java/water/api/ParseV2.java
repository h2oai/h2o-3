package water.api;

import java.util.Arrays;
import water.*;
import water.api.ParseHandler.Parse;
import water.util.DocGen.HTML;
import water.parser.ParserType;

public class ParseV2 extends Schema<Parse,ParseV2> {

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

  @API(help="Check header: 0 means guess, +1 means 1st line is header not data, -1 means 1st line is data not header",dependsOn={"srcs"})
  int checkHeader;

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
  @Override public Parse createImpl() {
    Parse p = new Parse();
    p._hex = hex;
    p._srcs = srcs;
    p._pType = pType;
    p._sep = sep;
    p._ncols = ncols;
    p._checkHeader = checkHeader;
    p._singleQuotes = singleQuotes;
    p._columnNames = columnNames;
    p._delete_on_done = delete_on_done;
    p._blocking = blocking;
    return p;
  }

  // Version&Schema-specific filling from the handler
  @Override public ParseV2 fillFromImpl( Parse p ) {
    job = p._job._key;
    return this;
  }

  //==========================

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Parse Started");
    String url = JobV2.link(job);
    return ab.href("Poll",url,url);
  }

  // Helper so ParseSetup can link to Parse
  public static String link(Key[] srcs, String hexName, ParserType pType, byte sep, int ncols, int checkHeader, boolean singleQuotes, String[] columnNames) {
    return "Parse?srcs="+Arrays.toString(srcs)+
      "&hex="+hexName+
      "&pType="+pType+
      "&sep="+sep+
      "&ncols="+ncols+
      "&checkHeader="+checkHeader+
      "&singleQuotes="+singleQuotes+
      "&columnNames="+Arrays.toString(columnNames)+
      "";
  }
}

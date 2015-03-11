package water.api;

import water.api.KeyV1.FrameKeyV1;
import water.Iced;
import water.Key;
import water.api.KeyV1.VecKeyV1;
import water.fvec.Vec;
import water.parser.ParserType;
import water.util.DocGen.HTML;

import java.util.Arrays;

public class ParseV2 extends Schema<Iced, ParseV2> {
  // Input fields
  @API(help="Final hex key name",required=true)
  FrameKeyV1 destination_key;  // TODO: for now this has to be a Key, not a Frame, because it doesn't exist yet.

  @API(help="Source keys",required=true)
  FrameKeyV1[] source_keys;

  @API(help="Parser type", values = {"AUTO", "ARFF", "XLS", "XLSX", "CSV", "SVMLight"})
  ParserType parse_type;

  @API(help="Field separator")
  byte separator;

  @API(help="Single Quotes")
  boolean single_quotes;

  @API(help="Check header: 0 means guess, +1 means 1st line is header not data, -1 means 1st line is data not header")
  int check_header;

  @API(help="Number of columns")
  int number_columns;

  @API(help="Column names")
  String[] column_names;

  @API(help="Value types for columns")
  String[] column_types;

  @API(help="Domains for categorical columns")
  String[][] domains;

  @API(help="NA strings for columns")
  String[] na_strings;

  @API(help="Size of individual parse tasks", direction=API.Direction.INPUT)
  int chunk_size;

  @API(help="Delete input key after parse")
  boolean delete_on_done;

  @API(help="Block until the parse completes (as opposed to returning early and requiring polling")
  boolean blocking;

  @API(help="Remove frame after blocking parse, and return array of Vecs")
  boolean remove_frame;

  // Output fields
  @API(help="Parse job", direction=API.Direction.OUTPUT)
  JobV2 job;

  // Zero if blocking==false; row-count if blocking==true
  @API(help="Rows", direction=API.Direction.OUTPUT)
  long rows;

  // Only not-null if blocking==true and removeFrame=true
  @API(help="Vec keys", direction=API.Direction.OUTPUT)
  VecKeyV1[] vec_keys;


  //==========================

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Parse Started");
    String url = JobV2.link(job.key.key());
    return ab.href("Poll",url,url);
  }

  // Helper so ParseSetup can link to Parse
  public static String link(Key[] srcs, String hexName, ParserType pType, byte sep, int ncols, int checkHeader, boolean singleQuotes, String[] columnNames) {
    return "Parse?source_keys="+Arrays.toString(srcs)+
      "&destination_key="+hexName+
      "&parse_type="+pType+
      "&separator="+sep+
      "&number_columns="+ncols+
      "&check_header="+checkHeader+
      "&single_quotes="+singleQuotes+
      "&column_names="+Arrays.toString(columnNames)+
      "";
  }
}

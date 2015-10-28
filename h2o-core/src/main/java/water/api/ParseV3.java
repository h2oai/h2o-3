package water.api;

import water.api.KeyV3.FrameKeyV3;
import water.Iced;
import water.Key;
import water.api.KeyV3.VecKeyV3;
import water.parser.ParserType;

import java.util.Arrays;

public class ParseV3 extends RequestSchema<Iced, ParseV3> {
  // Input fields
  @API(help="Final frame name",required=true)
  FrameKeyV3 destination_frame;  // TODO: for now this has to be a Key, not a Frame, because it doesn't exist yet.

  @API(help="Source frames",required=true)
  FrameKeyV3[] source_frames;

  @API(help="Parser type", values = {"GUESS", "ARFF", "XLS", "XLSX", "CSV", "SVMLight"})
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
  String[][] na_strings;

  @API(help="Size of individual parse tasks", direction=API.Direction.INPUT)
  int chunk_size;

  @API(help="Delete input key after parse")
  boolean delete_on_done;

  @API(help="Block until the parse completes (as opposed to returning early and requiring polling")
  boolean blocking;

  // Output fields
  @API(help="Parse job", direction=API.Direction.OUTPUT)
  JobV3 job;

  // Zero if blocking==false; row-count if blocking==true
  @API(help="Rows", direction=API.Direction.OUTPUT)
  long rows;


  //==========================

  // Helper so ParseSetup can link to Parse
  public static String link(Key[] srcs, String hexName, ParserType pType, byte sep, int ncols, int checkHeader, boolean singleQuotes, String[] columnNames, String[] columnTypes, String[][] naStrings, int chunkSize) {
    return "Parse?source_keys="+Arrays.toString(srcs)+
      "&destination_key="+hexName+
      "&parse_type="+pType+
      "&separator="+sep+
      "&number_columns="+ncols+
      "&check_header="+checkHeader+
      "&single_quotes="+singleQuotes+
      "&column_names="+Arrays.toString(columnNames)+
      "&column_types="+Arrays.toString(columnTypes)+
      "&chunk_size="+chunkSize+
      "";
  }
}

package water.api;

import water.Key;
import water.api.KeyV3.FrameKeyV3;
import water.fvec.FileVec;
import water.parser.ParseSetup;
import water.parser.ParserType;
import water.util.DocGen.HTML;

import java.util.Arrays;

public class ParseSetupV3 extends RequestSchema<ParseSetup,ParseSetupV3> {

  // Input fields
  @API(help="Source frames", required=true, direction=API.Direction.INOUT)
  public FrameKeyV3[] source_frames;

  @API(help="Parser type", values = {"GUESS", "ARFF", "XLS", "XLSX", "CSV", "SVMLight"}, direction=API.Direction.INOUT)
  public ParserType parse_type = ParserType.GUESS;

  @API(help="Field separator", direction=API.Direction.INOUT)
  public byte separator = ParseSetup.GUESS_SEP;

  @API(help="Single quotes", direction=API.Direction.INOUT)
  public boolean single_quotes = false;

  @API(help="Check header: 0 means guess, +1 means 1st line is header not data, -1 means 1st line is data not header", direction=API.Direction.INOUT)
  public int check_header = ParseSetup.GUESS_HEADER;

  @API(help="Column names", direction=API.Direction.INOUT)
  public String[] column_names = null;

  @API(help="Value types for columns", direction=API.Direction.INOUT)
  public String[] column_types = null;

  @API(help="NA strings for columns", direction=API.Direction.INOUT)
  public String[][] na_strings;

  @API(help="Regex for names of columns to return", direction=API.Direction.INOUT)
  public String column_name_filter;

  @API(help="Column offset to return", direction=API.Direction.INOUT)
  public int column_offset;

  @API(help="Number of columns to return", direction=API.Direction.INOUT)
  public int column_count;

  // Output fields
  @API(help="Suggested name", direction=API.Direction.OUTPUT)
  public String destination_frame;

  @API(help="Number of header lines found", direction=API.Direction.OUTPUT)
  long header_lines;

  @API(help="Number of columns", direction=API.Direction.OUTPUT)
  public int number_columns = ParseSetup.GUESS_COL_CNT;

  @API(help="Sample data", direction=API.Direction.OUTPUT)
  public String[][] data;

  @API(help="Size of individual parse tasks", direction=API.Direction.OUTPUT)
  public int chunk_size = FileVec.DFLT_CHUNK_SIZE;

  @API(help="Total number of columns we would return with no column pagination", direction=API.Direction.INOUT)
  public int total_filtered_column_count;

  //==========================
  // Helper so ImportV1 can link to ParseSetupV2
  static public String link(String[] keys) {
    return "ParseSetup?source_keys="+Arrays.toString(keys);
  }


  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("ParseSetup");
    if (null != source_frames && source_frames.length > 0) {
      Key[] srcs_key = new Key[source_frames.length];
      for (int i = 0; i < source_frames.length; i++)
        srcs_key[i] = source_frames[i].key();
      ab.href("Parse", source_frames[0].toString(), ParseV3.link(srcs_key, destination_frame, parse_type, separator, number_columns, check_header, single_quotes, column_names, column_types, na_strings, chunk_size));
    } else {
      Key[] srcs_key = new Key[source_frames.length];
      for (int i = 0; i < source_frames.length; i++)
        srcs_key[i] = source_frames[i].key();
      ab.href("Parse", "unknown", ParseV3.link(srcs_key, destination_frame, parse_type, separator, number_columns, check_header, single_quotes, column_names, column_types, na_strings, chunk_size));
    }
    ab.putA( "source_frames", source_frames);
    ab.putStr("destination_frame", destination_frame);
    ab.putEnum("parse_type", parse_type);
    ab.put1("separator", separator);
    ab.put4("number_columns",number_columns);
    ab.putZ("single_quotes",single_quotes);
    ab.putAStr("column_names",column_names);
    ab.putAStr("column_types",column_types);
    ab.putAAStr("na_strings",na_strings);
    ab.putAAStr("data",data);
    ab.put4("chunk_size", chunk_size);
    return ab;
  }
}

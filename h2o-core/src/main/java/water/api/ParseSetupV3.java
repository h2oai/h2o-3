package water.api;

import water.Key;
import water.api.KeyV3.FrameKeyV3;
import water.fvec.FileVec;
import water.parser.ParseSetup;
import water.parser.ParserType;

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

}

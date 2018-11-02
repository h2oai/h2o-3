package water.api.schemas3;

import water.api.API;
import water.api.ParseTypeValuesProvider;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.exceptions.H2OIllegalValueException;
import water.fvec.FileVec;
import water.parser.ParseSetup;
import water.parser.ParserInfo;
import water.parser.ParserProvider;
import water.parser.ParserService;

import static water.parser.DefaultParserProviders.GUESS_INFO;

public class ParseSetupV3 extends RequestSchemaV3<ParseSetup, ParseSetupV3> {

  // Input fields
  @API(help="Source frames", required=true, direction=API.Direction.INOUT)
  public FrameKeyV3[] source_frames;

  @API(help="Parser type", valuesProvider = ParseTypeValuesProvider.class, direction=API.Direction.INOUT)
  public String parse_type = GUESS_INFO.name();

  @API(help="Field separator", direction=API.Direction.INOUT)
  public byte separator = ParseSetup.GUESS_SEP;

  @API(help="Single quotes", direction=API.Direction.INOUT)
  public boolean single_quotes = false;

  @API(help="Check header: 0 means guess, +1 means 1st line is header not data, -1 means 1st line is data not header", direction=API.Direction.INOUT)
  public int check_header = ParseSetup.GUESS_HEADER;

  @API(help="Column names", direction=API.Direction.INOUT)
  public String[] column_names = null;

  @API(help="Skipped columns indices", direction=API.Direction.INOUT)
  public int[] skipped_columns = null;

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

  @API(help="Warnings", direction=API.Direction.OUTPUT)
  public String[] warnings;

  @API(help="Size of individual parse tasks", direction=API.Direction.OUTPUT)
  public int chunk_size = FileVec.DFLT_CHUNK_SIZE;

  @API(help="Total number of columns we would return with no column pagination", direction=API.Direction.INOUT)
  public int total_filtered_column_count;

  @API(help="Key-reference to an initialized instance of a Decryption Tool")
  public KeyV3.DecryptionToolKeyV3 decrypt_tool;

  @Override
  public ParseSetup fillImpl(ParseSetup impl) {
    ParseSetup parseSetup = fillImpl(impl, new String[] {"parse_type"});
    // Transform the field parse_type
    ParserInfo pi = GUESS_INFO;
    if (this.parse_type != null) {
      ParserProvider pp = ParserService.INSTANCE.getByName(this.parse_type);
      if (pp != null) {
        pi = pp.info();
      } else throw new H2OIllegalValueException("Cannot find right parser for specified parser type!", this.parse_type);
    }
    parseSetup.setParseType(pi);

    return parseSetup;
  }

  @Override
  public ParseSetupV3 fillFromImpl(ParseSetup impl) {
    ParseSetupV3 parseSetupV3 = fillFromImpl(impl, new String[] {"parse_type"});
    parseSetupV3.parse_type = impl.getParseType() != null ? impl.getParseType().name() : GUESS_INFO.name();
    return parseSetupV3;
  }
}


package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.api.ParseTypeValuesProvider;
import water.parser.ParseSetup;

public class ParseV3 extends RequestSchemaV3<Iced, ParseV3> {
  // Input fields
  @API(help="Final frame name",required=true)
  public FrameKeyV3 destination_frame;  // TODO: for now this has to be a Key, not a Frame, because it doesn't exist
  // yet.

  @API(help="Source frames",required=true)
  public FrameKeyV3[] source_frames;

  @API(help="Parser type", valuesProvider = ParseTypeValuesProvider.class)
  public String parse_type;

  @API(help="Field separator")
  public byte separator;

  @API(help="Single Quotes")
  public boolean single_quotes;

  @API(help="Check header: 0 means guess, +1 means 1st line is header not data, -1 means 1st line is data not header")
  public int check_header;

  @API(help="Number of columns")
  public int number_columns;

  @API(help="Column names")
  public String[] column_names;

  @API(help="Value types for columns")
  public String[] column_types;

  @API(help="Skipped columns indices", direction=API.Direction.INOUT)
  public int[] skipped_columns;

  @API(help="If true, will force the column types to be either the ones in Parquet schema for Parquet files or the " +
          "ones specified in column_types.  This parameter is used for numerical columns only.  Other column" +
          "settings will happen without setting this parameter.  Defaults to false.", 
          direction=API.Direction.INPUT)
  public boolean force_col_types;

  @API(help="Domains for categorical columns")
  public String[][] domains;

  @API(help="NA strings for columns")
  public String[][] na_strings;

  @API(help="Size of individual parse tasks", direction=API.Direction.INPUT)
  public int chunk_size;

  @API(help="Delete input key after parse")
  public boolean delete_on_done;

  @API(help="Block until the parse completes (as opposed to returning early and requiring polling")
  public boolean blocking;

  @API(help="Key-reference to an initialized instance of a Decryption Tool")
  public KeyV3.DecryptionToolKeyV3 decrypt_tool;

  @API(help="Custom characters to be treated as non-data line markers", direction=API.Direction.INPUT)
  public String custom_non_data_line_markers;

  @API(help = "Name of the column the persisted dataset has been partitioned by.")
  public String[] partition_by;

  // Output fields
  @API(help="Parse job", direction=API.Direction.OUTPUT)
  public JobV3 job;

  // Zero if blocking==false; row-count if blocking==true
  @API(help="Rows", direction=API.Direction.OUTPUT)
  public long rows;

  @API(help="One ASCII character used to escape other characters.", direction=API.Direction.INOUT)
  public byte escapechar = ParseSetup.DEFAULT_ESCAPE_CHAR;

  @API(help="Adjust the imported time from GMT timezone to cluster timezone.", direction=API.Direction.INPUT)
  public boolean tz_adjust_to_local;
}

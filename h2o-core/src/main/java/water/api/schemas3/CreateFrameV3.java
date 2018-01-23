package water.api.schemas3;

import hex.CreateFrame;
import water.Key;
import water.api.API;
import water.fvec.Frame;

public class CreateFrameV3 extends RequestSchemaV3<CreateFrame, CreateFrameV3> {
  @API(help="destination key", direction=API.Direction.INOUT)
  public KeyV3.FrameKeyV3 dest;

  @API(help = "Number of rows", direction=API.Direction.INOUT)
  public long rows;

  @API(help = "Number of data columns (in addition to the first response column)", direction=API.Direction.INOUT)
  public int cols;

  @API(help = "Random number seed that determines the random values", direction=API.Direction.INOUT)
  public long seed;

  @API(help = "Random number seed for setting the column types", direction=API.Direction.INOUT)
  public long seed_for_column_types;

  @API(help = "Whether frame should be randomized", direction=API.Direction.INOUT)
  public boolean randomize;

  @API(help = "Constant value (for randomize=false)", direction=API.Direction.INOUT)
  public long value;

  @API(help = "Range for real variables (-range ... range)", direction=API.Direction.INOUT)
  public long real_range;

  @API(help = "Fraction of categorical columns (for randomize=true)", direction=API.Direction.INOUT)
  public double categorical_fraction;

  @API(help = "Factor levels for categorical variables", direction=API.Direction.INOUT)
  public int factors;

  @API(help = "Fraction of integer columns (for randomize=true)", direction=API.Direction.INOUT)
  public double integer_fraction;

  @API(help = "Range for integer variables (-range ... range)", direction=API.Direction.INOUT)
  public long integer_range;

  @API(help = "Fraction of binary columns (for randomize=true)", direction=API.Direction.INOUT)
  public double binary_fraction;

  @API(help = "Fraction of 1's in binary columns", direction=API.Direction.INOUT)
  public double binary_ones_fraction;

  @API(help = "Fraction of date/time columns (for randomize=true)", direction=API.Direction.INOUT)
  public double time_fraction;

  @API(help = "Fraction of string columns (for randomize=true)", direction=API.Direction.INOUT)
  public double string_fraction;

  @API(help = "Fraction of missing values", direction=API.Direction.INOUT)
  public double missing_fraction;

  @API(help = "Whether an additional response column should be generated", direction=API.Direction.INOUT)
  public boolean has_response;

  @API(help = "Number of factor levels of the first column (1=real, 2=binomial, N=multinomial or ordinal)", direction=API.Direction.INOUT)
  public int response_factors;

  @API(help = "For real-valued response variable: Whether the response should be positive only.", direction=API.Direction.INOUT)
  public boolean positive_response;

  // Output only:
  @API(help="Job Key", direction=API.Direction.OUTPUT)
  public KeyV3.JobKeyV3 key;

  @Override public CreateFrame createImpl( ) { return new CreateFrame(Key.<Frame>make()); }
}


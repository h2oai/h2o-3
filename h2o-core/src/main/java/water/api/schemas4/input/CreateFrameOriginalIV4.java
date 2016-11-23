package water.api.schemas4.input;

import hex.createframe.recipes.OriginalCreateFrameRecipe;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas4.InputSchemaV4;


/**
 * Input schema for `POST /4/frames/$original` endpoint.
 */
public class CreateFrameOriginalIV4 extends InputSchemaV4<OriginalCreateFrameRecipe, CreateFrameOriginalIV4> {

  @API(help="destination key")
  public KeyV3.FrameKeyV3 dest;

  @API(help = "Number of rows")
  public int rows;

  @API(help = "Number of data columns (in addition to the first response column)")
  public int cols;

  @API(help = "Random number seed that determines the random values")
  public long seed;

  @API(help = "Whether frame should be randomized")
  public boolean randomize;

  @API(help = "Constant value (for randomize=false)")
  public long value;

  @API(help = "Range for real variables (-range ... range)")
  public double real_range;

  @API(help = "Fraction of categorical columns (for randomize=true)")
  public double categorical_fraction;

  @API(help = "Factor levels for categorical variables")
  public int factors;

  @API(help = "Fraction of integer columns (for randomize=true)")
  public double integer_fraction;

  @API(help = "Range for integer variables (-range ... range)")
  public int integer_range;

  @API(help = "Fraction of binary columns (for randomize=true)")
  public double binary_fraction;

  @API(help = "Fraction of 1's in binary columns")
  public double binary_ones_fraction;

  @API(help = "Fraction of date/time columns (for randomize=true)")
  public double time_fraction;

  @API(help = "Fraction of string columns (for randomize=true)")
  public double string_fraction;

  @API(help = "Fraction of missing values")
  public double missing_fraction;

  @API(help = "Whether an additional response column should be generated")
  public boolean has_response;

  @API(help = "Number of factor levels of the first column (1=real, 2=binomial, N=multinomial)")
  public int response_factors;

  @API(help = "For real-valued response variable: Whether the response should be positive only.")
  public boolean positive_response;

}

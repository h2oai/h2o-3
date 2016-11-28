package water.api.schemas4.input;

import hex.createframe.recipes.SimpleCreateFrameRecipe;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas4.InputSchemaV4;


/**
 * Input schema for `POST /4/frames/$simple` endpoint.
 */
public class CreateFrameSimpleIV4 extends InputSchemaV4<SimpleCreateFrameRecipe, CreateFrameSimpleIV4> {

  @API(help = "Id for the frame to be created.")
  public KeyV3.FrameKeyV3 dest;

  @API(help = "Random number seed that determines the random values.")
  public long seed;

  @API(help = "Number of rows.")
  public int nrows;

  @API(help = "Number of real-valued columns. Values in these columns will be uniformly distributed between " +
              "real_lb and real_ub.")
  public int ncols_real;

  @API(help = "Number of integer columns.")
  public int ncols_int;

  @API(help = "Number of enum (categorical) columns.")
  public int ncols_enum;

  @API(help = "Number of boolean (binary) columns.")
  public int ncols_bool;

  @API(help = "Number of string columns.")
  public int ncols_str;

  @API(help = "Number of time columns.")
  public int ncols_time;

  @API(help = "Lower bound for the range of the real-valued columns.")
  public double real_lb;

  @API(help = "Upper bound for the range of the real-valued columns.")
  public double real_ub;

  @API(help = "Lower bound for the range of integer columns.")
  public int int_lb;

  @API(help = "Upper bound for the range of integer columns.")
  public int int_ub;

  @API(help = "Number of levels (categories) for the enum columns.")
  public int enum_nlevels;

  @API(help = "Fraction of ones in each boolean (binary) column.")
  public double bool_p;

  @API(help = "Lower bound for the range of time columns (in ms since the epoch).")
  public long time_lb;

  @API(help = "Upper bound for the range of time columns (in ms since the epoch).")
  public long time_ub;

  @API(help = "Length of generated strings in string columns.")
  public int str_length;

  @API(help = "Fraction of missing values.")
  public double missing_fraction;

  @API(help = "Type of the response column to add.", values = {"none", "real", "int", "bool", "enum", "time"})
  public SimpleCreateFrameRecipe.ResponseType response_type;

  @API(help = "Lower bound for the response variable (real/int/time types).")
  public double response_lb;

  @API(help = "Upper bound for the response variable (real/int/time types).")
  public double response_ub;

  @API(help = "Frequency of 1s for the bool (binary) response column.")
  public double response_p;

  @API(help = "Number of categorical levels for the enum response column.")
  public int response_nlevels;
}

package hex.createframe.recipes;

import hex.createframe.CreateFrameExecutor;
import hex.createframe.CreateFrameRecipe;
import hex.createframe.columns.*;
import hex.createframe.postprocess.MissingInserterCfps;
import hex.createframe.postprocess.ShuffleColumnsCfps;


/**
 * Similar to {@link OriginalCreateFrameRecipe}, except that this recipe
 * requires to specify the number of columns of each type explicitly (not
 * as fractions). It also uses different naming scheme, so that columns of
 * different types have names according to that type: integer columns are
 * {@code I1, I2, ...}, binary are {@code B1, ...}, and so on.
 */
public class SimpleCreateFrameRecipe extends CreateFrameRecipe<SimpleCreateFrameRecipe> {
  public int nrows = 100;
  public int ncols_real = 0;
  public int ncols_int = 0;
  public int ncols_enum = 0;
  public int ncols_bool = 0;
  public int ncols_str = 0;
  public int ncols_time = 0;
  public double real_lb = -100;
  public double real_ub = 100;
  public int int_lb = -100;
  public int int_ub = 100;
  public int enum_nlevels = 10;
  public double bool_p = 0.3;
  public long time_lb = 30L * 365 * 24 * 3600 * 1000;  // ~ 2000-01-01
  public long time_ub = 50L * 365 * 24 * 3600 * 1000;  // ~ 2020-01-01
  public int str_length = 8;
  public double missing_fraction = 0;
  public ResponseType responseType = ResponseType.NONE;

  public enum ResponseType {
    NONE, REAL, INT, ENUM, BOOL, TIME
  }


  protected void checkParametersValidity() {
    check(nrows > 0, "Number of rows must be greater than 0");
    check(ncols_real >= 0, "Number of real columns cannot be negative");
    check(ncols_int >= 0, "Number of integer columns cannot be negative");
    check(ncols_bool >= 0, "Number of bool (binary) columns cannot be negative");
    check(ncols_enum >= 0, "Number of enum (categorical) columns cannot be negative");
    check(ncols_str >= 0, "Number of string columns cannot be negative");
    check(ncols_time >= 0, "Number of time columns cannot be negative");
    check(!Double.isNaN(real_lb), "Real range's lower bound cannot be NaN");
    check(!Double.isNaN(real_ub), "Real range's upper bound cannot be NaN");
    check(!Double.isInfinite(real_lb), "Real range's lower bound cannot be infinite");
    check(!Double.isInfinite(real_ub), "Real range's upper bound cannot be infinite");
    check(real_lb <= real_ub, "Invalid real range interval: lower bound exceeds the upper bound");
    check(int_lb <= int_ub, "Invalid integer range interval: lower bound exceeds the upper bound");
    check(bool_p >= 0 && bool_p <= 1, "Boolean frequency parameter must be in the range 0..1");
    check(time_lb <= time_ub, "Invalid time range interval: lower bound exceeds the upper bound");
    check(enum_nlevels > 0, "Number of levels for enum (categorical) columns must be positive");
    check(str_length > 0, "Length of string values should be positive");
  }


  protected void buildRecipe(CreateFrameExecutor cfe) {
    cfe.setSeed(seed);
    cfe.setNumRows(nrows);

    switch (responseType) {
      case REAL:
        cfe.addColumnMaker(new RealColumnCfcm("response", real_lb, real_ub));
        break;
      case INT:
        cfe.addColumnMaker(new IntegerColumnCfcm("response", int_lb, int_ub));
        break;
      case ENUM:
        cfe.addColumnMaker(new CategoricalColumnCfcm("response", enum_nlevels));
        break;
      case BOOL:
        cfe.addColumnMaker(new BinaryColumnCfcm("response", bool_p));
        break;
      case TIME:
        cfe.addColumnMaker(new TimeColumnCfcm("response", time_lb, time_ub));
        break;
    }

    for (int i = 1; i <= ncols_real; i++)
      cfe.addColumnMaker(new RealColumnCfcm("R" + i, real_lb, real_ub));
    for (int i = 1; i <= ncols_int; i++)
      cfe.addColumnMaker(new IntegerColumnCfcm("I" + i, int_lb, int_ub));
    for (int i = 0; i < ncols_enum; i++)
      cfe.addColumnMaker(new CategoricalColumnCfcm("E" + i, enum_nlevels));
    for (int i = 1; i <= ncols_bool; i++)
      cfe.addColumnMaker(new BinaryColumnCfcm("B" + i, bool_p));
    for (int i = 0; i < ncols_time; i++)
      cfe.addColumnMaker(new TimeColumnCfcm("T" + i, time_lb, time_ub));
    for (int i = 0; i < ncols_str; i++)
      cfe.addColumnMaker(new StringColumnCfcm("S" + i, str_length));

    cfe.addPostprocessStep(new MissingInserterCfps(missing_fraction));
    cfe.addPostprocessStep(new ShuffleColumnsCfps(true, true));
  }

}

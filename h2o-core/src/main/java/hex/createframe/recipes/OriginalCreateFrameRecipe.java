package hex.createframe.recipes;

import hex.createframe.CreateFrameExecutor;
import hex.createframe.CreateFrameRecipe;
import hex.createframe.columns.*;
import hex.createframe.postprocess.MissingInserterCfps;
import hex.createframe.postprocess.ShuffleColumnsCfps;

/**
 * This recipe tries to match the behavior of the original hex.CreateFrame class.
 */
public class OriginalCreateFrameRecipe extends CreateFrameRecipe<OriginalCreateFrameRecipe> {

  private int rows = 10000;
  private int cols = 10;
  private double real_range = 100;
  private double categorical_fraction = 0.2;
  private int factors = 100;
  private boolean randomize = true;
  private long value = 0;
  private double integer_fraction = 0.2;
  private double time_fraction = 0.0;
  private double string_fraction = 0.0;
  private int integer_range = 100;
  private double binary_fraction = 0.1;
  private double binary_ones_fraction = 0.02;
  private double missing_fraction = 0.01;
  private int response_factors = 2;
  private boolean positive_response = false;  // only for response_factors == 1
  private boolean has_response = false;


  @Override
  protected void checkParametersValidity() {
    double total_fraction = integer_fraction + binary_fraction + categorical_fraction + time_fraction + string_fraction;
    check(total_fraction < 1.00000001, "Integer, binary, categorical, time and string fractions must add up to <= 1");
    check(missing_fraction >= 0 && missing_fraction < 1, "Missing fraction must be between 0 and 1");
    check(integer_fraction >= 0 && integer_fraction <= 1, "Integer fraction must be between 0 and 1");
    check(binary_fraction >= 0 && binary_fraction <= 1, "Binary fraction must be between 0 and 1");
    check(time_fraction >= 0 && time_fraction <= 1, "Time fraction must be between 0 and 1");
    check(string_fraction >= 0 && string_fraction <= 1, "String fraction must be between 0 and 1");
    check(binary_ones_fraction >= 0 && binary_ones_fraction <= 1, "Binary ones fraction must be between 0 and 1");
    check(categorical_fraction >= 0 && categorical_fraction <= 1, "Categorical fraction must be between 0 and 1");
    check(categorical_fraction == 0 || factors >= 2, "Factors must be larger than 2 for categorical data");
    check(response_factors >= 1, "Response factors must be either 1 (real-valued response), or >=2 (factor levels)");
    check(response_factors <= 1024, "Response factors must be <= 1024");
    check(factors <= 1000000, "Number of factors must be <= 1,000,000");
    check(cols > 0 && rows > 0, "Must have number of rows and columns > 0");
    check(real_range >= 0, "Real range must be a nonnegative number");
    check(integer_range >= 0, "Integer range must be a nonnegative number");
    check(dest != null, "Destination frame must have a key");
    if (positive_response)
      check(response_factors == 1, "positive_response can only be requested for real-valued response column");
    if (randomize)
      check(value == 0, "Cannot set data to a constant value if randomize is true");
    else {
      check(!has_response, "Cannot have response column if randomize is false");
      check(total_fraction == 0,
            "Cannot have integer, categorical, string, binary or time columns if randomize is false");
    }
  }


  @Override
  protected void buildRecipe(CreateFrameExecutor cfe) {
    cfe.setSeed(seed);
    cfe.setNumRows(rows);

    // Sometimes the client requests, say, 0.3 categorical columns. By the time this number arrives here, it becomes
    // something like 0.299999999997. If we just multiply by the number of columns (say 10000) and take integer part,
    // we'd have 2999 columns only -- not what the client expects. This is why we add 0.1 to each count before taking
    // the floor part.
    int catcols = (int)(categorical_fraction * cols + 0.1);
    int intcols = (int)(integer_fraction * cols + 0.1);
    int bincols = (int)(binary_fraction * cols + 0.1);
    int timecols = (int)(time_fraction * cols + 0.1);
    int stringcols = (int)(string_fraction * cols + 0.1);
    int realcols = cols - catcols - intcols - bincols - timecols - stringcols;

    // At this point we might have accidentally allocated too many columns -- in such case adjust their counts.
    if (realcols < 0 && catcols > 0) { catcols--; realcols++; }
    if (realcols < 0 && intcols > 0) { intcols--; realcols++; }
    if (realcols < 0 && bincols > 0) { bincols--; realcols++; }
    if (realcols < 0 && timecols > 0) { timecols--; realcols++; }
    if (realcols < 0 && stringcols > 0) { stringcols--; realcols++; }
    assert catcols >= 0 && intcols >= 0 && bincols >= 0 && realcols >= 0 && timecols >= 0 && stringcols >= 0;

    // Create response column
    if (has_response) {
      if (response_factors == 1)
        cfe.addColumnMaker(new RealColumnCfcm("response", positive_response? 0 : -real_range, real_range));
      else
        cfe.addColumnMaker(new CategoricalColumnCfcm("response", response_factors));
    }

    // Create "feature" columns
    if (randomize) {
      int j = 0;
      for (int i = 0; i < intcols; i++)
        cfe.addColumnMaker(new IntegerColumnCfcm("C" + (++j), -integer_range, integer_range));
      for (int i = 0; i < realcols; i++)
        cfe.addColumnMaker(new RealColumnCfcm("C" + (++j), -real_range, real_range));
      for (int i = 0; i < catcols; i++)
        cfe.addColumnMaker(new CategoricalColumnCfcm("C" + (++j), factors));
      for (int i = 0; i < bincols; i++)
        cfe.addColumnMaker(new BinaryColumnCfcm("C" + (++j), binary_ones_fraction));
      for (int i = 0; i < timecols; i++)
        cfe.addColumnMaker(new TimeColumnCfcm("C" + (++j), 0, 50L * 365 * 24 * 3600 * 1000));  // 1970...2020
      for (int i = 0; i < stringcols; i++)
        cfe.addColumnMaker(new StringColumnCfcm("C" + (++j), 8));
    } else {
      assert catcols + intcols + bincols + timecols + stringcols == 0;
      for (int i = 0; i < realcols; i++)
        cfe.addColumnMaker(new RealColumnCfcm("C" + (i+1), value, value));
    }

    // Add post-processing steps
    cfe.addPostprocessStep(new MissingInserterCfps(missing_fraction));
    cfe.addPostprocessStep(new ShuffleColumnsCfps(true, true));
  }
}

package hex;

import water.H2O;
import water.Iced;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.fvec.FrameCreator;
import water.util.Log;
import water.util.PrettyPrint;

import java.util.Random;

/**
 * Create a Frame from scratch
 * If randomize = true, then the frame is filled with Random values.
 */
public class CreateFrame extends Iced {
  public final Job<Frame> _job;
  public long rows = 10000;
  public int cols = 10;
  public long seed = new Random().nextLong();
  public long seed_for_column_types = -1;
  public boolean randomize = true;
  public long value = 0;
  public long real_range = 100;
  public double categorical_fraction = 0.2;
  public int factors = 100;
  public double integer_fraction = 0.2;
  public double time_fraction = 0.0;
  public double string_fraction = 0.0;
  public long integer_range = 100;
  public double binary_fraction = 0.1;
  public double binary_ones_fraction = 0.02;
  public double missing_fraction = 0.01;
  public int response_factors = 2;
  public boolean positive_response; // only for response_factors=1
  public boolean has_response = false;

  public CreateFrame(Key<Frame> key) { _job = new Job<>(key,Frame.class.getName(),"CreateFrame"); }
  public CreateFrame() { this(Key.<Frame>make()); }

  public Job<Frame> execImpl() {
    if (seed_for_column_types==-1) seed_for_column_types = seed;
    if (integer_fraction + binary_fraction + categorical_fraction + time_fraction + string_fraction > 1) throw new IllegalArgumentException("Integer, binary, categorical, time and string fractions must add up to <= 1.");
    if (missing_fraction < 0 || missing_fraction > 1) throw new IllegalArgumentException("Missing fraction must be between 0 and 1.");
    if (integer_fraction < 0 || integer_fraction > 1) throw new IllegalArgumentException("Integer fraction must be between 0 and 1.");
    if (binary_fraction < 0 || binary_fraction > 1) throw new IllegalArgumentException("Binary fraction must be between 0 and 1.");
    if (time_fraction <0 || time_fraction > 1) throw new IllegalArgumentException("Time fraction must be between 0 and 1.");
    if (string_fraction <0 || string_fraction > 1) throw new IllegalArgumentException("String fraction must be between 0 and 1.");
    if (binary_ones_fraction < 0 || binary_ones_fraction > 1) throw new IllegalArgumentException("Binary ones fraction must be between 0 and 1.");
    if (categorical_fraction < 0 || categorical_fraction > 1) throw new IllegalArgumentException("Categorical fraction must be between 0 and 1.");
    if (categorical_fraction > 0 && factors <= 1) throw new IllegalArgumentException("Factors must be larger than 2 for categorical data.");
    if (response_factors < 1) throw new IllegalArgumentException("Response factors must be either 1 (real-valued response), or >=2 (factor levels).");
    if (response_factors > 1024) throw new IllegalArgumentException("Response factors must be <= 1024.");
    if (factors > 1000000) throw new IllegalArgumentException("Number of factors must be <= 1,000,000).");
    if (cols <= 0 || rows <= 0) throw new IllegalArgumentException("Must have number of rows > 0 and columns > 1.");

    // estimate byte size of the frame
    double byte_estimate = randomize ? rows * cols * (
            binary_fraction * 1./8 //bits
                    + categorical_fraction * (factors < 128 ? 1 : factors < 32768 ? 2 : 4)
                    + integer_fraction * (integer_range < 128 ? 1 : integer_range < 32768 ? 2 : integer_range < (1<<31) ? 4 : 8)
                    + time_fraction * 8
                    + (1-integer_fraction - binary_fraction - categorical_fraction - time_fraction - string_fraction) * 8 ) //reals
            + rows //response is
            : 0; // all constants - should be small

    long cluster_free_mem = H2O.CLOUD.free_mem();
    if (byte_estimate > cluster_free_mem)
      throw new IllegalArgumentException("Frame is expected to require " + PrettyPrint.bytes((long) byte_estimate) + ", won't fit into H2O's free memory of "+ cluster_free_mem);

    if (!randomize) {
      if (integer_fraction != 0 || categorical_fraction != 0 || time_fraction != 0 || string_fraction != 0)
        throw new IllegalArgumentException("Cannot have integer, categorical or time fractions > 0 unless randomize=true.");
    } else {
      if (value != 0)
        throw new IllegalArgumentException("Cannot set data to a constant value if randomize=true.");
    }
    Log.info("Generated seed: " + seed);

    FrameCreator fc = new FrameCreator(this);
    return _job.start(fc,fc.nChunks()*7);      // And start FrameCreator
  }
}

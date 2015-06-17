package hex;

import water.H2O;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.fvec.FrameCreator;
import water.util.PrettyPrint;

import java.util.Random;

/**
 * Create a Frame from scratch
 * If randomize = true, then the frame is filled with Random values.
 */
public class CreateFrame extends Job<Frame> {
  public long rows = 10000;
  public int cols = 10;
  public long seed = new Random().nextLong();
  public boolean randomize = true;
  public long value = 0;
  public long real_range = 100;
  public double categorical_fraction = 0.2;
  public int factors = 100;
  public double integer_fraction = 0.2;
  public long integer_range = 100;
  public double binary_fraction = 0.1;
  public double binary_ones_fraction = 0.02;
  public double missing_fraction = 0.01;
  public int response_factors = 2;
  public boolean positive_response; // only for response_factors=1
  public boolean has_response = false;

  public CreateFrame(Key<Frame> dest, String desc) { super(dest, (desc == null ? "CreateFrame" : desc)); }
  public CreateFrame() { super(Key.make(), "CreateFrame"); }

  public Job<Frame> execImpl() {
    if (integer_fraction + binary_fraction + categorical_fraction > 1) throw new IllegalArgumentException("Integer, binary and categorical fractions must add up to <= 1.");
    if (Math.abs(missing_fraction) > 1) throw new IllegalArgumentException("Missing fraction must be between 0 and 1.");
    if (Math.abs(integer_fraction) > 1) throw new IllegalArgumentException("Integer fraction must be between 0 and 1.");
    if (Math.abs(binary_fraction) > 1) throw new IllegalArgumentException("Binary fraction must be between 0 and 1.");
    if (Math.abs(binary_ones_fraction) > 1) throw new IllegalArgumentException("Binary ones fraction must be between 0 and 1.");
    if (Math.abs(categorical_fraction) > 1) throw new IllegalArgumentException("Categorical fraction must be between 0 and 1.");
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
                    + (1-integer_fraction - binary_fraction - categorical_fraction) * 8 ) //reals
            + rows * 1 //response is
            : 0; // all constants - should be small

    if (byte_estimate > H2O.CLOUD._memary[0].get_max_mem() * H2O.CLOUD.size())
      throw new IllegalArgumentException("Frame is expected to require " + PrettyPrint.bytes((long) byte_estimate) + ", won't fit into H2O's memory.");

    if (!randomize) {
      if (integer_fraction != 0 || categorical_fraction != 0)
        throw new IllegalArgumentException("Cannot have integer or categorical fractions > 0 unless randomize=true.");
    } else {
      if (value != 0)
        throw new IllegalArgumentException("Cannot set data to a constant value if randomize=true.");
    }
    if (_dest == null) throw new IllegalArgumentException("Destination key cannot be null.");

    FrameCreator fc = new FrameCreator(this, this._key);
    start(fc, fc.nChunks()*5);
    return this;
  }
}

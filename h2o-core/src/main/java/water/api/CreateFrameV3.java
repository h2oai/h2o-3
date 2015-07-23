package water.api;

import hex.CreateFrame;
import water.Key;
import water.fvec.Frame;

class CreateFrameV3 extends JobV3<CreateFrame, CreateFrameV3> {
  @API(help = "Number of rows", json=true)
  public long rows;

  @API(help = "Number of data columns (in addition to the first response column)", json=true)
  public int cols;

  @API(help = "Random number seed", json=true)
  public long seed;

  @API(help = "Whether frame should be randomized", json=true)
  public boolean randomize;

  @API(help = "Constant value (for randomize=false)", json=true)
  public long value;

  @API(help = "Range for real variables (-range ... range)", json=true)
  public long real_range;

  @API(help = "Fraction of categorical columns (for randomize=true)", json=true)
  public double categorical_fraction;

  @API(help = "Factor levels for categorical variables", json=true)
  public int factors;

  @API(help = "Fraction of integer columns (for randomize=true)", json=true)
  public double integer_fraction;

  @API(help = "Range for integer variables (-range ... range)", json=true)
  public long integer_range;

  @API(help = "Fraction of binary columns (for randomize=true)", json=true)
  public double binary_fraction;

  @API(help = "Fraction of 1's in binary columns", json=true)
  public double binary_ones_fraction;

  @API(help = "Fraction of missing values", json=true)
  public double missing_fraction;

  @API(help = "Number of factor levels of the first column (1=real, 2=binomial, N=multinomial)", json=true)
  public int response_factors;

  @API(help = "Whether an additional response column should be generated", json=true)
  public boolean has_response;

  @Override public CreateFrame createImpl( ) { return new CreateFrame(Key.<Frame>make(), null); }
}


package water.api;

import hex.CreateFrame;
import water.Job;
import water.Key;

class CreateFrameV2 extends JobV2<CreateFrameV2> {
  @API(help = "Key")
  Key key;

  @API(help = "Number of rows", required = true, json=true)
  public long rows;

  @API(help = "Number of data columns (in addition to the first response column)", required = true,json=true)
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
  public int factors = 100;

  @API(help = "Fraction of integer columns (for randomize=true)", json=true)
  public double integer_fraction = 0.2;

  @API(help = "Range for integer variables (-range ... range)", json=true)
  public long integer_range = 100;

  @API(help = "Fraction of binary columns (for randomize=true)", json=true)
  public double binary_fraction = 0.1;

  @API(help = "Fraction of 1's in binary columns", json=true)
  public double binary_ones_fraction = 0.02;

  @API(help = "Fraction of missing values", json=true)
  public double missing_fraction = 0.01;

  @API(help = "Number of factor levels of the first column (1=real, 2=binomial, N=multinomial)", json=true)
  public int response_factors = 2;

  @Override public Job createImpl( ) { return new CreateFrame(Key.make(), null); }
}


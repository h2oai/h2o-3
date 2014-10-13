package hex.gbm;

import water.*;
import water.H2O.H2OCountedCompleter;
import hex.*;
import hex.schemas.*;

public class GBM extends ModelBuilder<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {
  /** Distribution functions */
  // Note: AUTO will select gaussian for continuous, and multinomial for categorical response
  // TODO: Replace with drop-down that displays different distributions depending on cont/cat response
  public enum Family {
    AUTO, bernoulli
  }

  // Called from an http request
  public GBM( GBMModel.GBMParameters parms) {
    super("GBM",parms);
  }

  public ModelBuilderSchema schema() { return new GBMV2(); }


  /** Start the GBM training Job on an F/J thread. */
  @Override public Job<GBMModel> train() {
    return start(new GBMDriver(), _parms.ntrees);
  }

  // ----------------------
  private class GBMDriver extends H2OCountedCompleter<GBMDriver> {

    @Override protected void compute2() {
      throw H2O.unimpl();
    }
  }
}

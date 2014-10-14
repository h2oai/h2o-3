package hex.gbm;

import water.*;
import water.H2O.H2OCountedCompleter;
import hex.*;
import hex.schemas.*;

public class GBM extends SharedTree<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {
  // Called from an http request
  public GBM( GBMModel.GBMParameters parms) {
    super("GBM",parms);
  }

  public ModelBuilderSchema schema() { return new GBMV2(); }


  /** Start the GBM training Job on an F/J thread. */
  @Override public Job<GBMModel> train() {
    return start(new GBMDriver(), _parms._ntrees);
  }

  // ----------------------
  private class GBMDriver extends H2OCountedCompleter<GBMDriver> {

    @Override protected void compute2() {
      throw H2O.unimpl();
    }
  }
}

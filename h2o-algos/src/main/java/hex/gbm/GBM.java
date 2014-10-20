package hex.gbm;

import hex.schemas.*;
import water.H2O.H2OCountedCompleter;
import water.H2O;
import water.Job;
import water.fvec.Frame;

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
      Frame fr = null;
      GBMModel model = null;
      try {
        // Fetch & read-lock source frame
        fr = _parms._training_frame.get();
        fr.read_lock(_key);

        throw H2O.unimpl();

      } catch( Throwable t ) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        if( model != null ) model.unlock(_key);
        if( fr != null ) fr.unlock(_key);
        done();                 // Job done!
      }
      //tryComplete();
    }
  }
}

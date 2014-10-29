package hex.gbm;

import hex.schemas.GBMV2;
import water.H2O;
import water.H2O.H2OCountedCompleter;
import water.Job;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class GBM extends SharedTree<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {
  // Called from an http request
  public GBM( GBMModel.GBMParameters parms) { super("GBM",parms); }

  @Override public GBMV2 schema() { return new GBMV2(); }

  /** Start the GBM training Job on an F/J thread. */
  @Override public Job<GBMModel> train() {
    if (_parms.sanityCheckParameters() > 0)
      throw new IllegalArgumentException("Invalid parameters for GBM: " + _parms.validationErrors());

    return start(new GBMDriver(), _parms._ntrees/*work for progress bar*/);
  }

  // ----------------------
  private class GBMDriver extends H2OCountedCompleter<GBMDriver> {

    /** Sum of variable empirical improvement in squared-error. The value is not scaled! */
    private transient float[/*nfeatures*/] _improvPerVar;

    @Override protected void compute2() {
      GBMModel model = null;    // Resulting model!
      try {
        _parms.lock_frames(GBM.this); // Fetch & read-lock input frames

        throw H2O.unimpl();

        // Initialize gbm-specific data structures
        //if( _parms._importance ) _improvPerVar = new float[initialModel.nfeatures()];

      } catch( Throwable t ) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.unlock_frames(GBM.this);
        done();                 // Job done!
      }
      //tryComplete();
    }
  }
}

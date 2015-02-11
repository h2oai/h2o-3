package hex.glrm;

import hex.Model;
import hex.ModelBuilder;
import hex.gram.Gram.*;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.GLRMV2;
import hex.gram.Gram.GramTask;
import water.DKV;
import water.H2O;
import water.Job;
import water.util.Log;

/**
 * Generalized Low Rank Models
 * This is an algorithm for dimensionality reduction of numerical data.
 * It is a general, parallelized implementation of PCA with regularization.
 * <a href = "http://web.stanford.edu/~boyd/papers/pdf/glrm.pdf">Generalized Low Rank Models</a>
 * @author anqi_fu
 *
 */
public class GLRM extends ModelBuilder<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {
  static final int MAX_COL = 5000;

  public enum Initialization {
    SVD, PlusPlus
  }

  @Override
  public ModelBuilderSchema schema() {
    return new GLRMV2();
  }

  @Override
  public Job<GLRMModel> trainModel() {
    return start(new GLRMDriver(), 0);
  }

  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{ Model.ModelCategory.Clustering };
  }

  // Called from an http request
  public GLRM(GLRMModel.GLRMParameters parms ) { super("GLRM",parms); init(false); }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if(_train.numCols() < 2) error("_train", "_train must have more than one column");
    if(_parms._lambda < 0) error("_lambda", "lambda must be a non-negative number");
  }

  private class GLRMDriver extends H2O.H2OCountedCompleter<GLRMDriver> {
    @Override
    protected void compute2() {
      GLRMModel model = null;
      try {
        _parms.read_lock_frames(GLRM.this); // Fetch & read-lock input frames
        init(true);
        if( error_count() > 0 ) throw new IllegalArgumentException("Found validation errors: "+validationErrors());

        // The model to be built
        model = new GLRMModel(dest(), _parms, new GLRMModel.GLRMOutput(GLRM.this));
        model.delete_and_lock(_key);

        // TODO: How do we initialize X and Y matrices?

        GramTask gtsk = new GramTask(_xkey, xinfo).doAll(xinfo._adaptedFrame);
        if(_parms._lambda > 0)
          gtsk._gram.addDiag(_parms._lambda);
        Cholesky chol = gtsk._gram.cholesky(null);

        // TODO: Compute X'A or AY' and use Cholesky to solve
        MultTask mtsk = new MultTask(_xkey, xinfo, _key, dinfo).doAll(xinfo._adaptedFrame, dinfo._adaptedFrame);
      } catch( Throwable t ) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.read_unlock_frames(GLRM.this);
      }
      tryComplete();
    }
  }
}

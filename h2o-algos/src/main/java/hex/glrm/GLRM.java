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
import water.MRTask;
import water.fvec.Chunk;
import water.util.Log;
import water.util.ArrayUtils;

import java.util.Arrays;

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
    if(_parms._num_pc > _train.numCols()) error("_num_pc", "_num_pc cannot be greater than the number of columns in _train");
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

        // TODO: Initialize Y' matrix using k-means++
        double[][] yt = new double[_train.numCols()][_parms._num_pc];
        for(int i = 0; i < yt.length; i++) Arrays.fill(yt[i], 0);

        // 1) Compute X = AY'(YY' + \gamma I)^(-1)
        // a) Form Gram matrix YY' and add \gamma to diagonal

        // 2) Compute Y = (X'X + \gamma I)^(-1)X'A
        // a) Form Gram matrix X'X and add \gamma to diagonal
        GramTask gtsk = new GramTask(_xkey, xinfo).doAll(xinfo._adaptedFrame);
        if(_parms._lambda > 0) gtsk._gram.addDiag(_parms._lambda);

        // b) Get Cholesky decomposition of D = X'X + \gamma I
        Cholesky chol = gtsk._gram.cholesky(null);

        // b) Compute A'X and solve for Y' of DY' = A'X
        // TODO: Jam A (original data) and X into single frame dinfo
        yt = new MulTask(_dkey, dinfo).doAll(dinfo._adaptedFrame)._prod;
        for(int i = 0; i < yt.length; i++) chol.solve(yt[i]);

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

  // TODO: Adapt this to work for right multiply AY' as well
  // Computes A'X on a matrix [A, X], where A is n by p, X is n by k, and p < k
  // Resulting matrix D = A'X will have dimensions p by k
  private static class MulTask extends MRTask<MulTask> {
    int _ncolA, _ncolX; // _ncolA = p, _ncolX = k
    double[][] _prod;   // _prod = D = A'X

    MulTask(int ncolA, int ncolX) {
      _ncolA = ncolA;
      _ncolX = ncolX;
      _prod = new double[ncolX][ncolA];
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + _ncolX) == cs.length;

      // Cycle over columns of A
      for( int i = 0; i < _ncolA; i++ ) {
        // Cycle over columns of X
        for(int j = _ncolA; j < _ncolX; j++ ) {
          double sum = 0;
          for( int row = 0; row < cs[0]._len; row++ ) {
            double a = cs[i].atd(row);
            double x = cs[j].atd(row);
            if(Double.isNaN(a) || Double.isNaN(x)) continue;
            sum += a*x;
          }
          _prod[i][j] = sum;
        }
      }
    }

    @Override public void reduce(MulTask other) {
      ArrayUtils.add(_prod, other._prod);
    }
  }
}

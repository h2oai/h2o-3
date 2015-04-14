package hex.svd;

import hex.DataInfo;
import hex.FrameTask;
import hex.Model;
import hex.ModelBuilder;
import hex.gram.Gram;
import hex.gram.Gram.GramTask;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.SVDV2;
import water.*;
import water.fvec.NewChunk;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

/**
 * Singular Value Decomposition
 * <a href = "http://www.cs.yale.edu/homes/el327/datamining2013aFiles/07_singular_value_decomposition.pdf">SVD via Power Method Algorithm</a>
 * <a href = "https://www.cs.cmu.edu/~venkatg/teaching/CStheory-infoage/book-chapter-4.pdf">Proof of Convergence for Power Method</a>
 * @author anqi_fu
 */
public class SVD extends ModelBuilder<SVDModel,SVDModel.SVDParameters,SVDModel.SVDOutput> {
  // Convergence tolerance
  private final double TOLERANCE = 1e-8;

  @Override public ModelBuilderSchema schema() {
    return new SVDV2();
  }

  @Override public Job<SVDModel> trainModel() {
    return start(new SVDDriver(), 0);
  }

  @Override public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{ Model.ModelCategory.DimReduction };
  }

  // Called from an http request
  public SVD(SVDModel.SVDParameters parms) {
    super("SVD", parms);
    init(false);
  }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._max_iterations < 1)
      error("_max_iterations", "max_iterations must be at least 1");
    if(_train != null && (_parms._k < 1 || _parms._k > _train.numCols()))
      error("_k", "Number of singular values must be between 1 and " + _train.numCols());
  }

  class SVDDriver extends H2O.H2OCountedCompleter<SVDDriver> {

    @Override protected void compute2() {
      SVDModel model = null;
      DataInfo dinfo = null;

      try {
        _parms.read_lock_frames(SVD.this); // Fetch & read-lock input frames
        init(true);
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new SVDModel(dest(), _parms, new SVDModel.SVDOutput(SVD.this));
        model.delete_and_lock(_key);
        _train.read_lock(_key);

        // 0) Transform training data and save standardization vectors for use in scoring later
        dinfo = new DataInfo(Key.make(), _train, null, 0, false, _parms._transform, DataInfo.TransformType.NONE, true);
        DKV.put(dinfo._key, dinfo);

        model._output._normSub = dinfo._normSub == null ? new double[_train.numCols()] : Arrays.copyOf(dinfo._normSub, _train.numCols());
        if(dinfo._normMul == null) {
          model._output._normMul = new double[_train.numCols()];
          Arrays.fill(model._output._normMul, 1.0);
        } else
          model._output._normMul = Arrays.copyOf(dinfo._normMul, _train.numCols());

        // 1) Compute Gram of training data
        // NOTE: Gram computes A'A/n where n = nrow(A) = number of rows in training set
        GramTask tsk = new GramTask(self(), dinfo).doAll(dinfo._adaptedFrame);
        double[][] gram = tsk._gram.getXX();
        double norm = Math.sqrt(_train.numRows());    // Normalization constant dependent on n
        double[][] rsvec = new double[_parms._k][gram.length];
        model._output._iterations = 0;

        // 2) Compute and save first k singular values
        for(int i = 0; i < _parms._k; i++) {
          // Iterate x_i <- (A'A/n)x_{i-1} and set v_i = sqrt(n)*x_i/||x_i||
          rsvec[i] = powerLoop(gram, _parms._seed);
          ArrayUtils.mult(rsvec[i], norm);

          // Calculate I - v_iv_i' using current singular value
          double[][] ivv = ArrayUtils.outerProduct(rsvec[i], rsvec[i]);
          for(int j = 0; j < ivv.length; j++) ivv[j][j] = 1 - ivv[j][j];

          // TODO: Update training frame A <- A - \sigma_i u_iv_i' = A - Av_iv_i' = A(I - v_iv_i')
          // This gives Gram matrix A'A <- (I - v_iv_i')A'A(I - v_iv_i')
          double[][] lmat = ArrayUtils.multArrArr(ivv, gram);
          gram = ArrayUtils.multArrArr(lmat, ivv);
          // TODO: Compute \sigma_1 = ||Av_1|| and u_1 = Av_1/\sigma_1 (optional?)
          model._output._iterations++;
        }
        model._output._v = rsvec;
        done();
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
        if( dinfo != null ) dinfo.remove();
        _parms.read_unlock_frames(SVD.this);
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  public double[] powerLoop(double[][] gram) {
    return powerLoop(gram, ArrayUtils.gaussianVector(gram[0].length));
  }
  public double[] powerLoop(double[][] gram, long seed) {
    return powerLoop(gram, ArrayUtils.gaussianVector(gram[0].length, seed));
  }
  public double[] powerLoop(double[][] gram, double[] vinit) {
    assert gram.length == gram[0].length;
    assert vinit.length == gram.length;

    // Set x_i to initial value x_0
    int iters = 0;
    double err = 2 * TOLERANCE;
    double[] v = vinit.clone();
    double[] vnew = new double[v.length];

    // Update x_i <- A'Ax_{i-1} where A'A = Gram of training frame
    while(err > TOLERANCE && iters < _parms._max_iterations) {
      err = 0;
      for (int i = 0; i < v.length; i++) {
        vnew[i] = ArrayUtils.innerProduct(gram[i], v);
        double diff = vnew[i] - v[i];
        err += diff * diff;
      }
      v = vnew;
      iters++;
    }

    // Compute singular vector v = x_i/||x_i||
    ArrayUtils.div(v, ArrayUtils.l2norm(v));
    return v;
  }

  private class SingVal extends FrameTask<SingVal> {
    double[] _svec;   // Input: Right singular vectors (V)
    double _sval;     // Output: Singular values (\sigma)

    public SingVal(Key jobKey, DataInfo dinfo, final double[] svec) {
      super(jobKey, dinfo);
      _svec = svec;
      _sval = 0;
    }

    @Override protected void processRow(long gid, DataInfo.Row row, NewChunk[] outputs) {
      double[] nums = row.numVals;
      assert nums.length == _svec.length;
      double tmp = ArrayUtils.innerProduct(nums, _svec);
      _sval += tmp * tmp;
    }

    @Override protected void postGlobal() {
      _sval = Math.sqrt(_sval);
    }
  }
}

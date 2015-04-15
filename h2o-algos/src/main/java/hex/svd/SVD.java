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
  private final double TOLERANCE = 1e-6;    // Cutoff for estimation error of singular value \sigma_i

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

        // 1) Calculate and save Gram matrix of training data
        // NOTE: Gram computes A'A/n where n = nrow(A) = number of rows in training set
        GramTask tsk = new GramTask(self(), dinfo).doAll(dinfo._adaptedFrame);
        double[][] gram = tsk._gram.getXX();
        double[][] rsvec = new double[_parms._k][gram.length];
        double[] sigma = new double[_parms._k];

        // Keep track of I - \sum_i v_iv_i' where v_i = eigenvector i
        double[][] ivv_sum = new double[gram.length][gram.length];
        for(int i = 0; i < gram.length; i++) ivv_sum[i][i] = 1;

        double[][] gram_update = gram.clone();
        for(int k = 0; k < _parms._k; k++) {
          // 2) Iterate x_i <- (A_k'A_k/n)x_{i-1} until convergence and set v_k = x_i/||x_i||
          rsvec[k] = powerLoop(gram_update, _parms._seed);

          // 3) Residual data A_k = A - \sum_{i=1}^k \sigma_i u_iv_i' = A - \sum_{i=1}^k Av_iv_i' = A(I - \sum_{i=1}^k v_iv_i')
          // 3a) Compute \sigma_k = ||A_{k-1}v_k|| and TODO: u_k = A_{k-1}v_k/\sigma_k (latter during scoring?)
          double[] ivv_vk = ArrayUtils.multArrVec(ivv_sum, rsvec[k]);
          sigma[k] = new MultBArrSVec(self(), dinfo, ivv_vk).doAll(dinfo._adaptedFrame)._sval;

          // 3b) Compute Gram of residual A_k'A_k = (I - \sum_{i=1}^k v_jv_j')A'A(I - \sum_{i=1}^k v_jv_j')
          // Update I - \sum_{i=1}^k v_iv_i' with sum up to current singular value
          double[][] vv = ArrayUtils.outerProduct(rsvec[k], rsvec[k]);
          for(int i = 0; i < vv.length; i++) {
            for(int j = 0; j < i; j++) {
              double diff = ivv_sum[i][j] - vv[i][j];
              ivv_sum[i][j] = ivv_sum[j][i] = diff;
            }
            ivv_sum[i][i] -= vv[i][i];
          }
          double[][] lmat = ArrayUtils.multArrArr(ivv_sum, gram);
          gram_update = ArrayUtils.multArrArr(lmat, ivv_sum);
        }
        model._output._v = ArrayUtils.transpose(rsvec);
        model._output._singular_vals = sigma;
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

    // Set initial value v_0 to standard normal distribution
    int iters = 0;
    double err = 2*TOLERANCE;
    double[] v = vinit.clone();
    double[] vnew = new double[v.length];

    // Update v_i <- (A'Av_{i-1})/||A'Av_{i-1}|| where A'A = Gram matrix of training frame
    while(iters < _parms._max_iterations && err > TOLERANCE) {
      // Compute x_i <- A'Av_{i-1} and ||x_i||
      for (int i = 0; i < v.length; i++)
        vnew[i] = ArrayUtils.innerProduct(gram[i], v);
      double norm = ArrayUtils.l2norm(vnew);

      double diff;
      for (int i = 0; i < v.length; i++) {
        vnew[i] /= norm;        // Compute singular vector v_i = x_i/||x_i||
        diff = v[i] - vnew[i];  // Save error ||v_i - v_{i-1}||
        err += diff * diff;
        v[i] = vnew[i];         // Update v_i for next iteration
      }
      err = Math.sqrt(err);
      iters++;
    }
    return v;
  }

  private class MultBArrSVec extends FrameTask<MultBArrSVec> {
    double[] _svec;   // Input: Right singular vectors (V)
    double _sval;     // Output: Singular values (\sigma)

    public MultBArrSVec(Key jobKey, DataInfo dinfo, final double[] svec) {
      super(jobKey, dinfo);
      _svec = svec;
      _sval = 0;
    }

    @Override protected void processRow(long gid, DataInfo.Row row) {
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

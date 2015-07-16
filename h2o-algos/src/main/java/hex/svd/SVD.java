package hex.svd;

import hex.*;
import hex.gram.Gram;
import hex.gram.Gram.GramTask;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.SVDV99;
import hex.svd.SVDModel.SVDParameters;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
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
  private final double TOLERANCE = 1e-6;    // Cutoff for estimation error of right singular vector

  // Maximum number of columns when categoricals expanded
  private final int MAX_COLS_EXPANDED = 5000;

  // Number of columns in training set (p)
  private transient int _ncolExp;    // With categoricals expanded into 0/1 indicator cols

  @Override public ModelBuilderSchema schema() {
    return new SVDV99();
  }

  @Override public Job<SVDModel> trainModelImpl(long work) {
    return start(new SVDDriver(), work);
  }

  @Override
  public long progressUnits() {
    return _parms._nv+1;
  }


  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{ ModelCategory.DimReduction };
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; };

  // Called from an http request
  public SVD(SVDModel.SVDParameters parms) {
    super("SVD", parms);
    init(false);
  }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    // if (_parms._u_key == null) _parms._u_key = Key.make("SVDUMatrix_" + Key.rand());
    if (_parms._u_name == null || _parms._u_name.length() == 0)
      _parms._u_name = "SVDUMatrix_" + Key.rand();
    if (_parms._max_iterations < 1)
      error("_max_iterations", "max_iterations must be at least 1");

    if(_train == null) return;
    _ncolExp = _train.numColsExp(_parms._use_all_factor_levels, false);
    if (_ncolExp > MAX_COLS_EXPANDED)
      warn("_train", "_train has " + _ncolExp + " columns when categoricals are expanded. Algorithm may be slow.");

    if(_parms._nv < 1 || _parms._nv > _ncolExp)
      error("_nv", "Number of right singular values must be between 1 and " + _ncolExp);
  }

  // public double[] powerLoop(Gram gram) { return powerLoop(gram, ArrayUtils.gaussianVector(gram.fullN())); }
  public double[] powerLoop(Gram gram, long seed) { return powerLoop(gram, ArrayUtils.gaussianVector(gram.fullN(), seed)); }
  public double[] powerLoop(Gram gram, double[] vinit) {
    // TODO: What happens if Gram matrix is essentially zero? Numerical inaccuracies in PUBDEV-1161.
    assert vinit.length == gram.fullN();

    // Set initial value v_0 to standard normal distribution
    int iters = 0;
    double err = 2 * TOLERANCE;
    double[] v = vinit.clone();
    double[] vnew = new double[v.length];

    // Update v_i <- (A'Av_{i-1})/||A'Av_{i-1}|| where A'A = Gram matrix of training frame
    while(iters < _parms._max_iterations && err > TOLERANCE) {
      // Compute x_i <- A'Av_{i-1} and ||x_i||
      gram.mul(v, vnew);
      double norm = ArrayUtils.l2norm(vnew);

      double diff; err = 0;
      for (int i = 0; i < v.length; i++) {
        vnew[i] /= norm;        // Compute singular vector v_i = x_i/||x_i||
        diff = v[i] - vnew[i];  // Save error ||v_i - v_{i-1}||
        err += diff * diff;
        v[i] = vnew[i];         // Update v_i for next iteration
      }
      err = Math.sqrt(err);
      iters++;    // TODO: Should output vector of final iterations for each k
    }
    return v;
  }

  // Compute ivv_sum - vec * vec' for symmetric array ivv_sum
  public static double[][] updateIVVSum(double[][] ivv_sum, double[] vec) {
    double diff;
    for(int i = 0; i < vec.length; i++) {
      for(int j = 0; j < i; j++) {
        diff = ivv_sum[i][j] - vec[i] * vec[j];
        ivv_sum[i][j] = ivv_sum[j][i] = diff;
      }
      ivv_sum[i][i] -= vec[i] * vec[i];
    }
    return ivv_sum;
  }

  class SVDDriver extends H2O.H2OCountedCompleter<SVDDriver> {

    @Override protected void compute2() {
      SVDModel model = null;
      DataInfo dinfo = null;
      Frame u = null;
      Vec[] uvecs = null;

      try {
        init(true);   // Initialize parameters
        _parms.read_lock_frames(SVD.this); // Fetch & read-lock input frames
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new SVDModel(dest(), _parms, new SVDModel.SVDOutput(SVD.this));
        model.delete_and_lock(self());

        // 0) Transform training data and save standardization vectors for use in scoring later
        dinfo = new DataInfo(Key.make(), _train, _valid, 0, _parms._use_all_factor_levels, _parms._transform, DataInfo.TransformType.NONE,
                            /* skipMissing */ true, /* missingBucket */ false, /* weights */ false, /* offset */ false, /* intercept */ false);
        DKV.put(dinfo._key, dinfo);

        // Save adapted frame info for scoring later
        model._output._normSub = dinfo._normSub == null ? new double[dinfo._nums] : dinfo._normSub;
        if(dinfo._normMul == null) {
          model._output._normMul = new double[dinfo._nums];
          Arrays.fill(model._output._normMul, 1.0);
        } else
          model._output._normMul = dinfo._normMul;
        model._output._permutation = dinfo._permutation;
        model._output._nnums = dinfo._nums;
        model._output._ncats = dinfo._cats;
        model._output._catOffsets = dinfo._catOffsets;
        model._output._names_expanded = dinfo.coefNames();

        // Calculate and save Gram matrix of training data
        // NOTE: Gram computes A'A/n where n = nrow(A) = number of rows in training set (excluding rows with NAs)
        GramTask gtsk = new GramTask(self(), dinfo).doAll(dinfo._adaptedFrame);
        Gram gram = gtsk._gram;   // TODO: This ends up with all NaNs if training data has too many missing values
        assert gram.fullN() == _ncolExp;
        model._output._nobs = gtsk._nobs;
        model._output._v = new double[_parms._nv][_ncolExp];
        model._output._total_variance = gram.diagSum() * gtsk._nobs / (gtsk._nobs-1);  // Since gram = X'X/nobs, but variance requires nobs-1 in denominator
        model.update(self());
        update(1);

        // 1) Run one iteration of power method
        // 1a) Initialize right singular vector v_1
        model._output._v[0] = powerLoop(gram, _parms._seed);

        // Keep track of I - \sum_i v_iv_i' where v_i = eigenvector i
        double[][] ivv_sum = new double[_ncolExp][_ncolExp];
        for(int i = 0; i < _ncolExp; i++) ivv_sum[i][i] = 1;

        // 1b) Initialize singular value \sigma_1 and update u_1 <- Av_1
        if(!_parms._only_v) {
          model._output._d = new double[_parms._nv];
          model._output._u_key = Key.make(_parms._u_name);
          uvecs = new Vec[_parms._nv];

          // Compute first singular value \sigma_1
          double[] ivv_vk = ArrayUtils.multArrVec(ivv_sum, model._output._v[0]);
          CalcSigmaU ctsk = new CalcSigmaU(self(), dinfo, ivv_vk).doAll(1, dinfo._adaptedFrame);
          model._output._d[0] = ctsk._sval;
          assert ctsk._nobs == model._output._nobs : "Processed " + ctsk._nobs + " rows but expected " + model._output._nobs;    // Check same number of skipped rows as Gram
          Frame tmp = ctsk.outputFrame();
          uvecs[0] = tmp.vec(0);   // Save output column of U
          tmp.unlock(self());
        }
        model.update(self()); // Update model in K/V store
        update(1);            // One unit of work

        // 1c) Update Gram matrix A_1'A_1 = (I - v_1v_1')A'A(I - v_1v_1')
        updateIVVSum(ivv_sum, model._output._v[0]);
        // double[][] gram_update = ArrayUtils.multArrArr(ArrayUtils.multArrArr(ivv_sum, gram), ivv_sum);
        GramUpdate guptsk = new GramUpdate(self(), dinfo, ivv_sum).doAll(dinfo._adaptedFrame);
        Gram gram_update = guptsk._gram;

        for(int k = 1; k < _parms._nv; k++) {
          if(!isRunning()) break;

          // 2) Iterate x_i <- (A_k'A_k/n)x_{i-1} until convergence and set v_k = x_i/||x_i||
          model._output._v[k] = powerLoop(gram_update, _parms._seed);

          // 3) Residual data A_k = A - \sum_{i=1}^k \sigma_i u_iv_i' = A - \sum_{i=1}^k Av_iv_i' = A(I - \sum_{i=1}^k v_iv_i')
          // 3a) Compute \sigma_k = ||A_{k-1}v_k|| and u_k = A_{k-1}v_k/\sigma_k
          if(!_parms._only_v) {
            double[] ivv_vk = ArrayUtils.multArrVec(ivv_sum, model._output._v[k]);
            CalcSigmaU ctsk = new CalcSigmaU(self(), dinfo, ivv_vk).doAll(1, dinfo._adaptedFrame);
            model._output._d[k] = ctsk._sval;
            assert ctsk._nobs == model._output._nobs : "Processed " + ctsk._nobs + " rows but expected " + model._output._nobs;
            Frame tmp = ctsk.outputFrame();
            uvecs[k] = tmp.vec(0);   // Save output column of U
            tmp.unlock(self());
          }

          // 3b) Compute Gram of residual A_k'A_k = (I - \sum_{i=1}^k v_jv_j')A'A(I - \sum_{i=1}^k v_jv_j')
          updateIVVSum(ivv_sum, model._output._v[k]);   // Update I - \sum_{i=1}^k v_iv_i' with sum up to current singular value
          // gram_update = ArrayUtils.multArrArr(ivv_sum, ArrayUtils.multArrArr(gram, ivv_sum));  // Too slow on wide arrays
          guptsk = new GramUpdate(self(), dinfo, ivv_sum).doAll(dinfo._adaptedFrame);
          gram_update = guptsk._gram;
          model.update(self()); // Update model in K/V store
          update(1);            // One unit of work
        }

        // 4) Normalize output frame columns by singular values to get left singular vectors
        // TODO: Make sure model building consistent if algo cancelled midway
        model._output._v = ArrayUtils.transpose(model._output._v);  // Transpose to get V (since vectors were stored as rows)
        if(!_parms._only_v && _parms._keep_u) {
          u = new Frame(model._output._u_key, null, uvecs);
          DKV.put(u._key, u);

          final double[] sigma = model._output._d;
          new MRTask() {
              @Override public void map(Chunk cs[]) {
              for (int col = 0; col < cs.length; col++) {
                for(int row = 0; row < cs[0].len(); row++) {
                  double x = cs[col].atd(row);
                  cs[col].set(row, x / sigma[col]);
                }
              }
            }
          }.doAll(u);
        }
        model.update(self());
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
        if( u != null & !_parms._keep_u ) u.delete();
        _parms.read_unlock_frames(SVD.this);
      }

      // Job thisJob = DKV.getGet(_key);
      // System.out.println("------------- JOB status: " + Arrays.toString(Job.jobs()));
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  private static class CalcSigmaU extends FrameTask<CalcSigmaU> {
    final double[] _svec;
    public double _sval;
    public long _nobs;

    public CalcSigmaU(Key jobKey, DataInfo dinfo, double[] svec) {
      super(jobKey, dinfo);
      _svec = svec;
      _sval = 0;
    }

    @Override protected void processRow(long gid, DataInfo.Row r, NewChunk[] outputs) {
      double num = r.innerProduct(_svec);
      outputs[0].addNum(num);
      _sval += num * num;
      ++_nobs;
    }

    @Override public void reduce(CalcSigmaU other) {
      _nobs += other._nobs;
      _sval += other._sval;
    }

    @Override protected void postGlobal() {
      _sval = Math.sqrt(_sval);
    }
  }

  private static class GramUpdate extends FrameTask<GramUpdate> {
    final double[][] _ivv;
    public Gram _gram;
    public long _nobs;

    public GramUpdate(Key jobKey, DataInfo dinfo, double[][] ivv) {
      super(jobKey, dinfo);
      assert null != ivv && ivv.length == ivv[0].length;
      _ivv = ivv;
    }

    @Override protected boolean chunkInit(){
      _gram = new Gram(_dinfo.fullN(), 0, _ivv.length, 0, false);
      return true;
    }

    @Override protected void processRow(long gid, DataInfo.Row r) {
      double w = 1; // TODO: add weights to dinfo?
      double[] nums = new double[_ivv.length];
      for(int row = 0; row < _ivv.length; row++)
        nums[row] = r.innerProduct(_ivv[row]);
      _gram.addRow(_dinfo.newDenseRow(nums), w);
      ++_nobs;
    }

    @Override protected void chunkDone(long n){
      double r = 1.0/_nobs;
      _gram.mul(r);
    }

    @Override public void reduce(GramUpdate gt){
      double r1 = (double)_nobs/(_nobs+gt._nobs);
      _gram.mul(r1);
      double r2 = (double)gt._nobs/(_nobs+gt._nobs);
      gt._gram.mul(r2);
      _gram.add(gt._gram);
      _nobs += gt._nobs;
    }
  }
}

package hex.svd;

import Jama.CholeskyDecomposition;
import Jama.Matrix;
import Jama.SingularValueDecomposition;
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

  @Override public Job<SVDModel> trainModelImpl(long work, boolean restartTimer) {
    return start(new SVDDriver(), work, restartTimer);
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

    // private double[] powerLoop(Gram gram) { return powerLoop(gram, ArrayUtils.gaussianVector(gram.fullN())); }
    private double[] powerLoop(Gram gram, long seed) { return powerLoop(gram, ArrayUtils.gaussianVector(gram.fullN(), seed)); }
    private double[] powerLoop(Gram gram, double[] vinit) {
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

    private double computeSigmaU(DataInfo dinfo, SVDModel model, int k, double[][] ivv_sum, Vec[] uvecs) {
      double[] ivv_vk = ArrayUtils.multArrVec(ivv_sum, model._output._v[k]);
      CalcSigmaU ctsk = new CalcSigmaU(self(), dinfo, ivv_vk).doAll(1, dinfo._adaptedFrame);
      model._output._d[k] = ctsk._sval;
      assert ctsk._nobs == model._output._nobs : "Processed " + ctsk._nobs + " rows but expected " + model._output._nobs;    // Check same number of skipped rows as Gram
      Frame tmp = ctsk.outputFrame();
      uvecs[k] = tmp.vec(0);   // Save output column of U
      tmp.unlock(self());
      return model._output._d[k];
    }

    // Algorithm 5.1: Direct SVD from Halko et al (http://arxiv.org/pdf/0909.4061.pdf)
    private Frame directSVD(DataInfo  aqinfo, DataInfo qinfo, SVDModel model) {
      // 1) Form the matrix B = Q'A = (A'Q)'
      SMulTask stsk = new SMulTask(_train.numCols(), _ncolExp, model._output._ncats, _parms._nv, model._output._normSub, model._output._normMul, model._output._catOffsets, _parms._use_all_factor_levels).doAll(aqinfo._adaptedFrame);
      double[][] qta = ArrayUtils.transpose(stsk._atq);

      // 2) Compute SVD of small matrix B = WDV'
      Matrix qtaJ = new Matrix(qta);
      SingularValueDecomposition svdJ = qtaJ.svd();

      // 3) Form orthonormal matrix U = QW
      double[][] utilde = svdJ.getU().getArray();
      BMulTask btsk = new BMulTask(self(), qinfo, ArrayUtils.transpose(utilde)).doAll(_parms._nv, qinfo._adaptedFrame);
      Frame u = btsk.outputFrame(model._output._u_key, null, null);

      model._output._d = svdJ.getSingularValues();
      model._output._v = svdJ.getV().getArray();
      return u;
    }

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
        dinfo = new DataInfo(Key.make(), _train, _valid, 0, _parms._use_all_factor_levels, _parms._transform, DataInfo.TransformType.NONE, /* skipMissing */ !_parms._impute_missing, /* imputeMissing */ _parms._impute_missing, /* missingBucket */ false, /* weights */ false, /* offset */ false, /* fold */ false, /* intercept */ false);
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
        model._output._total_variance = gram.diagSum() * gtsk._nobs / (gtsk._nobs-1);  // Since gram = X'X/nobs, but variance requires nobs-1 in denominator
        model.update(self());
        update(1);

        if(_parms._svd_method == SVDParameters.Method.GramSVD) {
          // Calculate SVD of G = A'A/n and back out SVD of A. If SVD of A = UDV' then A'A/n = V(D^2/n)V'
          Matrix gramJ = new Matrix(gtsk._gram.getXX());
          SingularValueDecomposition svdJ = gramJ.svd();

          // Output diagonal of D
          double[] sval = svdJ.getSingularValues();
          model._output._d = new double[_parms._nv];    // Only want rank = nv diagonal values
          for(int k = 0; k < _parms._nv; k++)
            model._output._d[k] = Math.sqrt(sval[k] * model._output._nobs);

          // Output right singular vectors V
          double[][] v = svdJ.getV().getArray();
          assert v.length == _ncolExp && dinfo._adaptedFrame.numColsExp(_parms._use_all_factor_levels, false) == _ncolExp;
          model._output._v = new double[_ncolExp][_parms._nv];  // Only want rank = nv decomposition
          for(int i = 0; i < v.length; i++)
            System.arraycopy(v[i], 0, model._output._v[i], 0, _parms._nv);

          // Calculate left singular vectors U = AVD^(-1) if requested
          if(!_parms._only_v && _parms._keep_u) {
            model._output._u_key = Key.make(_parms._u_name);
            double[][] vt = ArrayUtils.transpose(model._output._v);
            for (int k = 0; k < _parms._nv; k++)
              ArrayUtils.div(vt[k], model._output._d[k]);
            BMulTask tsk = new BMulTask(self(), dinfo, vt).doAll(_parms._nv, dinfo._adaptedFrame);
            u = tsk.outputFrame(model._output._u_key, null, null);
          }
        } else {
          // 1) Run one iteration of power method
          // 1a) Initialize right singular vector v_1
          model._output._v = new double[_parms._nv][_ncolExp];  // Store V' for ease of use and transpose back at end
          model._output._v[0] = powerLoop(gram, _parms._seed);

          // Keep track of I - \sum_i v_iv_i' where v_i = eigenvector i
          double[][] ivv_sum = new double[_ncolExp][_ncolExp];
          for (int i = 0; i < _ncolExp; i++) ivv_sum[i][i] = 1;

          // 1b) Initialize singular value \sigma_1 and update u_1 <- Av_1
          if (!_parms._only_v) {
            model._output._d = new double[_parms._nv];
            model._output._u_key = Key.make(_parms._u_name);
            uvecs = new Vec[_parms._nv];
            computeSigmaU(dinfo, model, 0, ivv_sum, uvecs);  // Compute first singular value \sigma_1
          }
          model.update(self()); // Update model in K/V store
          update(1);            // One unit of work

          // 1c) Update Gram matrix A_1'A_1 = (I - v_1v_1')A'A(I - v_1v_1')
          updateIVVSum(ivv_sum, model._output._v[0]);
          // double[][] gram_update = ArrayUtils.multArrArr(ArrayUtils.multArrArr(ivv_sum, gram), ivv_sum);
          GramUpdate guptsk = new GramUpdate(self(), dinfo, ivv_sum).doAll(dinfo._adaptedFrame);
          Gram gram_update = guptsk._gram;

          for (int k = 1; k < _parms._nv; k++) {
            if (!isRunning()) break;

            // 2) Iterate x_i <- (A_k'A_k/n)x_{i-1} until convergence and set v_k = x_i/||x_i||
            model._output._v[k] = powerLoop(gram_update, _parms._seed);

            // 3) Residual data A_k = A - \sum_{i=1}^k \sigma_i u_iv_i' = A - \sum_{i=1}^k Av_iv_i' = A(I - \sum_{i=1}^k v_iv_i')
            // 3a) Compute \sigma_k = ||A_{k-1}v_k|| and u_k = A_{k-1}v_k/\sigma_k
            if (!_parms._only_v)
              computeSigmaU(dinfo, model, k, ivv_sum, uvecs);

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

          if (!_parms._only_v && !_parms._keep_u) {          // Delete U vecs if computed, but user does not want it returned
            for (int i = 0; i < uvecs.length; i++) uvecs[i].remove();
          } else if (!_parms._only_v && _parms._keep_u) {   // Divide U cols by singular values and save to DKV
            u = new Frame(model._output._u_key, null, uvecs);
            DKV.put(u._key, u);
            DivideU utsk = new DivideU(model._output._d);
            utsk.doAll(u);
          }
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
        updateModelOutput();
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

  private static class DivideU extends MRTask<DivideU> {
    final double[] _sigma;

    public DivideU(double[] sigma) {
      _sigma = sigma;
    }

    @Override public void map(Chunk cs[]) {
      assert _sigma.length == cs.length;

      for (int col = 0; col < cs.length; col++) {
        for(int row = 0; row < cs[0].len(); row++) {
          double x = cs[col].atd(row);
          cs[col].set(row, x / _sigma[col]);
        }
      }
    }
  }

  // Computes XY where X is n by k and Y is k by p
  private static class BMulTask extends FrameTask<BMulTask> {
    final double[][] _yt;   // _yt = Y' (transpose of Y)

    public BMulTask(Key jobKey, DataInfo dinfo, double[][] yt) {
      super(jobKey, dinfo);
      _yt = yt;
    }

    @Override protected void processRow(long gid, DataInfo.Row row, NewChunk[] outputs) {
      for(int p = 0; p < _yt.length; p++) {
        double x = row.innerProduct(_yt[p]);
        outputs[p].addNum(x);
      }
    }
  }

  // Given Cholesky L from A'A = LL', compute Q from A = QR decomposition, where R = L'
  private static class QRfromChol extends MRTask<QRfromChol> {
    final int _ncolA;     // Number of cols in A
    final int _ncolExp;   // Number of cols in A with categoricals expanded
    final int _ncats;     // Number of categorical cols in A
    final int _ncolQ;     // Number of cols in Q
    final double[] _normSub;  // For standardizing A
    final double[] _normMul;
    final int[] _catOffsets;  // Categorical offsets for A
    final int _numStart;      // Beginning of numeric cols when categorical cols expanded
    boolean _use_all_factor_levels;   // Use all factor levels when expanding A?
    final double[][] _L;

    public QRfromChol( CholeskyDecomposition chol, double nobs, int ncolA, int ncolExp, int ncats, int ncolQ, double[] normSub, double[] normMul, int[] catOffsets, boolean use_all_factor_levels) {
      _ncolA = ncolA;
      _ncolExp = ncolExp;
      _ncats = ncats;
      _ncolQ = ncolQ;
      _normSub = normSub;
      _normMul = normMul;
      _catOffsets = catOffsets;
      _numStart = _catOffsets[_ncats-1];
      _use_all_factor_levels = use_all_factor_levels;

      _L = chol.getL().getArray();
      ArrayUtils.mult(_L, Math.sqrt(nobs));   // Must scale since Cholesky of A'A/nobs where nobs = nrow(A)
    }

    public final void forwardSolve(double[][] L, double[] b) {
      assert L != null && L.length == L[0].length && L.length == b.length;

      for(int i = 0; i < b.length; i++) {
        double sum = 0;
        for(int j = 0; j < i; j++)
          sum += L[i][j] * b[j];
        b[i] = (b[i] - sum) / L[i][i];
      }
    }

    // Input frame is [A,Q] where we write to Q
    @Override public void map(Chunk cs[]) {
      assert (_ncolA + _ncolQ) == cs.length;
      double[] qrow = new double[_ncolQ];

      for(int row = 0; row < cs[0]._len; row++) {
        // 1) Extract single expanded row of A
        // Categorical columns
        for(int p = 0; p < _ncats; p++) {
          double a = cs[p].atd(row);
          if(Double.isNaN(a)) continue;

          int last_cat = _catOffsets[p+1]-_catOffsets[p]-1;
          int level = (int)a - (_use_all_factor_levels ? 0:1);  // Reduce index by 1 if first factor level dropped during training
          if (level < 0 || level > last_cat) continue;  // Skip categorical level in test set but not in train
          qrow[_catOffsets[p] + level] = 1;
        }

        // Numeric columns
        int pnum = 0;
        int pexp = _numStart;
        for(int p = _ncats; p < _ncolA; p++) {
          double a = cs[p].atd(row);
          qrow[pexp] = (a - _normSub[pnum]) * _normMul[pnum];
          pexp++; pnum++;
        }

        // 2) Solve for single row of Q using forward substitution
        forwardSolve(_L, qrow);

        // 3) Save row of solved values into Q
        int i = 0;
        for(int d = _ncolA; d < _ncolA+_ncolQ; d++)
          cs[d].set(row, qrow[i]);
        assert i == qrow.length;
      }
    }
  }

  // Computes A'Q where A is n by p and Q is n by k
  private static class SMulTask extends MRTask<SMulTask> {
    final int _ncolA;     // Number of cols in A
    final int _ncolExp;   // Number of cols in A with categoricals expanded
    final int _ncats;     // Number of categorical cols in A
    final int _ncolQ;     // Number of cols in Q
    final double[] _normSub;  // For standardizing A
    final double[] _normMul;
    final int[] _catOffsets;  // Categorical offsets for A
    final int _numStart;      // Beginning of numeric cols when categorical cols expanded
    boolean _use_all_factor_levels;   // Use all factor levels when expanding A?

    double[][] _atq;    // Output: A'Q is p_exp by k, where p_exp = number of cols in A with categoricals expanded

    public SMulTask(int ncolA, int ncolExp, int ncats, int ncolQ, double[] normSub, double[] normMul, int[] catOffsets, boolean use_all_factor_levels) {
      assert normSub != null && normSub.length == ncats;
      assert normMul != null && normMul.length == ncats;
      assert catOffsets != null && (catOffsets.length-1) == ncats;

      _ncolA = ncolA;
      _ncolExp = ncolExp;
      _ncats = ncats;
      _ncolQ = ncolQ;
      _normSub = normSub;
      _normMul = normMul;
      _catOffsets = catOffsets;
      _numStart = _catOffsets[_ncats-1];
      _use_all_factor_levels = use_all_factor_levels;
    }

    // Input frame is [A,Q]
    @Override public void map(Chunk cs[]) {
      assert (_ncolA + _ncolQ) == cs.length;
      _atq = new double[_ncolExp][_ncolQ];

      for(int k = _ncolA; k < (_ncolA + _ncolQ); k++) {
        // Categorical columns
        for(int p = 0; p < _ncats; p++) {
          for(int row = 0; row < cs[0]._len; row++) {
            double q = cs[k].atd(row);
            double a = cs[p].atd(row);
            if (Double.isNaN(a)) continue;

            int last_cat = _catOffsets[p+1]-_catOffsets[p]-1;
            int level = (int)a - (_use_all_factor_levels ? 0:1);  // Reduce index by 1 if first factor level dropped during training
            if (level < 0 || level > last_cat) continue;  // Skip categorical level in test set but not in train
            _atq[_catOffsets[p] + level][k] += q;
          }
        }

        // Numeric columns
        int pnum = 0;
        int pexp = _numStart;
        for(int p = _ncats; p < _ncolA; p++) {
          for(int row = 0; row  < cs[0]._len; row++) {
            double q = cs[k].atd(row);
            double a = cs[p].atd(row);
            _atq[pexp][k] += q * (a - _normSub[pnum]) * _normMul[pnum];
          }
          pexp++; pnum++;
        }
      }
    }

    @Override public void reduce(SMulTask other) {
      ArrayUtils.add(_atq, other._atq);
    }
  }
}

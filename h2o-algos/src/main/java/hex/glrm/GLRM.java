package hex.glrm;

import hex.DataInfo;
import hex.DataInfo.Row;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.Model;
import hex.ModelBuilder;
import hex.gram.Gram.*;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.GLRMV2;
import hex.gram.Gram.GramTask;
import hex.FrameTask;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
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
    return new Model.ModelCategory[]{Model.ModelCategory.Clustering};
  }

  // Called from an http request
  public GLRM(GLRMModel.GLRMParameters parms) {
    super("GLRM", parms);
    init(false);
  }

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    if (_train.numCols() < 2) error("_train", "_train must have more than one column");
    if (_parms._num_pc > _train.numCols())
      error("_num_pc", "_num_pc cannot be greater than the number of columns in _train");
    if (_parms._gamma < 0) error("_gamma", "lambda must be a non-negative number");

    Vec[] vecs = _train.vecs();
    for (int i = 0; i < vecs.length; i++) {
      if (!vecs[i].isNumeric()) throw H2O.unimpl();
    }
  }

  private class GLRMDriver extends H2O.H2OCountedCompleter<GLRMDriver> {

    // Initialize Y to be the k centers from k-means++
    double[][] initialY(GLRMModel model) {
      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = _parms._train;
      parms._ignored_columns = _parms._ignored_columns;
      parms._dropConsCols = _parms._dropConsCols;
      parms._dropNA20Cols = _parms._dropNA20Cols;
      parms._max_confusion_matrix_size = _parms._max_confusion_matrix_size;
      parms._score_each_iteration = _parms._score_each_iteration;
      parms._init = KMeans.Initialization.PlusPlus;
      parms._k = _parms._num_pc;
      parms._max_iterations = _parms._max_iterations;
      parms._standardize = _parms._standardize;

      KMeansModel km = null;
      KMeans job = null;
      try {
        job = new KMeans(parms);
        km = job.trainModel().get();
      } finally {
        if (job != null) job.remove();
      }

      return km._output._centers_raw;
    }

    // Add constant \gamma to the diagonal of a k by k symmetric matrix X
    double[] addDiag(double[][] x, double gamma) {
      if (x == null) return null;
      if (x.length != x[0].length)
        throw new IllegalArgumentException("x must be a symmetric matrix!");

      int len = x.length;
      double[] diag = new double[len];
      if (gamma == 0) return diag;
      for (int i = 0; i < len; i++) {
        x[i][i] += gamma;
        diag[i] = gamma;
      }
      return diag;
    }

    // Given a n by k matrix X, form its k by k Gram matrix X'X
    double[][] formGram(double[][] x) {
      if (x == null) return null;
      int ncol = x[0].length;
      double[][] xgram = new double[ncol][ncol];

      // Compute all entries on and above diagonal
      for (int i = 0; i < x.length; i++) {
        // Outer product = x[i]' * x[i], where x[i] is row i
        for (int j = 0; j < ncol; j++) {
          for (int k = j; k < ncol; k++)
            xgram[j][k] += x[i][j] * x[i][k];
        }
      }

      // Fill in entries below diagonal since Gram is symmetric
      for (int i = 0; i < x.length; i++) {
        for (int j = 0; j < ncol; j++) {
          for (int k = 0; k < j; k++)
            xgram[j][k] = xgram[k][j];
        }
      }
      return xgram;
    }

    // Stopping criteria
    boolean isDone(GLRMModel model) {
      if (!isRunning()) return true; // Stopped/cancelled
      // Stopped for running out iterations
      if (model._output._iterations > _parms._max_iterations) return true;

      // TODO: Stop if average change < _parms._tolerance
      return false;             // Not stopping
    }

    // Main worker thread
    @Override
    protected void compute2() {
      GLRMModel model = null;

      try {
        _parms.read_lock_frames(GLRM.this); // Fetch & read-lock input frames
        init(true);
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new GLRMModel(dest(), _parms, new GLRMModel.GLRMOutput(GLRM.this));
        model.delete_and_lock(_key);
        _train.read_lock(_key);

        // 0) Initialize Y' matrix using k-means++
        double[][] yt = ArrayUtils.transpose(initialY(model));

        // Jam A and X into a single frame [A,X] for distributed computation
        // A is read-only training data, X will be modified in place every iteration
        Vec[] xvecs = new Vec[_parms._num_pc];
        Vec[] vecs = new Vec[_train.numCols() + _parms._num_pc];
        for (int i = 0; i < _train.numCols(); i++) vecs[i] = _train.vec(i);
        int c = 0;
        for (int i = _train.numCols(); i < vecs.length; i++) {
          vecs[i] = _train.anyVec().makeZero();
          xvecs[c++] = vecs[i];
        }
        assert c == xvecs.length;

        Frame fr = new Frame(Key.make(), null, vecs);
        DataInfo dinfo = new DataInfo(fr._key, fr, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        DKV.put(dinfo._key, dinfo);

        // Create separate reference to X for Gram task
        Frame x = new Frame(Key.make(), null, xvecs);
        DataInfo xinfo = new DataInfo(x._key, x, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);

        while (!isDone(model)) {
          // 1) Compute X = AY'(YY' + \gamma I)^(-1)
          double[][] ygram = formGram(yt);    // a) Form Gram matrix of Y', which is (Y')'Y' = YY'

          // b) Get Cholesky decomposition of D = Y'Y + \gamma I
          double[] diag = addDiag(ygram, _parms._gamma);
          // CholeskyDecomposition yychol = new Matrix(ygram).chol();
          Cholesky yychol = new Cholesky(ygram, diag);

          // c) Compute AY' and solve for X of XD = AY'
          CholMulTask cmtsk = new CholMulTask(self(), dinfo, yychol, yt, _train.numCols(), _parms._num_pc);
          cmtsk.doAll(dinfo._adaptedFrame);
          model._output._avg_change = cmtsk._err / fr.numRows();

          // 2) Compute Y = (X'X + \gamma I)^(-1)X'A
          // a) Form Gram matrix X'X
          GramTask xgram = new GramTask(self(), xinfo).doAll(xinfo._adaptedFrame);

          // b) Get Cholesky decomposition of D = X'X + \gamma I
          if (_parms._gamma > 0) xgram._gram.addDiag(_parms._gamma);
          Cholesky xxchol = xgram._gram.cholesky(null);

          // c) Compute A'X and solve for Y' of DY' = A'X
          yt = new SMulTask(_train.numCols(), _parms._num_pc).doAll(dinfo._adaptedFrame)._prod;
          for (int i = 0; i < yt.length; i++) xxchol.solve(yt[i]);
          // TODO: Calculate average error on yt (save yt_old)
          // model._output._avg_change += average error on yt from yt_old

          model._output._iterations++;
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work
        }

        // TODO: Should we compute difference ||A - XY||?
        // 3) Compute solution XY
        BMulTask tsk = new BMulTask(self(), xinfo, yt).doAll(_parms._num_pc, xinfo._adaptedFrame);
        String[] names = new String[_parms._num_pc];
        for(int i = 0; i < names.length; i++) names[i] = "PC" + String.valueOf(i);
        tsk.outputFrame(names, null);
        // TODO: Need to save and output final Frame

        done();
      } catch (Throwable t) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        if (model != null) model.unlock(_key);
        _parms.read_unlock_frames(GLRM.this);
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  // Computes A'X on a matrix [A,X], where A is n by p, X is n by k, and k <= p
  // Resulting matrix D = A'X will have dimensions p by k
  private static class SMulTask extends MRTask<SMulTask> {
    int _ncolA, _ncolX; // _ncolA = p, _ncolX = k
    double[][] _prod;   // _prod = D = A'X

    SMulTask(final int ncolA, final int ncolX) {
      _ncolA = ncolA;
      _ncolX = ncolX;
      _prod = new double[ncolX][ncolA];
    }

    @Override
    public void map(Chunk[] cs) {
      assert (_ncolA + _ncolX) == cs.length;

      // Cycle over columns of A
      for (int i = 0; i < _ncolA; i++) {
        // Cycle over columns of X
        for (int j = _ncolA; j < _ncolX; j++) {
          double sum = 0;
          for (int row = 0; row < cs[0]._len; row++) {
            double a = cs[i].atd(row);
            double x = cs[j].atd(row);
            if (Double.isNaN(a) || Double.isNaN(x)) continue;
            sum += a * x;
          }
          _prod[i][j] = sum;
        }
      }
    }

    @Override
    public void reduce(SMulTask other) {
      ArrayUtils.add(_prod, other._prod);
    }
  }

  // Computes XY where X is n by k, Y is k by p, and k <= p
  //  Resulting matrix Z = XY will have dimensions n by k
  private static class BMulTask extends FrameTask<BMulTask> {
    double[][] _yt;   // _yt = Y' (transpose of Y)

    BMulTask(Key jobKey, DataInfo dinfo, final double[][] yt) {
      super(jobKey, dinfo);
      _yt = yt;
    }

    @Override protected void processRow(long gid, Row row, NewChunk[] outputs) {
      double[] nums = row.numVals;
      assert nums.length == _yt[0].length;

      for(int k = 0; k < _yt[0].length; k++) {
        double x = 0;
        int c = _dinfo.numStart();
        for(int d = 0; d < nums.length; d++)
          x += nums[d] * _yt[k][c++];
        assert c == _yt[0].length;
        outputs[k].addNum(x);
      }
    }
  }

  // TODO: Generalize this with boolean flag indicating whether to transpose Y
  // Solves XD = AY' for X where A is n by p, Y is k by p, D is k by k, and n >> p > k
  // Resulting matrix X = (AY')D^(-1) will have dimensions n by k
  private static class CholMulTask extends FrameTask<CholMulTask> {
    int _ncolA;       // _ncolA = p (number of training cols)
    int _ncolX;       // _ncolX = k (number of PCs)
    double[][] _yt;   // _yt = Y' (transpose of Y)
    Cholesky _chol;   // Cholesky decomposition of D' (transpose of D, since need left multiply)

    double _err;      // Total sum of squared error over rows of X

    CholMulTask(Key jobKey, DataInfo dinfo, final Cholesky chol, final double[][] yt) {
      this(jobKey, dinfo, chol, yt, yt.length, yt[0].length);
    }

    CholMulTask(Key jobKey, DataInfo dinfo, final Cholesky chol, final double[][] yt, final int ncolA, final int ncolX) {
      super(jobKey, dinfo);   // dinfo = [A,X] jammed into single frame
      assert yt != null && yt.length == ncolA && yt[0].length == ncolX;
      _chol = chol;
      _yt = yt;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _err = 0;
    }

    @Override protected void processRow(long gid, Row row) {
      double[] nums = row.numVals;
      double[] xrow = new double[_ncolX];
      assert (_ncolX + _ncolA) == nums.length;

      // Compute single row of AY'
      for(int k = 0; k < _ncolX; k++) {
        double x = 0;
        int c = _dinfo.numStart();
        for(int d = 0; d < _ncolA; d++)
          x += nums[d] * _yt[c++][k];
        assert c == _yt.length;
        xrow[k] = x;
      }

      // Cholesky solve for single row of X
      _chol.solve(xrow);

      // Update X in place with new solved values
      int i = 0;
      for(int d = _ncolA; d < nums.length; d++) {
        // Sum of squared error compared to row of old X
        _err += xrow[i] - nums[d];
        row.addNum(d, xrow[i++]);
      }
      assert i == xrow.length;
    }

    @Override public void reduce(CholMulTask other) {
      _err += other._err;
    }
  }
}

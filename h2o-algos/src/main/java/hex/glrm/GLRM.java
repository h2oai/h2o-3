package hex.glrm;

import Jama.CholeskyDecomposition;
import Jama.Matrix;
import Jama.QRDecomposition;
import Jama.SingularValueDecomposition;
import hex.DMatrix;
import hex.DataInfo;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.algos.glrm.GlrmInitialization;
import hex.genmodel.algos.glrm.GlrmLoss;
import hex.genmodel.algos.glrm.GlrmMojoModel;
import hex.genmodel.algos.glrm.GlrmRegularizer;
import hex.glrm.GLRMModel.GLRMParameters;
import hex.gram.Gram;
import hex.gram.Gram.Cholesky;
import hex.gram.Gram.GramTask;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.svd.SVD;
import hex.svd.SVDModel;
import hex.svd.SVDModel.SVDParameters;
import hex.util.LinearAlgebraUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.api.ModelCacheManager;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static hex.genmodel.algos.glrm.GlrmLoss.Quadratic;
import static hex.util.DimensionReductionUtils.generateIPC;
import static water.util.ArrayUtils.transpose;

/**
 * Generalized Low Rank Models
 * This is an algorithm for dimensionality reduction of a dataset. It is a general, parallelized
 * optimization algorithm that applies to a variety of loss and regularization functions.
 * Categorical columns are handled by expansion into 0/1 indicator columns for each level.
 * <a href = "http://web.stanford.edu/~boyd/papers/pdf/glrm.pdf">Generalized Low Rank Models</a>
 * @author anqi_fu
 */
public class GLRM extends ModelBuilder<GLRMModel, GLRMModel.GLRMParameters, GLRMModel.GLRMOutput> {
  // Convergence tolerance
  private static final double TOLERANCE = 1e-10;

  // Number of columns in the training set (n)
  private transient int _ncolA;

  // Number of columns in the resulting matrix Y, taking into account expansion of the categoricals
  private transient int _ncolY;

  // Number of columns in the resulting matrix X (k), also number of rows in Y.
  private transient int _ncolX;

  boolean _wideDataset = false;         // default with wideDataset set to be false.
  // Loss function for each column
  private transient GlrmLoss[] _lossFunc;

  private ArrayList<Integer> _binaryColumnIndices;  // store binary columns using binary loss functions.

  @Override protected GLRMDriver trainModelImpl() { return new GLRMDriver(); }
  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ModelCategory.Clustering}; }
  @Override public boolean isSupervised() { return false; }

  @Override public boolean havePojo() { return false; }
  @Override public boolean haveMojo() { return true; }

  @Override protected void checkMemoryFootPrint_impl() {
    HeartBeat hb = H2O.SELF._heartbeat;
    double p = hex.util.LinearAlgebraUtils.numColsExp(_train,true);
    double r = _train.numRows();
    long mem_usage = (long)(hb._cpus_allowed * p*_parms._k * 8*2);  // loose estimation of memory usage
    long mem_usage_w = (long)(hb._cpus_allowed * r*_parms._k * 8*2);  // loose estimation of memory usage
    long max_mem = H2O.SELF._heartbeat.get_free_mem();
    if ((mem_usage > max_mem) && (mem_usage_w > max_mem)) {
      String msg = "Archtypes in matrix Y cannot fit in the driver node's memory ("
              + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
              + ") - try reducing k, the number of columns and/or the number of categorical factors.";
      error("_train", msg);
    }

    if (mem_usage > mem_usage_w) {  // choose the most memory efficient one
      _wideDataset = true;   // set to true if wide dataset is detected
    }
  }

  /*
		Set value of wideDataset.  Note that this routine is used for test purposes only and not for users.
*/
  public void setWideDataset(boolean isWide) {
    _wideDataset = isWide;
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Model initialization
  //--------------------------------------------------------------------------------------------------------------------

  // Called from an http request
  public GLRM(GLRMParameters parms) {
    super(parms);
    init(false);
  }
  public GLRM(GLRMParameters parms, Job<GLRMModel> job) {
    super(parms, job);
    init(false);
  }
  public GLRM(boolean startup_once) {
    super(new GLRMParameters(), startup_once);
  }

  /**
   * Validate all parameters, and prepare the model for training.
   */
  @Override public void init(boolean expensive) {
    super.init(expensive);

    _ncolX = _parms._k;
    _ncolA = _train == null? -1 : _train.numCols();
    _ncolY = _train == null? -1 : LinearAlgebraUtils.numColsExp(_train, true);

    initLoss();

    if (_parms._gamma_x < 0) error("_gamma_x", "gamma must be a non-negative number");
    if (_parms._gamma_y < 0) error("_gamma_y", "gamma_y must be a non-negative number");
    if (_parms._max_iterations < 1 || _parms._max_iterations > 1e6)
      error("_max_iterations", "max_iterations must be between 1 and 1e6 inclusive");
    if (_parms._init_step_size <= 0)
      error ("_init_step_size", "init_step_size must be a positive number");
    if (_parms._min_step_size < 0 || _parms._min_step_size > _parms._init_step_size)
      error("_min_step_size", "min_step_size must be between 0 and " + _parms._init_step_size);

    // Cannot recover SVD of original _train from XY of transformed _train
    if (_parms._recover_svd && (_parms._impute_original && _parms._transform != DataInfo.TransformType.NONE))
      error("_recover_svd", "_recover_svd and _impute_original cannot both be true if _train" +
              " is transformed");

    if (_train == null) return;
    if (_ncolA < 2)
      error("_train", "_train must have more than one column");
    if (_valid != null && _valid.numRows() != _train.numRows())
      error("_valid", "_valid must have same number of rows as _train");

    if (_ncolY > 5000)
      warn("_train", "_train has " + _ncolY + " columns when categoricals are expanded. Algorithm" +
              " may be slow.");

    if (_parms._k < 1 || _parms._k > _ncolY) error("_k", "_k must be between 1 and " + _ncolY +
            " inclusive");
    if (_parms._user_y != null) { // Check dimensions of user-specified initial Y
      if (_parms._init != GlrmInitialization.User)
        error("_init", "init must be 'User' if providing user-specified points");

      Frame user_y = _parms._user_y.get();
      assert user_y != null;
      int user_y_cols = _parms._expand_user_y ? _ncolA : _ncolY;

      // Check dimensions of user-specified initial Y
      if (user_y.numCols() != user_y_cols)
        error("_user_y", "The user-specified Y must have the same number of columns ("
                + user_y_cols + ") " +
                "as the training observations");
      else if (user_y.numRows() != _parms._k)
        error("_user_y", "The user-specified Y must have k = " + _parms._k + " rows");
      else {
        int zero_vec = 0;
        Vec[] centersVecs = user_y.vecs();
        for (int c = 0; c < _ncolA; c++) {
          if (centersVecs[c].naCnt() > 0) {
            error("_user_y", "The user-specified Y cannot contain any missing values");
            break;
          } else if (centersVecs[c].isConst() && centersVecs[c].max() == 0)
            zero_vec++;
        }
        if (zero_vec == _ncolA)
          error("_user_y", "The user-specified Y cannot all be zero");
      }
    }

    if (_parms._user_x != null) { // Check dimensions of user-specified initial X
      if (_parms._init != GlrmInitialization.User)
        error("_init", "init must be 'User' if providing user-specified points");

      Frame user_x = _parms._user_x.get();
      assert user_x != null;

      if (user_x.numCols() != _parms._k)
        error("_user_x", "The user-specified X must have k = " + _parms._k + " columns");
      else if (user_x.numRows() != _train.numRows())
        error("_user_x", "The user-specified X must have the same number of rows " +
                "(" + _train.numRows() + ") as the training observations");
      else {
        int zero_vec = 0;
        Vec[] centersVecs = user_x.vecs();
        for (int c = 0; c < _parms._k; c++) {
          if (centersVecs[c].naCnt() > 0) {
            error("_user_x", "The user-specified X cannot contain any missing values");
            break;
          } else if (centersVecs[c].isConst() && centersVecs[c].max() == 0)
            zero_vec++;
        }
        if (zero_vec == _parms._k)
          error("_user_x", "The user-specified X cannot all be zero");
      }
    }

    for (int i = 0; i < _ncolA; i++) {
      if (_train.vec(i).isString() || _train.vec(i).isUUID())
        throw H2O.unimpl("GLRM cannot handle String or UUID data");
    }

    if (expensive && error_count() == 0) checkMemoryFootPrint();  // check to make sure we can fit.
  }

  /** Validate all Loss-related parameters, and fill in the `_lossFunc` array. */
  private void initLoss() {
    int num_loss_by_cols = _parms._loss_by_col == null? 0 : _parms._loss_by_col.length;
    int num_loss_by_cols_idx = _parms._loss_by_col_idx == null? 0 : _parms._loss_by_col_idx.length;

    // First validate the parameters that do not require access to the training frame
    if (_parms._period <= 0)
      error("_period", "_period must be a positive integer");
    if (!_parms._loss.isForNumeric())
      error("_loss", _parms._loss + " is not a numeric loss function");
    if (!_parms._multi_loss.isForCategorical())
      error("_multi_loss", _parms._multi_loss + " is not a multivariate loss function");
    if (num_loss_by_cols != num_loss_by_cols_idx && num_loss_by_cols_idx > 0)
      error("_loss_by_col", "Sizes of arrays _loss_by_col and _loss_by_col_idx must be the same");

    if (_train == null) return;

    _binaryColumnIndices = new ArrayList<Integer>();

    // Initialize the default loss functions for each column
    // Note: right now for binary columns `.isCategorical()` returns true. It has the undesired consequence that
    // such variables will get categorical loss function, and will get expanded into 2 columns.
    _lossFunc = new GlrmLoss[_ncolA];
    for (int i = 0; i < _ncolA; i++) {
      Vec vi = _train.vec(i);
      _lossFunc[i] = vi.isCategorical()? _parms._multi_loss : _parms._loss;
    }

    String[] origColumnNames = _parms.train().names();  // grab original frame column names before change
    ArrayList<String> newColumnNames = new ArrayList<String>(Arrays.asList(_train._names));
    // If _loss_by_col is provided, then override loss functions on the specified columns
    if (num_loss_by_cols > 0) {
      if (num_loss_by_cols_idx == 0) {
        if (num_loss_by_cols == origColumnNames.length)
          assignLossByCol(num_loss_by_cols, newColumnNames, origColumnNames);
        else
          error("_loss_by_col", "Number of override loss functions should be the same as the " +
                  "number of columns in the input frame; or otherwise an explicit _loss_by_col_idx should be " +
                  "provided.");
      }
      if (num_loss_by_cols_idx == num_loss_by_cols)
        assignLossByCol(num_loss_by_cols, newColumnNames, origColumnNames);

      // Otherwise we have already reported an error at the start of this method
    }

    // Check that all loss functions correspond to their actual type
    for (int i = 0; i < _ncolA; i++) {
      Vec vi = _train.vec(i);
      GlrmLoss lossi = _lossFunc[i];

      if (vi.isNumeric())   { // numeric columns
        if (!vi.isBinary()) { // non-binary numeric columns
          if (!lossi.isForNumeric())
            error("_loss_by_col", "Loss function "+lossi+" cannot be applied to numeric column "+i);
        } else {  // binary numeric columns
          if (!lossi.isForBinary() && !lossi.isForNumeric()) {
            error("_loss_by_col", "Loss function "+lossi+" cannot be applied to binary column "+i);
          }
        }
      } else if (vi.isCategorical()) {  // categorical columns
        if (vi.isBinary())  {  // categorical binary columns
          if (!lossi.isForBinary() && !lossi.isForCategorical())
            error("_loss_by_col", "Loss function "+lossi+" cannot be applied to binary column "+i);
          else if (lossi.isForBinary())
            _binaryColumnIndices.add(i);  // collect column indices storing binary columns with binary loss function.
        } else {  // categorical non-binary columns
          if (!lossi.isForCategorical())
            error("_loss_by_col","Loss function "+lossi+" cannot be applied to categorical column" +
                    " "+i);
        }
      }

      // For "Periodic" loss function supply the period. We currently have no support for different periods for
      // different columns.
      if (lossi == GlrmLoss.Periodic)
        lossi.setParameters(_parms._period);
    }
  }

  /*
  Need to assign column loss for each column.  However, due to constant columns being dropping, the
  loss function specified for a constant columns will no longer apply since we dropped that column.
  Need to take care of this case to avoid errors.
 */
  private void assignLossByCol(int num_loss_by_cols, ArrayList<String> newColumnNames, String[] origColumnNames) {
    for (int i = 0; i < num_loss_by_cols; i++) {
      int cidx = _parms._loss_by_col_idx==null?i:_parms._loss_by_col_idx[i];
      String colNames = origColumnNames[cidx];
      if (cidx < 0 || cidx >= origColumnNames.length)
        error("_loss_by_col_idx", "Column index " + cidx + " must be in [0," + _ncolA + ")");
      else
      if (newColumnNames.contains(colNames))
        _lossFunc[newColumnNames.indexOf(colNames)] = _parms._loss_by_col[i];
    }
  }

  // Squared Frobenius norm of a matrix (sum of squared entries)
  public static double frobenius2(double[][] x) {
    if (x == null) return 0;
    double frob = 0;
    for (double[] xs : x)
      for (double j : xs)
        frob += j*j;
    return frob;
  }

  // Closed-form solution for X and Y may exist if both loss and regularizers are quadratic.
  public final boolean hasClosedForm(long na_cnt) {
    if (na_cnt != 0) return false;
    for (GlrmLoss lossi : _lossFunc)
      if (lossi != Quadratic)
        return false;
    return (_parms._gamma_x == 0 || _parms._regularization_x == GlrmRegularizer.None ||
                                    _parms._regularization_x == GlrmRegularizer.Quadratic) &&
           (_parms._gamma_y == 0 || _parms._regularization_y == GlrmRegularizer.None ||
                                    _parms._regularization_y == GlrmRegularizer.Quadratic);
  }


  // Transform each column of a 2-D array, assuming categoricals sorted before numeric cols
  public static double[][] transform(double[][] centers, double[] normSub, double[] normMul, int ncats, int nnums) {
    int K = centers.length;
    int N = centers[0].length;
    assert ncats + nnums == N;
    double[][] value = new double[K][N];
    double[] means = normSub == null ? MemoryManager.malloc8d(nnums) : normSub;
    double[] mults = normMul == null ? MemoryManager.malloc8d(nnums) : normMul;
    if (normMul == null) Arrays.fill(mults, 1.0);

    for (int clu = 0; clu < K; clu++) {
      System.arraycopy(centers[clu], 0, value[clu], 0, ncats);
      for (int col = 0; col < nnums; col++)
        value[clu][ncats+col] = (centers[clu][ncats+col] - means[col]) * mults[col];
    }
    return value;
  }

  // More efficient implementation assuming sdata cols aligned with adaptedFrame
  public static double[][] expandCats(double[][] sdata, DataInfo dinfo) {
    if (sdata == null || dinfo._cats == 0) return sdata;
    assert sdata[0].length == dinfo._adaptedFrame.numCols();

    // Column count for expanded matrix
    int catsexp = dinfo._catOffsets[dinfo._catOffsets.length-1];
    double[][] cexp = new double[sdata.length][catsexp + dinfo._nums];

    for (int i = 0; i < sdata.length; i++)
      LinearAlgebraUtils.expandRow(sdata[i], dinfo, cexp[i], false);
    return cexp;
  }

  class GLRMDriver extends Driver {
    // Initialize Y and X matrices
    // tinfo = original training data A, dfrm = [A,X,W] where W is working copy of X (initialized here)
    private double[][] initialXY(DataInfo tinfo, Frame dfrm, GLRMModel model, long na_cnt) {
      double[][] centers, centers_exp = null;

      if (_parms._init == GlrmInitialization.User) { // Set X and Y to user-specified points if available,
                                                     // Gaussian matrix if not
        Frame userYFrame = _parms._user_y == null? null : _parms._user_y.get();
        if (userYFrame != null) {   // Set Y = user-specified initial points
          Vec[] yVecs = userYFrame.vecs();

          if (_parms._expand_user_y) {   // Categorical cols must be one-hot expanded
            // Get the centers and put into array
            centers = new double[_parms._k][_ncolA];
            for (int c = 0; c < _ncolA; c++) {
              for (int r = 0; r < _parms._k; r++)
                centers[r][c] = yVecs[c].at(r);
            }

            // Permute cluster columns to align with dinfo and expand out categoricals
            centers = ArrayUtils.permuteCols(centers, tinfo._permutation);
            centers_exp = expandCats(centers, tinfo);
          } else {    // User Y already has categoricals expanded
            centers_exp = new double[_parms._k][_ncolY];
            for (int c = 0; c < _ncolY; c++) {
              for (int r = 0; r < _parms._k; r++)
                centers_exp[r][c] = yVecs[c].at(r);
            }
          }
        } else
          centers_exp = ArrayUtils.gaussianArray(_parms._k, _ncolY, _parms._seed);

        if (_parms._user_x != null) {   // Set X = user-specified initial points
          Frame tmp = new Frame(dfrm);
          tmp.add(_parms._user_x.get());   // [A,X,W,U] where U = user-specified X

          // Set X and W to the same values as user-specified initial X
          new MRTask() {
            @Override public void map(Chunk[] cs) {
              for (int row = 0; row < cs[0]._len; row++) {
                for (int i = _ncolA; i < _ncolA+_ncolX; i++) {
                  double x = cs[2*_ncolX + i].atd(row);
                  cs[i].set(row, x);
                  cs[_ncolX + i].set(row, x);
                }
              }
            }
          }.doAll(tmp);
        } else {
          InitialXProj xtsk = new InitialXProj(_parms, _ncolA, _ncolX);
          xtsk.doAll(dfrm);
        }
        return centers_exp;   // Don't project or change Y in any way if user-specified, just return it

      } else if (_parms._init == GlrmInitialization.Random) {  // Generate X and Y from standard normal distribution
        centers_exp = ArrayUtils.gaussianArray(_parms._k, _ncolY, _parms._seed);
        InitialXProj xtsk = new InitialXProj(_parms, _ncolA, _ncolX);
        xtsk.doAll(dfrm);

      } else if (_parms._init == GlrmInitialization.SVD) {  // Run SVD on A'A/n (Gram), set Y = right singular vectors
        SVDParameters parms = new SVDParameters();
        parms._train = _parms._train;
        parms._ignored_columns = _parms._ignored_columns;
        parms._ignore_const_cols = _parms._ignore_const_cols;
        parms._score_each_iteration = _parms._score_each_iteration;
        parms._use_all_factor_levels = true;   // Since GLRM requires Y matrix to have fully expanded ncols
        parms._nv = _parms._k;
        parms._transform = _parms._transform;
        parms._svd_method = _parms._svd_method;
        parms._max_iterations = parms._svd_method == SVDParameters.Method.Randomized ?
                _parms._k : _parms._max_iterations;
        parms._seed = _parms._seed;
        parms._keep_u = true;
        parms._impute_missing = true;
        parms._save_v_frame = false;

        SVDModel svd = ModelCacheManager.get(parms);

        if (svd == null) {
          SVD svdP = new SVD(parms, _job, true, model);
          svdP.setWideDataset(_wideDataset);  // force to treat dataset as wide even though it is not.

          // Build an SVD model
          svd = svdP.trainModelNested(_rebalancedTrain);
        }
        model._output._init_key = svd._key;

        // Ensure SVD centers align with adapted training frame cols
        assert svd._output._permutation.length == tinfo._permutation.length;
        for (int i = 0; i < tinfo._permutation.length; i++)
          assert svd._output._permutation[i] == tinfo._permutation[i];
        centers_exp = transpose(svd._output._v);

        // Set X and Y appropriately given SVD of A = UDV'
        // a) Set Y = D^(1/2)V'S where S = diag(\sigma)
        _parms._k = svd._parms._nv;   // parameter k may have been reduced due to rank deficient dataset
        double[] dsqrt = new double[_parms._k];
        for (int i = 0; i < _parms._k; i++) {
          dsqrt[i] = Math.sqrt(svd._output._d[i]);
          ArrayUtils.mult(centers_exp[i], dsqrt[i]);  // This gives one row of D^(1/2)V'
        }
        // b) Set X = UD^(1/2) = AVD^(-1/2)
        Frame uFrm = DKV.get(svd._output._u_key).get();
        assert uFrm.numCols() == _parms._k;
        assert uFrm.isCompatible(dfrm);
        Frame fullFrm = (new Frame(uFrm)).add(dfrm);  // Jam matrices together into frame [U,A,X,W]
        InitialXSVD xtsk = new InitialXSVD(dsqrt, _parms._k, _ncolA, _ncolX);
        xtsk.doAll(fullFrm);
      } else if (_parms._init == GlrmInitialization.PlusPlus) {
        // Run k-means++ and set Y = resulting cluster centers, X = indicator matrix of assignments
        KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
        parms._train = _parms._train;
        parms._ignored_columns = _parms._ignored_columns;
        parms._ignore_const_cols = _parms._ignore_const_cols;
        parms._score_each_iteration = _parms._score_each_iteration;
        parms._init = KMeans.Initialization.PlusPlus;
        parms._k = _parms._k;
        parms._max_iterations = _parms._max_iterations;
        parms._standardize = true;
        parms._seed = _parms._seed;
        parms._pred_indicator = true;

        KMeansModel km = ModelCacheManager.get(parms);
        if (km == null) km = new KMeans(parms, _job).trainModelNested(_rebalancedTrain);
        model._output._init_key = km._key;

        // Score only if clusters well-defined and closed-form solution does not exist
        double frob = frobenius2(km._output._centers_raw);
        if (frob != 0 && !Double.isNaN(frob) && !hasClosedForm(na_cnt)) {
          // Frame pred = km.score(_parms.train());
          Log.info("Initializing X to matrix of weights inversely correlated with cluster distances");
          InitialXKMeans xtsk = new InitialXKMeans(_parms, km, _ncolA, _ncolX);
          xtsk.doAll(dfrm);
        }

        // Permute cluster columns to align with dinfo, normalize nums, and expand out cats to indicator cols
        centers = ArrayUtils.permuteCols(km._output._centers_raw, tinfo.mapNames(km._output._names));
        centers = transform(centers, tinfo._normSub, tinfo._normMul, tinfo._cats, tinfo._nums);
        centers_exp = expandCats(centers, tinfo); // expand categorical columns to N binary columns
      } else
        error("_init", "Initialization method " + _parms._init + " is undefined");

      // If all centers are zero or any are NaN, initialize to standard Gaussian random matrix
      assert centers_exp != null && centers_exp.length == _parms._k && centers_exp[0].length == _ncolY :
              "Y must have " + _parms._k + " rows and " + _ncolY + " columns";
      double frob = frobenius2(centers_exp);
      if (frob == 0 || Double.isNaN(frob)) {
        warn("_init", "Initialization failed. Setting initial Y to standard normal random" +
                " matrix instead");
        centers_exp = ArrayUtils.gaussianArray(_parms._k, _ncolY);
      }

      // Project rows of Y into appropriate subspace for regularizer
      Random rand = RandomUtils.getRNG(_parms._seed);
      for (int i = 0; i < _parms._k; i++)
        centers_exp[i] = _parms._regularization_y.project(centers_exp[i], rand);
      return centers_exp;
    }

    // In case of quadratic loss and regularization, initialize closed form X = AY'(YY' + \gamma)^(-1)
    private void initialXClosedForm(DataInfo dinfo, Archetypes yt_arch, double[] normSub, double[] normMul) {
      Log.info("Initializing X = AY'(YY' + gamma I)^(-1) where A = training data");
      double[][] ygram = ArrayUtils.formGram(yt_arch._archetypes);
      if (_parms._gamma_y > 0) {
        for (int i = 0; i < ygram.length; i++)
          ygram[i][i] += _parms._gamma_y;
      }
      CholeskyDecomposition yychol = regularizedCholesky(ygram, 10, false);
      if(!yychol.isSPD())
        Log.warn("Initialization failed: (YY' + gamma I) is non-SPD. Setting initial X to standard normal" +
                " random matrix. Results will be numerically unstable");
      else {
        CholMulTask cmtsk = new CholMulTask(yychol, yt_arch, _ncolA, _ncolX, dinfo._cats, normSub, normMul);
        cmtsk.doAll(dinfo._adaptedFrame);
      }
    }

    // Stopping criteria
    private boolean isDone(GLRMModel model, int steps_in_row, double step) {
      if (stop_requested()) return true;  // Stopped/cancelled

      // Stopped for running out of iterations
      if (model._output._iterations >= _parms._max_iterations) return true;
      if (model._output._updates >= _parms._max_updates) return true;

      // Stopped for falling below minimum step size
      if (step <= _parms._min_step_size) return true;

      // Stopped when enough steps and average decrease in objective per iteration < TOLERANCE
      return model._output._iterations > 10 && steps_in_row > 3 && Math.abs(model._output._avg_change_obj) < TOLERANCE;
    }

    // Regularized Cholesky decomposition using H2O implementation
    public Cholesky regularizedCholesky(Gram gram, int max_attempts) {
      int attempts = 0;
      double addedL2 = 0;   // TODO: Should I report this to the user?
      Cholesky chol = gram.cholesky(null);

      while (!chol.isSPD() && attempts < max_attempts) {
        if (addedL2 == 0) addedL2 = 1e-5;
        else addedL2 *= 10;
        ++attempts;
        gram.addDiag(addedL2); // try to add L2 penalty to make the Gram SPD
        Log.info("Added L2 regularization = " + addedL2 + " to diagonal of Gram matrix");
        gram.cholesky(chol);
      }
      if (!chol.isSPD())
        throw new Gram.NonSPDMatrixException();
      return chol;
    }

    public Cholesky regularizedCholesky(Gram gram) { return regularizedCholesky(gram, 10); }

    // Regularized Cholesky decomposition using JAMA implementation
    public CholeskyDecomposition regularizedCholesky(double[][] gram, int max_attempts, boolean throw_exception) {
      int attempts = 0;
      double addedL2 = 0;
      Matrix gmat = new Matrix(gram);
      CholeskyDecomposition chol = new CholeskyDecomposition(gmat);

      while (!chol.isSPD() && attempts < max_attempts) {
        if (addedL2 == 0) addedL2 = 1e-5;
        else addedL2 *= 10;
        ++attempts;

        for (int i = 0; i < gram.length; i++) gmat.set(i,i,addedL2); // try to add L2 penalty to make the Gram SPD
        Log.info("Added L2 regularization = " + addedL2 + " to diagonal of Gram matrix");
        chol = new CholeskyDecomposition(gmat);
      }
      if (!chol.isSPD() && throw_exception)
        throw new Gram.NonSPDMatrixException();
      return chol;
    }

    // Recover singular values and eigenvectors of XY
    // However, they are only saved if the user has specified _parms._recover_svd to be true.
    // Otherwise, we are doing recoverSVD to just recover enough information to calculate information
    // for variance metrics specified in PUBDEV-3501.
    public void recoverSVD(GLRMModel model, DataInfo xinfo, DataInfo dinfo) {
      // NOTE: Gram computes X'X/n where n = nrow(A) = number of rows in training set
      GramTask xgram = new GramTask(_job._key, xinfo).doAll(xinfo._adaptedFrame);
      GramTask dgram = new GramTask(_job._key, dinfo).doAll(dinfo._adaptedFrame.subframe(0, _ncolA));
      Cholesky xxchol = regularizedCholesky(xgram._gram);
      long nobs = xgram._nobs;

      // R from QR decomposition of X = QR is upper triangular factor of Cholesky of X'X
      // Gram = X'X/n = LL' -> X'X = (L*sqrt(n))(L'*sqrt(n))
      Matrix x_r = new Matrix(xxchol.getL()).transpose();
      x_r = x_r.times(Math.sqrt(nobs));

      Matrix yt = new Matrix(model._output._archetypes_raw.getY(true));
      QRDecomposition yt_qr = new QRDecomposition(yt);
      Matrix yt_r = yt_qr.getR();   // S from QR decomposition of Y' = ZS
      Matrix rrmul = x_r.times(yt_r.transpose());
      SingularValueDecomposition rrsvd = new SingularValueDecomposition(rrmul);   // RS' = U \Sigma V'
      double[] sval = rrsvd.getSingularValues();  // get singular values as double array

      double dfcorr = (nobs > 1)?nobs/(nobs-1.0):1.0;  // find number of observations
      double oneOverNobsm1 = (nobs>1)?1.0/Math.sqrt(nobs-1):1.0;

      model._output._std_deviation = Arrays.copyOf(sval, sval.length);
      ArrayUtils.mult(model._output._std_deviation, oneOverNobsm1);
      model._output._total_variance = dfcorr * dgram._gram.diagSum();

      double maxSumVal = ArrayUtils.l2norm2(model._output._std_deviation);
      if (maxSumVal > model._output._total_variance) {  // scale output std to avoid cum std > 1
          double catScale = Math.sqrt(model._output._total_variance/maxSumVal);
          ArrayUtils.mult(model._output._std_deviation, catScale);
      }

      double[] vars = new double[model._output._std_deviation.length];
      double[] prop_var = new double[vars.length];
      double[] cum_var = new double[vars.length];
      generateIPC(model._output._std_deviation, model._output._total_variance, vars, prop_var, cum_var);

      String[] colTypes = new String[_parms._k];
      String[] colFormats = new String[_parms._k];
      String[] colHeaders = new String[_parms._k];
      String[]  pcHeaders = new String[_parms._k];  // header for variance metrics, set to equal PCA
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormats, "%5f");
      for (int i = 0; i < colHeaders.length; i++) {
        colHeaders[i] = "Vec" + String.valueOf(i + 1);
        pcHeaders[i] = "pc" + String.valueOf(i + 1);
      }

      model._output._importance = new TwoDimTable("Importance of components", null,
              new String[]{"Standard deviation", "Proportion of Variance", "Cumulative Proportion"},
              pcHeaders, colTypes, colFormats, "", new String[3][],
              new double[][]{model._output._std_deviation,
              prop_var, cum_var});

      if (_parms._recover_svd) {  // only save the eigenvectors, singular values if _recover_svd=true
        // Eigenvectors are V'Z' = (ZV)'
        Matrix eigvec = yt_qr.getQ().times(rrsvd.getV());
        // Make TwoDimTable objects for prettier output
        model._output._eigenvectors_raw = eigvec.getArray();

        // Singular values ordered in weakly descending order by algorithm
        model._output._singular_vals = rrsvd.getSingularValues();
        assert model._output._names_expanded.length == model._output._eigenvectors_raw.length;
        model._output._eigenvectors = new TwoDimTable("Eigenvectors", null,
                model._output._names_expanded, colHeaders, colTypes, colFormats, "",
                new String[model._output._eigenvectors_raw.length][], model._output._eigenvectors_raw);
      }
    }

    private transient Frame _rebalancedTrain;
    @SuppressWarnings("ConstantConditions")  // Method too complex for IntelliJ
    @Override
    public void computeImpl() {
      GLRMModel model = null;
      DataInfo dinfo = null, xinfo = null, tinfo = null, tempinfo = null;
      Frame fr = null;
      Frame frTA = null; // frame to store T(A)
      Frame xwF = null; // frame to store X, W matrices for wide datasets
      Frame xwF2 = null;
      Frame xVecs = null; // store X frame vectors
      int colCount = _ncolA;
						ObjCalc objtsk = null;
						ObjCalcW objtskw = null;
      Archetypes yt = null;
      Archetypes ytnew = null;

      try {
        init(true);   // Initialize + Validate parameters
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new GLRMModel(dest(), _parms, new GLRMModel.GLRMOutput(GLRM.this));
        model.delete_and_lock(_job);

        _rebalancedTrain = new Frame(_train);
        // Save adapted frame info for scoring later
        tinfo = new DataInfo(_train, _valid, 0, true, _parms._transform,
                DataInfo.TransformType.NONE, false, false, false,
                /* weights */ false, /* offset */ false, /* fold */ false);
        DKV.put(tinfo._key, tinfo);

        // used later to recover SVD info
        tempinfo = new DataInfo(_train, null, 0, true, _parms._transform,
                DataInfo.TransformType.NONE, false, false, false,
                /* weights */ false, /* offset */ false, /* fold */ false);

        correctForBinaryLoss(tinfo);

        model._output._permutation = tinfo._permutation;
        model._output._nnums = tinfo._nums;
        model._output._ncats = tinfo._cats;
        model._output._catOffsets = tinfo._catOffsets;
        int[] numLevels = tinfo._adaptedFrame.cardinality();
        // may have more numerical columns now since we may change binary columns with binary loss to numerical columns
        for (int colIndex = tinfo._cats; colIndex < _train.numCols(); colIndex++) {
          if (numLevels[colIndex] > -1)
            numLevels[colIndex] = -1;
          else
            break;  // hit the numericcal columns already.  Nothing more need to be done here.
        }
        // need to prevent binary data column being expanded into two when the loss function is logistic here
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());
        model._output._catOffsets = tinfo._catOffsets;
        model._output._names_expanded = tinfo.coefNames();

        // Save training frame adaptation information for use in scoring later
        model._output._normSub = tinfo._normSub == null ? new double[tinfo._nums] : tinfo._normSub;
        if (tinfo._normMul == null) {
          model._output._normMul = new double[tinfo._nums];
          Arrays.fill(model._output._normMul, 1.0);
        } else
          model._output._normMul = tinfo._normMul;

        // Save loss function for each column in adapted frame order
        assert _lossFunc != null && _lossFunc.length == _train.numCols();
        model._output._lossFunc = new GlrmLoss[_lossFunc.length];
        for (int i = 0; i < _lossFunc.length; i++)
          model._output._lossFunc[i] = _lossFunc[tinfo._permutation[i]];

        long nobs = _train.numRows() * _train.numCols();
        long na_cnt = 0;
        for (int i = 0; i < _train.numCols(); i++)
          na_cnt += _train.vec(i).naCnt();
        model._output._nobs = nobs - na_cnt;   // TODO: Should we count NAs?

        // 0) Initialize Y and X matrices
        // Jam A and X into a single frame for distributed computation
        // [A,X,W] A is read-only training data, X is matrix from prior iteration, W is working copy of X this iteration
        // for _wideDataset=true, it stores [A, YeXOld, W (YeXNew)]
        fr = new Frame(_train);
        Vec anyvec = fr.anyVec();
        assert anyvec != null;
        for (int i = 0; i < _ncolX; i++) fr.add("xcol_" + i, anyvec.makeZero());
        for (int i = 0; i < _ncolX; i++) fr.add("wcol_" + i, anyvec.makeZero());

        dinfo = new DataInfo(/* train */ fr, /* validation */ null, /* nResponses */ 0,
                /* useAllFactorLevels */ true, /* pred. transform */ _parms._transform,
                /* resp. transform */ DataInfo.TransformType.NONE, /* skipMissing */ false,
                /* imputeMissing */ false, /* missingBucket */ false,
                /* weights */ false, /* offset */ false, /* fold */ false);
        DKV.put(dinfo._key, dinfo); // for wideDataset, contains A, Yex_old, Yex_new.  For normal, contains A, XO, W
        fr = dinfo._adaptedFrame;
        int weightId = dinfo._weights ? dinfo.weightChunkId() : -1;

        // Use closed form solution for X if quadratic loss and regularization
        _job.update(1, "Initializing X and Y matrices");   // One unit of work

        double[/*k*/][/*features*/] yinit = initialXY(tinfo, dinfo._adaptedFrame, model, na_cnt); // on transformed A

        // Store Y' for more efficient matrix ops (rows = features, cols = k rank)
        yt = new Archetypes(transpose(yinit), true, tinfo._catOffsets, numLevels);
        ytnew = yt;

        double yreg = _parms._regularization_y.regularize(yt._archetypes);
        // Set X to closed-form solution of ALS equation if possible for better accuracy.  No need to set to
        // work with wideDataset.  Dealing with small matrices anymore.  Optimize to use H2O Chol maybe.
        if (!(_parms._init == GlrmInitialization.User && _parms._user_x != null) && hasClosedForm(na_cnt))
          initialXClosedForm(dinfo, yt, model._output._normSub, model._output._normMul);

        if (_wideDataset) { // 1. create fr as transpose(A). 2. make T(X) as double[][] array 3. build frame for x
          colCount = (int) _train.numRows();
          frTA = generateFrameOfZeros(_ncolA, colCount);
          xwF = new water.util.ArrayUtils().frame(transpose(yinit));  // YeX stored as frame, duplicated
          xwF2 = new water.util.ArrayUtils().frame(transpose(yinit));
          xwF.add(xwF2);

          new DMatrix.TransposeTsk(frTA).doAll(dinfo._adaptedFrame.subframe(0, _ncolA));  // store T(A)

          yinit = new double[_parms._k][colCount];    // store the X matrix from adaptedFrame as 2D double array
          for (int index = colCount; index < colCount+_ncolX; index++) {
            int trueIndex = index-colCount;
            yinit[trueIndex] = new FrameUtils.Vec2ArryTsk(colCount).doAll(dinfo._adaptedFrame.vec(trueIndex+_ncolA)).res;
          }

          // set weights to _weights in archetype class instead of as part of frame
          double[] tempWeights = new double[(int)_train.numRows()];
          if (weightId < 0) { //
            Arrays.fill(tempWeights,1);
          } else {
            tempWeights = new FrameUtils.Vec2ArryTsk(weightId).doAll(dinfo._adaptedFrame.vec(weightId)).res;
          }

          yt = new Archetypes(transpose(yinit), true, tinfo._catOffsets, numLevels, tempWeights);
          ytnew = yt;
          setTrain(rebalance(xwF, false, _result + ".temporary.xwF"));
        }

        // Compute initial objective function
        _job.update(1, "Computing initial objective function");   // One unit of work
        // Assume regularization on initial X is finite, else objective can be NaN if \gamma_x = 0
        boolean regX = _parms._regularization_x != GlrmRegularizer.None && _parms._gamma_x != 0;

        if (_wideDataset) {
										objtskw = new ObjCalcW(_parms, yt, colCount, _ncolX, tinfo._cats, model._output._normSub,
																		model._output._normMul, model._output._lossFunc, regX, xwF, 0);
										objtskw.doAll(frTA);
          model._output._objective = objtskw._loss + _parms._gamma_x * objtskw._xold_reg + _parms._gamma_y * yreg;
								} else {
										objtsk = new ObjCalc(_parms, yt, _ncolA, _ncolX, tinfo._cats, model._output._normSub,
																		model._output._normMul, model._output._lossFunc, weightId, regX);
										objtsk.doAll(fr);
          model._output._objective = objtsk._loss + _parms._gamma_x * objtsk._xold_reg + _parms._gamma_y * yreg;
								}

        model._output._archetypes_raw = yt; // at some point, need to switch from yt to y after all updates.
        model._output._iterations = 0;
        model._output._updates = 0;
        model._output._avg_change_obj = 2 * TOLERANCE;    // Allow at least 1 iteration
        model._output._step_size = 0;       // set to zero
        model.update(_job);  // Update model in K/V store

        double step = _parms._init_step_size;   // Initial step size
        int steps_in_row = 0;                   // Keep track of number of steps taken that decrease objective

        while (!isDone(model, steps_in_row, step)) {
          // One unit of work
          _job.update(1, "Iteration " + String.valueOf(model._output._iterations+1) +
                  " of alternating minimization");
          UpdateX xtsk = null;
          UpdateYeX yextsk = null;
          double alpha = step/_ncolA;

          // TODO: Should step be divided by number of original or expanded (with 0/1 categorical) cols?
          // 1) Update X matrix given fixed Y.  Wide dataset, update Y.
          if (_wideDataset) {
            yextsk = new UpdateYeX(_parms, yt, alpha, _ncolA, _ncolX, tinfo._cats, model._output._normSub,
            model._output._normMul, model._output._lossFunc, xwF);
            double[][] yttmp = yextsk.doAll(frTA)._ytnew;
            ytnew = new Archetypes(yttmp, true, tinfo._catOffsets, numLevels, ytnew._weights);
          } else {
            // find out how much time it takes to update x, for wide dataset, it is updating Y
            xtsk = new UpdateX(_parms, yt, alpha, _ncolA, _ncolX, tinfo._cats,
                    model._output._normSub, model._output._normMul, model._output._lossFunc, weightId);
            xtsk.doAll(dinfo._adaptedFrame);
          }
          model._output._updates++;

          // 2) Update Y matrix given fixed X, for wide dataset, update X then.
          if (model._output._updates < _parms._max_updates) {
            if (_wideDataset) {
              UpdateXeY xeytsk = new UpdateXeY(_parms, ytnew, alpha, colCount, _ncolX, tinfo._cats,
                      model._output._normSub, model._output._normMul, model._output._lossFunc, frTA);
              xeytsk.doAll(xwF);
              yreg = xeytsk._yreg;
            } else {
              // If max_updates is odd, we will terminate after the X update, for wide dataset, it updates Y
              UpdateY ytsk = new UpdateY(_parms, yt, alpha, _ncolA, _ncolX, tinfo._cats,
                      model._output._normSub, model._output._normMul, model._output._lossFunc, weightId);
              double[][] yttmp = ytsk.doAll(dinfo._adaptedFrame)._ytnew;
              ytnew = new Archetypes(yttmp, true, tinfo._catOffsets, numLevels);
              yreg = ytsk._yreg;
            }
            model._output._updates++;
          }
          double obj_new = 0;
          if (_wideDataset) {
              objtskw = new ObjCalcW(_parms, ytnew, colCount, _ncolX, tinfo._cats, model._output._normSub,
                      model._output._normMul, model._output._lossFunc, regX, xwF, _parms._k);
              objtskw.doAll(frTA);
              obj_new = objtskw._loss + _parms._gamma_x * yextsk._xreg + _parms._gamma_y * yreg;
          } else {
            // 3) Compute average change in objective function
            objtsk = new ObjCalc(_parms, ytnew, _ncolA, _ncolX, tinfo._cats, model._output._normSub,
                    model._output._normMul, model._output._lossFunc, weightId);
            objtsk.doAll(dinfo._adaptedFrame);
            obj_new = objtsk._loss + _parms._gamma_x * xtsk._xreg + _parms._gamma_y * yreg;
          }
          model._output._avg_change_obj = (model._output._objective - obj_new) / nobs;
          model._output._iterations++;

          // step = 1.0 / model._output._iterations;   // Step size \alpha_k = 1/iters
          if (model._output._avg_change_obj > 0) {   // Objective decreased this iteration
            yt = ytnew;
            model._output._archetypes_raw = ytnew;  // Need full archetypes object for scoring
            model._output._objective = obj_new;
            step *= 1.05;
            steps_in_row = Math.max(1, steps_in_row+1);

            if (_wideDataset) { // update X matrix right now to avoid potential multi-thread collision.
              new updateXVecs(0, _ncolX).doAll(xwF);
            } else {
              new updateXVecs(_ncolA, _ncolX).doAll(dinfo._adaptedFrame);
            }
          } else {    // If objective increased, re-run with smaller step size
            step /= Math.max(1.5, -steps_in_row);
            steps_in_row = Math.min(0, steps_in_row-1);
            if (_parms._verbose) {
              Log.info("Iteration " + model._output._iterations + ": Objective increased to " + obj_new
                      + "; reducing step size to " + step);
              _job.update(0,"Iteration " + model._output._iterations + ": Objective increased to " +
                      obj_new + "; reducing step size to " + step);
            }
          }

          // Add to scoring history
          model._output._training_time_ms.add(System.currentTimeMillis());
          model._output._history_step_size.add(step);
          model._output._history_objective.add(model._output._objective);
          model.update(_job); // Update model in K/V store
        }

        model._output._representation_name = StringUtils.isNullOrEmpty(_parms._representation_name) ?
                "GLRMLoading_" + Key.rand() : _parms._representation_name;
        model._output._representation_key = Key.make(model._output._representation_name);
        model._output._x_factor_key = model._output._representation_key;  // point to this key for default

        String[] xnames = new String[_ncolX];
        for (int i=0; i<_ncolX; i++) {
          xnames[i] = "Arch"+String.valueOf(i+1);
        }

        Frame x = null;
        if (_wideDataset) {   // extract X into archetype, extract Y into X frames
          x = new water.util.ArrayUtils().frame(model._output._representation_key, xnames, yt._transposed?yt._archetypes:transpose(yt._archetypes));
          yt._archetypes = new FrameUtils.Vecs2ArryTsk(_ncolY, _parms._k).doAll(xwF).res;
          model._output._archetypes_raw = new Archetypes(yt._archetypes, yt._transposed, tinfo._catOffsets,
                  numLevels);
        } else {  // for normal dataset
          // 4) Save solution to model output
          // Save X frame for user reference later
          Vec[] xvecs = new Vec[_ncolX];
          for (int i = 0; i < _ncolX; i++) {
            xvecs[i] = fr.vec(idx_xold(i, _ncolA));
          }
          x = new Frame(model._output._representation_key, xnames, xvecs);
        }

        xinfo = new DataInfo(x, null, 0, true, DataInfo.TransformType.NONE,
                DataInfo.TransformType.NONE, false, false, false,
                /* weights */ false, /* offset */ false, /* fold */ false);
        DKV.put(x);
        DKV.put(xinfo);

        // add last step_size used
        model._output._step_size = step;

        // Add to scoring history
        model._output._history_step_size.add(step);
        // Transpose Y' to get original Y
        model._output._archetypes = yt.buildTable(model._output._names_expanded, false);
        recoverSVD(model, xinfo, tempinfo);     // getting variance information here

        // Impute and compute error metrics on training/validation frame
        model._output._training_metrics = model.scoreMetricsOnly(_parms.train());
        model._output._validation_metrics = model.scoreMetricsOnly(_parms.valid());
        model._output._model_summary = createModelSummaryTable(model._output);
        model._output._scoring_history = createScoringHistoryTable(model._output);  //no need to call this per iteration
       //
        model.update(_job);
      } finally {
        List<Key<Vec>> keep = new ArrayList<>();
        if (model._output._iterations ==0) {
          warn("_max_runtime_secs", "model may not be properly built due to timeout.  Set " +
                  "max_runtime_secs to 0 or increase its value.");
        }
        if (model != null) {
          Frame loadingFrm = DKV.getGet(model._output._representation_key);
          if (loadingFrm != null) for (Vec vec: loadingFrm.vecs()) keep.add(vec._key);
          model.unlock(_job);
        }
        if (tinfo != null) tinfo.remove();
        if (dinfo != null) dinfo.remove();
        if (xinfo != null) xinfo.remove();
        if (tempinfo != null) tempinfo.remove();
        // copy what is in  XeY into archetypes
        if (xwF != null) {
          xwF.delete();
        }
        if (xwF2 != null)
          xwF2.delete();
        // if (x != null && !_parms._keep_loading) x.delete();
        // Clean up unused copy of X matrix
        if (xVecs!=null) xVecs.remove();
        if (frTA != null) frTA.delete();
        if ((fr != null) && (!_wideDataset)) {
          for (int i = 0; i < _ncolX; i++) fr.vec(idx_xnew(i, _ncolA, _ncolX)).remove();
        }
        Scope.untrack(keep);
      }
    }

    private Frame generateFrameOfZeros(int rowCount, int colCount) {
      Vec tempVec = Vec.makeZero(rowCount);
      return(new Frame(tempVec.makeZeros(colCount)));  // return a frame of zeros with size rowCount by colCount
    }

    /*
    This funciton will
    1. change categorical binary columns using binary loss function into numerical columns.  This involves
    performing the following variables:
    _nums, _cats, _catOffsets, _catMissing, _catNAFill, _permutation, _normMul, _normSub, _normMeans, _numOffsets
      If no logistic loss is specified, no action will be performed.
    2. for numeric columns using logistic loss, it will prevent it from being transformed to say zero mean
    and unit variance columns.
     */
    private void correctForBinaryLoss(DataInfo tinfo) {

      // set mean = 0 and mul = 1.0 for binary numeric columns using binary loss functions
      for (int index = 0; index < tinfo._nums; index++) {
        if (_lossFunc[tinfo._permutation[index+tinfo._cats]].isForBinary()) { // binary loss used on numeric columns
          if (tinfo._normMul != null)
            tinfo._normMul[index] = 1;

          if (tinfo._normSub != null)
            tinfo._normSub[index] = 0;
        }
      }
      // change binary categorical columns using binary loss functions to binary numerical columns
      if (!(_binaryColumnIndices == null) && (_binaryColumnIndices.size()>0)) {
        // change the number of categorical and numerical column counts.
        int binaryLossCols = _binaryColumnIndices.size(); // number of columns to change to numerics
        int numCatColumns = tinfo._cats;  // store original categorical column number
        int numNumColumns = tinfo._nums;  // store original numerical column number
        tinfo._cats -= binaryLossCols;  // decrease the number of categorical columns
        tinfo._nums += binaryLossCols;  // increase the number of numerical columns

        int[] catOffsetsTemp = new int[tinfo._cats+1];   // offset column indices for 1-hot expanded values (includes enum-enum interaction)
        boolean[] catMissingTemp = new boolean[tinfo._cats];  // bucket for missing levels
        int[] catNAFillTemp = new int[tinfo._cats];    // majority class of each categorical col (or last bucket if _catMissing[i] is true)
        int[] permutationTemp = new int[tinfo._permutation.length]; // permutation matrix mapping input col indices to adaptedFrame
        int[] numOffsetsTemp = new int[tinfo._nums];
        int[] cardinalities = _train.cardinality();
        int[] currentCardinality = new int[tinfo._cats];
        double[] normMulTemp = new double[tinfo._nums];
        double[] normSubTemp = new double[tinfo._nums];
        double[] numMeansTemp = new double[tinfo._nums];
        int newColIndex = 0;

        for (int colIndex = 0; colIndex < numCatColumns; colIndex++) {  // go through all categoricals
          if (!(_binaryColumnIndices.contains(tinfo._permutation[colIndex]))) {
            permutationTemp[newColIndex] = tinfo._permutation[colIndex];
            catMissingTemp[newColIndex] = tinfo._catMissing[colIndex];
            catNAFillTemp[newColIndex] = tinfo.catNAFill(colIndex);
            currentCardinality[newColIndex] = cardinalities[colIndex];
            catOffsetsTemp[newColIndex+1] = catOffsetsTemp[newColIndex]+currentCardinality[newColIndex];

            newColIndex++;
          }
        }
        numOffsetsTemp[0] = catOffsetsTemp[newColIndex];
        for (int colIndex = 0; colIndex < binaryLossCols; colIndex++) { // set infos for new numerical binary columns
          permutationTemp[colIndex + newColIndex] = _binaryColumnIndices.get(colIndex);
          normMulTemp[colIndex] = 1.0;
          normSubTemp[colIndex] = 0.0;
          numMeansTemp[colIndex] = 0.0;
          if (colIndex > 0)
            numOffsetsTemp[colIndex] = numOffsetsTemp[colIndex-1]+1;
        }

        // copy over original numerical columns
        for (int colIndex = 0; colIndex < numNumColumns; colIndex++) {
          int newColumnIndex = colIndex + binaryLossCols;
          if (tinfo._normSub != null) {
            normMulTemp[newColumnIndex] = tinfo._normMul[colIndex];
          }
          if (tinfo._normSub != null) {
            normSubTemp[newColumnIndex] = tinfo._normSub[colIndex];
          }
          if (tinfo._numMeans != null) {
            numMeansTemp[newColumnIndex] = tinfo._numMeans[colIndex];
          }
          numOffsetsTemp[newColumnIndex] = numOffsetsTemp[newColumnIndex-1]+1;

          int numColIndex = newColumnIndex + tinfo._cats;
          permutationTemp[numColIndex] = tinfo._permutation[numColIndex];
        }

        // copy the changed arrays back to tinfo information
        tinfo._catOffsets = Arrays.copyOf(catOffsetsTemp, catOffsetsTemp.length);
        tinfo._catMissing = Arrays.copyOf(catMissingTemp, tinfo._cats);
        tinfo.setCatNAFill(Arrays.copyOf(catNAFillTemp, tinfo._cats));
        tinfo._permutation = Arrays.copyOf(permutationTemp, tinfo._permutation.length);
        tinfo._numOffsets = Arrays.copyOf(numOffsetsTemp, tinfo._nums);
        if (tinfo._normMul != null) {
          tinfo._normMul = Arrays.copyOf(normMulTemp, tinfo._nums);
        }
        if (tinfo._normSub != null) {
          tinfo._normSub = Arrays.copyOf(normSubTemp, tinfo._nums);
        }
        if (tinfo._numMeans != null) {
          tinfo._numMeans = Arrays.copyOf(numMeansTemp, tinfo._nums);
        }
        _ncolY = _ncolY-binaryLossCols;   // adjust for binary columns with binary loss changed to numerical columns
      }
    }

    private TwoDimTable createModelSummaryTable(GLRMModel.GLRMOutput output) {
      List<String> colHeaders = new ArrayList<>();
      List<String> colTypes = new ArrayList<>();
      List<String> colFormat = new ArrayList<>();

      // TODO: This causes overflow in R if too large
      // colHeaders.add("Number of Observed Entries"); colTypes.add("long"); colFormat.add("%d");
      colHeaders.add("Number of Iterations"); colTypes.add("long"); colFormat.add("%d");
      colHeaders.add("Final Step Size"); colTypes.add("double"); colFormat.add("%.5f");
      colHeaders.add("Final Objective Value"); colTypes.add("double"); colFormat.add("%.5f");

      TwoDimTable table = new TwoDimTable(
              "Model Summary", null,
              new String[1],
              colHeaders.toArray(new String[0]),
              colTypes.toArray(new String[0]),
              colFormat.toArray(new String[0]),
              "");
      int row = 0;
      int col = 0;
      // table.set(row, col++, output._nobs);
      table.set(row, col++, output._iterations);
      table.set(row, col++, output._history_step_size.get(output._history_step_size.size() - 1));
      table.set(row, col, output._objective);
      return table;
    }

    private TwoDimTable createScoringHistoryTable(GLRMModel.GLRMOutput output) {
      List<String> colHeaders = new ArrayList<>();
      List<String> colTypes = new ArrayList<>();
      List<String> colFormat = new ArrayList<>();
      colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
      colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
      colHeaders.add("Iterations"); colTypes.add("long"); colFormat.add("%d");
      colHeaders.add("Step Size"); colTypes.add("double"); colFormat.add("%.5f");
      colHeaders.add("Objective"); colTypes.add("double"); colFormat.add("%.5f");

      int rows = output._training_time_ms.size();
      TwoDimTable table = new TwoDimTable(
              "Scoring History", null,
              new String[rows],
              colHeaders.toArray(new String[0]),
              colTypes.toArray(new String[0]),
              colFormat.toArray(new String[0]),
              "");
      for (int row = 0; row<rows; row++) {
        int col = 0;
        assert(row < table.getRowDim());
        assert(col < table.getColDim());
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        table.set(row, col++, fmt.print(output._training_time_ms.get(row)));
        table.set(row, col++, PrettyPrint.msecs(output._training_time_ms.get(row) - _job.start_time(),
                true));
        table.set(row, col++, row);
        table.set(row, col++, output._history_step_size.get(row));
        table.set(row, col  , output._history_objective.get(row));
      }
      return table;
    }
  }

  @SuppressWarnings("ExternalizableWithoutPublicNoArgConstructor")
  protected static final class Archetypes extends Iced<Archetypes> {
    double[][] _archetypes;  // Y has nrows = k (lower dim), ncols = n (features)
    boolean _transposed;     // Is _archetypes = Y'? Used during model building for convenience.
    final int[] _catOffsets;
    final int[] _numLevels;  // numLevels[i] = -1 if column i is not categorical
    double[] _weights;         // store weights per row for wide datasets;

    Archetypes(double[][] y, boolean transposed, int[] catOffsets, int[] numLevels) {
      _archetypes = y;
      _transposed = transposed;
      _catOffsets = catOffsets;
      _numLevels = numLevels;   // TODO: Check sum(cardinality[cardinality > 0]) + nnums == nfeatures()
      _weights = null;
    }

    Archetypes(double[][] y, boolean transposed, int[] catOffsets, int[] numLevels, double[] weights) {
      _archetypes = y;
      _transposed = transposed;
      _catOffsets = catOffsets;
      _numLevels = numLevels;   // TODO: Check sum(cardinality[cardinality > 0]) + nnums == nfeatures()
      _weights = Arrays.copyOf(weights, weights.length);
    }

    public int rank() {
      return _transposed ? _archetypes[0].length : _archetypes.length;
    }

    public int nfeatures() {
      return _transposed ? _archetypes.length : _archetypes[0].length;
    }

    // If transpose = true, we want to return Y'
    public double[][] getY(boolean transpose) {
      return (transpose ^ _transposed) ? transpose(_archetypes) : _archetypes;
    }

    public TwoDimTable buildTable(String[] features, boolean transpose) {
      // Must pass in categorical column expanded feature names
      int rank = rank();
      int nfeat = nfeatures();
      assert features != null && features.length == nfeatures();

      double[][] yraw = getY(transpose);
      if (transpose) {  // rows = features (n), columns = archetypes (k)
        String[] colTypes = new String[rank];
        String[] colFormats = new String[rank];
        String[] colHeaders = new String[rank];

        Arrays.fill(colTypes, "double");
        Arrays.fill(colFormats, "%5f");
        for (int i = 0; i < colHeaders.length; i++) colHeaders[i] = "Arch" + String.valueOf(i + 1);
        return new TwoDimTable("Archetypes", null, features, colHeaders, colTypes,
                colFormats, "",
                new String[yraw.length][], yraw);
      } else {  // rows = archetypes (k), columns = features (n)
        String[] rowNames = new String[rank];
        String[] colTypes = new String[nfeat];
        String[] colFormats = new String[nfeat];

        Arrays.fill(colTypes, "double");
        Arrays.fill(colFormats, "%5f");
        for (int i = 0; i < rowNames.length; i++) rowNames[i] = "Arch" + String.valueOf(i + 1);
        return new TwoDimTable("Archetypes", null, rowNames, features, colTypes,
                colFormats, "",
                new String[yraw.length][], yraw);
      }
    }

    // For j = 0 to number of numeric columns - 1
    public int getNumCidx(int j) {
      return _catOffsets[_catOffsets.length-1]+j;
    }

    // For j = 0 to number of categorical columns - 1, and level = 0 to number of levels in categorical column - 1
    public int getCatCidx(int j, int level) {
      return GlrmMojoModel.getCatCidx(j, level, _numLevels, _catOffsets);
    }

    protected final double getNum(int j, int k) {
      int cidx = GlrmMojoModel.getNumCidx(j, _catOffsets);
      return _transposed ? _archetypes[cidx][k] : _archetypes[k][cidx];
    }

    // Inner product x * y_j where y_j is numeric column j of Y
    protected final double lmulNumCol(double[] x, int j) {
      return GlrmMojoModel.lmulNumCol(x, j, _transposed, _archetypes, _catOffsets);
    }

    protected final double getCat(int j, int level, int k) {
      int cidx = getCatCidx(j, level);
      return _transposed ? _archetypes[cidx][k] : _archetypes[k][cidx];
    }

    // Extract Y_j the k by d_j block of Y corresponding to categorical column j
    // Note: d_j = number of levels in categorical column j, not used often, don't bother to optimize further
    protected final double[][] getCatBlock(int j) {
      int catColJLevel = _numLevels[j];
      assert catColJLevel != 0 : "Number of levels in categorical column cannot be zero";
      double[][] block = new double[rank()][catColJLevel];

      if (_transposed) {
        for (int level = 0; level < catColJLevel; level++) {
          int cidx = getCatCidx(j,level);
          for (int k = 0; k < rank(); k++)
            block[k][level] = _archetypes[cidx][k];
        }
      } else {
        for (int level = 0; level < catColJLevel; level++) {
          int cidx = getCatCidx(j,level);
          for (int k = 0; k < rank(); k++)
            block[k][level] = _archetypes[k][cidx];
        }
      }
      return block;
    }
  }

  // In chunk, first _ncolA cols are A, next _ncolX cols are X
  protected static int idx_xold(int c, int ncolA) { return ncolA+c; }
  protected static int idx_xnew(int c, int ncolA, int ncolX) { return ncolA+ncolX+c; }

  // Initialize X to standard Gaussian random matrix projected into regularizer subspace
  private static class InitialXProj extends MRTask<InitialXProj> {
    GLRMParameters _parms;
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)

    InitialXProj(GLRMParameters parms, int ncolA, int ncolX) {
      _parms = parms;
      _ncolA = ncolA;
      _ncolX = ncolX;
    }

    @Override public void map(Chunk[] chks) {
      Random rand = RandomUtils.getRNG(_parms._seed); // may have to set back to 0 for compatibility

      for (int row = 0; row < chks[0]._len; row++) {
        rand.setSeed(_parms._seed + chks[0].start() + row);   // global row ID determines the seed
        double[] xrow = ArrayUtils.gaussianVector(_ncolX, rand);
        xrow = _parms._regularization_x.project(xrow, rand);
        for (int c = 0; c < xrow.length; c++) {
          chks[_ncolA+c].set(row, xrow[c]);
          chks[_ncolA+_ncolX+c].set(row, xrow[c]);
        }
      }
    }
  }

  // Initialize X = UD, where U is m x k and D is a diagonal k x k matrix
  private static class InitialXSVD extends MRTask<InitialXSVD> {
    final double[] _diag;   // Diagonal of D
    final int _ncolU;       // Number of cols in U (k)
    final int _offX;        // Column offset to X matrix
    final int _offW;        // Column offset to W matrix

    InitialXSVD(double[] diag, int ncolU, int ncolA, int ncolX) {
      assert diag != null && diag.length == ncolU;
      _diag = diag;
      _ncolU = ncolU;
      _offX = ncolU + ncolA;
      _offW = _offX + ncolX;
    }

    @Override public void map(Chunk[] chks) {
      for (int row = 0; row < chks[0]._len; row++) {
        for (int c = 0; c < _ncolU; c++) {
          double ud = chks[c].atd(row) * _diag[c];
          chks[_offX+c].set(row, ud);
          chks[_offW+c].set(row, ud);
        }
      }
    }
  }

  // Initialize X to matrix of indicator columns for cluster assignments, e.g. k = 4, cluster = 3 -> [0, 0, 1, 0]
  private static class InitialXKMeans extends MRTask<InitialXKMeans> {
    GLRMParameters _parms;
    KMeansModel _model;
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)

    InitialXKMeans(GLRMParameters parms, KMeansModel model, int ncolA, int ncolX) {
      _parms = parms;
      _model = model;
      _ncolA = ncolA;
      _ncolX = ncolX;
    }

    @Override public void map(Chunk[] chks) {
      double[] tmp = new double[_ncolA];
      Random rand = RandomUtils.getRNG(0);

      for (int row = 0; row < chks[0]._len; row++) {
        double[] p = _model.score_ratio(chks, row, tmp);
        rand.setSeed(_parms._seed + chks[0].start() + row); //global row ID determines the seed
        // TODO: Should we restrict indicator cols to regularizer subspace?
        p = _parms._regularization_x.project(p, rand);
        for (int c = 0; c < p.length; c++) {
          chks[_ncolA+c].set(row, p[c]);
          chks[_ncolA+_ncolX+c].set(row, p[c]);
        }
      }
    }
  }

  public static class updateXVecs extends MRTask<updateXVecs> {
    int _startCol;
    int _numCols;
    int _endCols;

    public updateXVecs(int startCol, int numCols) {
      assert startCol >= 0;
      assert numCols > 0;

      _startCol = startCol;
      _numCols = numCols;
      _endCols = startCol+numCols;
    }

    public void map(Chunk[] chks) {
      for (int colIndex = _startCol; colIndex < _endCols; colIndex++) {
        for (int rowIndex = 0; rowIndex < chks[0]._len; rowIndex++) {
          xFrameVec(chks, colIndex, 0).set(rowIndex, xFrameVec(chks, colIndex, _numCols).atd(rowIndex));
        }
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Update X step
  //--------------------------------------------------------------------------------------------------------------------

  private static class UpdateX extends MRTask<UpdateX> {
    // Input
    GLRMParameters _parms;
    GlrmLoss[] _lossFunc;
    final double _alpha;      // Step size divided by num cols in A
    final Archetypes _yt;     // _yt = Y' (transpose of Y)
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final int _ncats;         // Number of categorical cols in training frame
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    final int _weightId;

    // Output
    double _xreg;    // Regularization evaluated on new X

    UpdateX(GLRMParameters parms, Archetypes yt, double alpha, int ncolA, int ncolX, int ncats,
            double[] normSub, double[] normMul, GlrmLoss[] lossFunc, int weightId) {
      assert yt != null && yt.rank() == ncolX;
      _parms = parms;
      _yt = yt;
      _lossFunc = lossFunc;
      _alpha = alpha;
      _ncolA = ncolA;
      _ncolX = ncolX;

      // Info on A (cols 1 to ncolA of frame)
      assert ncats <= ncolA;
      _ncats = ncats;
      _weightId = weightId;
      _normSub = normSub;
      _normMul = normMul;
    }

    private Chunk chk_xold(Chunk[] chks, int c) {
      return chks[_ncolA + c];
    }
    private Chunk chk_xnew(Chunk[] chks, int c) {
      return chks[_ncolA + _ncolX + c];
    }

    @SuppressWarnings("ConstantConditions")  // The method is too complex for IntelliJ
    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;
      double[] a = new double[_ncolA];
      double[] tgrad = new double[_ncolX];  // new gradient calculation with reduced memory allocation
      double[] u = new double[_ncolX];
      double[] xnew = new double[_ncolX];
      Chunk chkweight = _weightId >= 0 ? cs[_weightId] : new C0DChunk(1, cs[0]._len);
      Random rand = RandomUtils.getRNG(0);
      _xreg = 0;
      double[] xy = null;
      double[] prod = null;
      if (_yt._numLevels[0] > 0) {
        xy = new double[_yt._numLevels[0]]; // maximum categorical level column is always the first one
        prod = new double[_yt._numLevels[0]];
      }

      for (int row = 0; row < cs[0]._len; row++) {
        rand.setSeed(_parms._seed + cs[0].start() + row); //global row ID determines the seed
        Arrays.fill(tgrad, 0.0);  // temporary gradient for comparison, need to stay

        // Additional user-specified weight on loss for this row
        double cweight = chkweight.atd(row);
        assert !Double.isNaN(cweight) : "User-specified weight cannot be NaN";

        // Compute gradient of objective at row
        // Categorical columns
        for (int j = 0; j < _ncats; j++) {
          a[j] = cs[j].atd(row);
          if (Double.isNaN(a[j])) continue;   // Skip missing observations in row
          int catColJLevel = _yt._numLevels[j];
          Arrays.fill(xy, 0, catColJLevel, 0);  // reset xy before accumulate sum, need to stay

          // Calculate x_i * Y_j where Y_j is sub-matrix corresponding to categorical col j
          for (int level = 0; level < catColJLevel ; level++) {
            for (int k = 0; k < _ncolX; k++) {
              xy[level] += chk_xold(cs, k).atd(row) * _yt.getCat(j, level, k);
            }
          }

          // Gradient wrt x_i is matrix product \grad L_{i,j}(x_i * Y_j, A_{i,j}) * Y_j'
          double[] weight = _lossFunc[j].mlgrad(xy, (int) a[j], prod, catColJLevel );
          if (_yt._transposed) {
            for (int c = 0; c < catColJLevel ; c++) {
              int cidx = _yt.getCatCidx(j, c);
              double weights = cweight * weight[c];
              double[] yArchetypes = _yt._archetypes[cidx];
              for (int k = 0; k < _ncolX; k++)
                tgrad[k] += weights * yArchetypes[k];

            }
          } else {
            for (int c = 0; c < catColJLevel; c++) {
              int cidx = _yt.getCatCidx(j, c);
              double weights = cweight * weight[c];

              for (int k = 0; k < _ncolX; k++)
                tgrad[k] += weights * _yt._archetypes[k][cidx];
            }
          }
        }

        // Numeric columns
        for (int j = _ncats; j < _ncolA; j++) {
          int js = j - _ncats;
          a[j] = cs[j].atd(row);
          if (Double.isNaN(a[j])) continue;   // Skip missing observations in row

          // Inner product x_i * y_j
          double xy1 = 0;
          for (int k = 0; k < _ncolX; k++)
            xy1 += chk_xold(cs, k).atd(row) * _yt.getNum(js, k);

          // Sum over y_j weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
          double weight = cweight * _lossFunc[j].lgrad(xy1, (a[j] - _normSub[js]) * _normMul[js]);
          for (int k = 0; k < _ncolX; k++)
            tgrad[k] += weight * _yt.getNum(js, k);
        }

        // Update row x_i of working copy with new values
        for (int k = 0; k < _ncolX; k++) {
          double xold = chk_xold(cs, k).atd(row);   // Old value of x_i
          u[k] = xold - _alpha * tgrad[k];
        }
        Arrays.fill(xnew, 0.0); // need to stay
        xnew = _parms._regularization_x.rproxgrad(u, xnew, _alpha*_parms._gamma_x, rand);
        _xreg += _parms._regularization_x.regularize(xnew);
        for (int k = 0; k < _ncolX; k++)
          chk_xnew(cs, k).set(row,xnew[k]);
      }
    }

    @Override public void reduce(UpdateX other) {
      _xreg += other._xreg;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Update Y equivalent to X step for wide datasets.  Now the Y is stored as a 2D double array instead.
  //--------------------------------------------------------------------------------------------------------------------
  private static class UpdateYeX extends MRTask<UpdateYeX> {
    // Input
    GLRMParameters _parms;
    GlrmLoss[] _lossFunc;
    final double _alpha;      // Step size divided by num cols in A
    final Archetypes _ytold;  // Old Y' matrix
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final int _ncats;         // Number of categorical cols in training frame
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    final Frame _xVecs;     // store XeY old and new

    // Output
    double[][] _ytnew;  // New Y matrix
    double _xreg;       // Regularization evaluated on new Y which is essentially x

    UpdateYeX(GLRMParameters parms, Archetypes yt, double alpha, int ncolA, int ncolX, int ncats, double[] normSub,
            double[] normMul, GlrmLoss[] lossFunc, Frame xVecs) {
      assert yt != null && yt.rank() == ncolX;
      _parms = parms;
      _lossFunc = lossFunc;
      _alpha = alpha;   // should be the same as normal dataset
      _ncolA = ncolA;
      _ncolX = ncolX;
      _ytold = yt;
      _xreg = 0;

      // Info on A (cols 1 to ncolA of frame)
      assert ncats <= ncolA;
      _ncats = ncats;
      _normSub = normSub;
      _normMul = normMul;
      _xVecs = xVecs;
    }

    /*
      instead of doing column wise, need to do row wise in order not to swap chunks containing Xs.
     */
    @Override public void map(Chunk[] cs) {
      double[] chkweight = _ytold._weights; // weight per sample
      int numTArow = (int) cs[0]._len;    // number of rows in Chunk of T(A)
      int tArowStart = (int) cs[0].start();   // first row of chunk T(A)
      int tArowEnd = numTArow + numTArow - 1;   // last row index of chunk T(A)
      Chunk[] xChunks = new Chunk[_parms._k*2]; // to store chunk of X
      _ytnew = new double[_ytold.nfeatures()][_ncolX];  // transpose of YeX
      double[] xy = null;   // store expanded enum columns content
      double[] grad = null;
      if (_ytold._numLevels[tArowStart] > 0) {  // minor optimization here
        xy = new double[_ytold._numLevels[tArowStart]];
        grad = new double[_ytold._numLevels[tArowStart]];
      }
      ArrayList<Integer> xChunkIndices = findXChunkIndices(_xVecs, tArowStart, tArowEnd, _ytold); // grab x chunk ind
      int numColIndexOffset = _ytold._catOffsets[_ncats] - _ncats;   // index into xframe numerical rows
      getXChunk(_xVecs, xChunkIndices.remove(0), xChunks); // get the first xFrame chunk
      int xChunkRowStart = (int) xChunks[0].start();   // first row index of xFrame
      int xChunkSize = (int) xChunks[0]._len;   // number of rows in xFrame
      int xRow = 0;   // store true index of X chunk
      int tARow = 0;  // store true index of T(A) chunk
      double cweight; // weight per data row.

      for (int row = 0; row < cs[0]._len; row++) {  // rows of T(A), columns of A
        tARow = row + tArowStart;     // true row index into T(A) chunks

        if (tARow < _ncats) { // dealing with a enum
          int catColJLevel = _ytold._numLevels[tARow];
          // Categorical row
          for (int j = 0; j < cs.length; j++) { // j is column of T(A) (rows of A)
            cweight = chkweight[j];
            assert !Double.isNaN(cweight) : "User-specified weight cannot be NaN";

            double a = cs[j].atd(row);       // access an element of T(A)
            if (Double.isNaN(a)) continue;   // Skip missing observations in column

            // Calculate x_i * Y_j where Y_j is sub-matrix corresponding to categorical col j
            Arrays.fill(xy, 0.0); // need to stay
            for (int level = 0; level < catColJLevel; level++) {  // finish dealing with one enum
              xRow = level+_ytold._catOffsets[tARow]-xChunkRowStart;  // equivalent row index into X chunk

              if (xRow >= xChunkSize) { // need to load in the next X chunk
                if (xChunkIndices.size() < 1) {
                  Log.err("GLRM train updateYeX", "Chunks mismatch between A transpose and X frame.");
                } else {
                  getXChunk(_xVecs, xChunkIndices.remove(0), xChunks);
                  xChunkRowStart = (int) xChunks[0].start();   // first row index of xFrame
                  xChunkSize = (int) xChunks[0]._len;   // number of rows in xFrame
                  xRow = level + _ytold._catOffsets[tARow] - xChunkRowStart;
                }
              }
              for (int k = 0; k < _ncolX; k++) {
                xy[level] += xFrameVec(xChunks, k, 0).atd(xRow) *  yArcheTypeVal(_ytold, j, k);
              }
            }

            // Gradient for level p is x_i weighted by \grad_p L_{i,j}(x_i * Y_j, A_{i,j})
            double[] weight = _lossFunc[tARow].mlgrad(xy, (int) a, grad, catColJLevel);
            for (int level = 0; level < catColJLevel; level++) {
              xRow = level+_ytold._catOffsets[tARow]-xChunkRowStart;  // equivalent row into X chunk
              for (int k = 0; k < _ncolX; k++)
                _ytnew[j][k] += cweight * weight[level] * xFrameVec(xChunks, k, 0).atd(xRow);
            }
          }
        } else {  // dealing with numerical rows
          // Numeric columns
          xRow = tARow - xChunkRowStart + numColIndexOffset; //index into x frame which expanded categoricals

          if (xRow >= xChunkSize) {  // load in new chunk of xFrame
            if (xChunkIndices.size() < 1) {
              Log.err("GLRM train", "Chunks mismatch between A transpose and X frame.");
            } else {
              getXChunk(_xVecs, xChunkIndices.remove(0), xChunks); // get a xVec chunk

              xChunkRowStart = (int) xChunks[0].start();   // first row index of xFrame
              xChunkSize = (int) xChunks[0]._len;   // number of rows in xFrame
              xRow = tARow - xChunkRowStart + numColIndexOffset;
            }
          }
          int numRow = tARow - _ncats;
          for (int j = 0; j < cs.length; j++) { // columns of T(A) chunks
            cweight = chkweight[j];
            assert !Double.isNaN(cweight) : "User-specified weight cannot be NaN";

            // Compute gradient of objective at column
              double a = cs[j].atd(row);    // T(A)
              if (Double.isNaN(a)) continue;   // Skip missing observations in column

              // Inner product x_i * y_j
              double txy = 0;
              for (int k = 0; k < _ncolX; k++)
                txy += xFrameVec(xChunks, k, 0).atd(xRow) * yArcheTypeVal(_ytold, j, k);

              // Sum over x_i weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
              double weight = cweight * _lossFunc[tARow].lgrad(txy, (a - _normSub[numRow]) * _normMul[numRow]);
              for (int k = 0; k < _ncolX; k++)
                _ytnew[j][k] += weight * xFrameVec(xChunks, k, 0).atd(xRow);

          }
        }
      }
    }

    @Override public void reduce(UpdateYeX other) {
      ArrayUtils.add(_ytnew, other._ytnew);
    }

    @Override protected void postGlobal() {
      assert _ytnew.length == _ytold.nfeatures() && _ytnew[0].length == _ytold.rank();
      Random rand = RandomUtils.getRNG(_parms._seed);

      // Compute new y_j values using proximal gradient
      for (int j = 0; j < _ytnew.length; j++) {
        double[] u = new double[_ytnew[0].length];  // Do not touch this memory allocation.  Needed for proper function.
        for (int k = 0; k < _ytnew[0].length; k++)
          u[k] = _ytold._archetypes[j][k] - _alpha * _ytnew[j][k];

        _ytnew[j] = _parms._regularization_x.rproxgrad(u, _alpha*_parms._gamma_x, rand);
        _xreg += _parms._regularization_x.regularize(_ytnew[j]);
      }
    }
  }

  public static double yArcheTypeVal(Archetypes yt, int j, int k) {
    return yt._transposed?yt._archetypes[j][k]:yt._archetypes[k][j];
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Update X equivalent to Y step for wide datasets.  Now the X is stored in H2OFrame instead of Y in 2D double array
  //--------------------------------------------------------------------------------------------------------------------
  private static class UpdateXeY extends MRTask<UpdateXeY> {
    // Input
    GLRMParameters _parms;
    GlrmLoss[] _lossFunc;
    final double _alpha;      // Step size divided by num cols in A
    final Archetypes _yt;     // _yt = Y' (transpose of Y)
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final int _ncats;         // Number of categorical cols in training frame
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    final Frame _tAVecs;       // stores T(A) in this case
    final int[] _finalCatIndex;

    // Output
    double _yreg;    // Regularization evaluated on new X

    UpdateXeY(GLRMParameters parms, Archetypes yt, double alpha, int ncolA, int ncolX, int ncats,
            double[] normSub, double[] normMul, GlrmLoss[] lossFunc, Frame tAVecs) {
      assert yt != null && yt.rank() == ncolX;
      _parms = parms;
      _yt = yt;
      _lossFunc = lossFunc;
      _alpha = alpha;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _tAVecs = tAVecs;

      // Info on A (cols 1 to ncolA of frame)
      assert ncats <= ncolA;
      _ncats = ncats;
      _normSub = normSub;
      _normMul = normMul;
      _finalCatIndex = new int[_ncats];
      for (int index=0; index < _ncats; index++) {
        _finalCatIndex[index] = _yt._catOffsets[index+1]-1;
      }
    }

    @SuppressWarnings("ConstantConditions")   // The method is too complex for IntelliJ
    @Override public void map(Chunk[] cs) {   // cs contains x vectors old and new
      double[] chkweight = _yt._weights;      // weight per sample
      int numXRow = (int) cs[0]._len;         // number of rows in Chunk of X
      int xRowStart = (int) cs[0].start();   // first row of chunk X
      int xRowEnd = xRowStart + numXRow - 1;   // last row index of chunk X
      Chunk[] tAChunks = new Chunk[_ncolA];     // to store chunk of T(A)
      Chunk[] xChunksN = new Chunk[cs.length];  // store next chunk of X just in case categoricals are cut off.
      int numCatColumns = _yt._catOffsets[_ncats];
      int xTARowStart = findOriginalColIndex(xRowStart, _ncats, numCatColumns, _yt);
      int xTARowEnd = findOriginalColIndex(xRowEnd, _ncats, numCatColumns, _yt);

      double[][] xMat = null; // store a chunk of x
      double[] xy = null;
      double[] prod = null;
      double[][] tgradEnum = null;
      double[][] uEnum = null;

      // grab the corresponding T(A) chunks
      ArrayList<Integer> tAChunkIndices = findtAChunkIndices(_tAVecs, xRowStart, xRowEnd, _yt); // grab T(A) chunk ind
      getXChunk(_tAVecs, tAChunkIndices.remove(0), tAChunks); // get the first tAFrame chunk
      int tAColNum = tAChunks.length;

      int tAChunkRowStart = (int) tAChunks[0].start();   // first row index of T(A) Frame
      int tAChunkSize = (int) tAChunks[0]._len;   // number of rows in T(A) Frame
      int xRow = 0;   // store row index of X chunk
      double[] xnew = new double[_ncolX];

      if (_yt._numLevels[tAChunkRowStart] > 0) {
        tgradEnum = new double[_yt._numLevels[tAChunkRowStart]][_ncolX];
        uEnum = new double[_yt._numLevels[tAChunkRowStart]][_ncolX];
        xMat = new double[_yt._numLevels[tAChunkRowStart]][_ncolX];
        xy = new double[_yt._numLevels[tAChunkRowStart]]; // maximum categorical level column is always the first one
        prod = new double[_yt._numLevels[tAChunkRowStart]];
      }

      double a = 0;  // store an element of T(A)
      double[] tgrad = new double[_ncolX];  // store a row of tgrad
      double[] u = new double[_ncolX];      // store a row

      Random rand = RandomUtils.getRNG(0);
      _yreg = 0;
      int row = 0;        // relative row index into current t(A) chunk
      int rowX = 0;       // relative row index into current X chunk
      int rowXTrue = 0;   // true row index into X chunk from the start of an expanded enum column
      int currentXRowstart = 0; // denote the enum columns actually contained in current X chunk
      int currentXRowEnd = 0;

      for (int tArow = xTARowStart; tArow <= xTARowEnd; tArow++) {  // each row of T(A) chunk under consideration
        row = tArow-tAChunkRowStart;    // translate to relative row index of a T(A) chunk, change automatically
        if (row >= tAChunkSize) {    // hitting the end of T(A) chunk, reload it
          getXChunk(_tAVecs, tAChunkIndices.remove(0), tAChunks);
          tAChunkRowStart = (int) tAChunks[0].start();   // first row index of T(A) Frame
          tAChunkSize = (int) tAChunks[0]._len;   // number of rows in T(A) Frame
          row = tArow-tAChunkRowStart;   // translate to relative row index of a T(A) chunk, change automatically
        }

        rand.setSeed(_parms._seed + cs[0].start() + row); //global row ID determines the seed
        if (tArow < _ncats) {   // dealing with a row of T(A) that is enum
          int catColJLevel = _yt._numLevels[tArow];     // initialize tgradEnum to all zeros
          for (int level=0; level<tgradEnum.length; level++) { // need to stay
            Arrays.fill(tgradEnum[level], 0, _parms._k, 0);
          }

          for (int j=0; j < tAColNum; j++) { // go throw all columns of T(A)
            // compute gradient of objective for enum columns
            if (j == 0) { // at the start of a enum column before expansion
              xRow = rowX+xRowStart;   // absolute true row index into X chunk, should have this one
              rowXTrue = _yt._catOffsets[tArow];  // absolute start of correct true expanded x chunk row index,
              int levelSeen = 0;
              if (xRow > rowXTrue) {  // need info from previous X chunk
                ArrayList<Integer> tempXChunkCidx = findXChunkIndices(_fr, rowXTrue, xRow - 1, _yt);
                getXChunk(_fr, tempXChunkCidx.remove(0), xChunksN); // read in a new X chunk
                int tempXStart = (int) xChunksN[0].start();
                int tempXSize = (int) xChunksN[0]._len;
                for (int rowN = rowXTrue; rowN < xRow; rowN++) {  // grab start enum column upto xRow
                  int relRowX = rowN-tempXStart;  // relative row into new X chunk

                  if (relRowX > tempXSize) {  // need to get to next X chunk in order to get the x row
                    if (tempXChunkCidx.size() < 1) {
                      Log.err("GLRM train", "Chunks mismatch between A transpose and X frame.");
                    } else {
                      getXChunk(_fr, tempXChunkCidx.remove(0), xChunksN); // read in a new X chunk
                      tempXStart = (int) xChunksN[0].start();
                      tempXSize = (int) xChunksN[0]._len;
                      relRowX = rowN - tempXStart;
                    }
                  }

                  for (int k = 0; k < _ncolX; k++) { // store a matrix of catColJLevel by _ncolX elements to form one T(A)
                    xMat[levelSeen][k] = xFrameVec(xChunksN, k, 0).atd(relRowX);
                  }
                  levelSeen++;
                }
              }

              currentXRowstart = levelSeen;
              for (int level=levelSeen; level < catColJLevel; level++) {
                if (rowX <= xRowEnd) {
                  for (int k = 0; k < _ncolX; k++) { // store a matrix of catColJLevel by _ncolX elements to form one T(A)
                    xMat[levelSeen][k] = xFrameVec(cs, k, 0).atd(rowX);
                  }
                  levelSeen++;
                  rowX++;   // move relative row index of current X chunk as well
                  xRow++;   // move to next absolute index of current X chunk
                } else {  // reach end of current X chunk and if calculation not finished, need more next X chunk
                  break;
                }
              }
              currentXRowEnd=levelSeen;
              int levelLeft = catColJLevel-levelSeen;
              if (levelLeft > 0) {  // current x chunk does not contains the enum rows needed, next chunk does
                int endXRow = xRow+levelLeft-1;
                ArrayList<Integer> tempXChunkCidx = findXChunkIndices(_fr, xRow, endXRow, _yt);
                getXChunk(_fr, tempXChunkCidx.remove(0), xChunksN); // read in a new X chunk
                int tempXStart = (int) xChunksN[0].start();
                int tempXSize = (int) xChunksN[0]._len;
                for (int rowN = xRow; rowN <= endXRow; rowN++) {
                  //Arrays.fill(tgradEnum[levelSeen], 0, _ncolX, 0); // time to reset tgrad with all zeros
                  int relRowX = rowN-tempXStart;

                  if (relRowX > tempXSize) {
                    if (tempXChunkCidx.size() < 1) {
                      Log.err("GLRM train", "Chunks mismatch between A transpose and X frame.");
                    } else {
                      getXChunk(_fr, tempXChunkCidx.remove(0), xChunksN); // read in a new X chunk
                      tempXStart = (int) xChunksN[0].start();
                      tempXSize = (int) xChunksN[0]._len;
                      relRowX = rowN - tempXStart;
                    }
                  }

                  for (int k = 0; k < _ncolX; k++) { // store a matrix of catColJLevel by _ncolX elements to form one T(A)
                    xMat[levelSeen][k] = xFrameVec(xChunksN, k, 0).atd(relRowX);
                  }
                  levelSeen++;
                }
              }
            }

            // Additional user-specified weight on loss for this row
            double cweight = chkweight[j];
            assert !Double.isNaN(cweight) : "User-specified weight cannot be NaN";

            a = tAChunks[j].atd(row);  // look at each element of T(A)
            if (Double.isNaN(a)) {
              continue;   // Skip missing observations in row and adjust the corresponding relative index into X chunk
            }
            Arrays.fill(xy, 0, catColJLevel, 0);  // reset xy before accumulate sum, need to stay

            // obtained the matrix of X to form XY for one element of T(A)
            for (int level=0; level < catColJLevel; level++) {
              for (int k = 0; k < _ncolX; k++) {
                xy[level] += xMat[level][k] * yArcheTypeVal(_yt, j, k);
              }
            }

            double[] weight = _lossFunc[tArow].mlgrad(xy, (int) a, prod, catColJLevel); // catColJLevel by 1
            // form tgradEnum which is catColJLevel by _ncolX
            for (int c = 0; c < catColJLevel; c++) {
              for (int k = 0; k < _ncolX; k++) {
                tgradEnum[c][k]+=weight[c]*cweight*yArcheTypeVal(_yt, j, k); // need to accumulate this over all columns
              }
            }
          }

          //update X which is catColJLevel by k by uEnum per one row of T(A)
          for (int level=currentXRowstart; level<currentXRowEnd; level++) { // only make updates to rows in current X chunk
            // only update for the rows that the current X chunk has
            for (int k = 0; k < _ncolX; k++) {
              uEnum[level][k] = xMat[level][k] - _alpha * tgradEnum[level][k];
            }
            // calculate how much update is due to regularization term
            Arrays.fill(xnew, 0.0); // need to stay
            xnew = _parms._regularization_y.rproxgrad(uEnum[level], xnew,_alpha*_parms._gamma_y, rand);
            _yreg += _parms._regularization_y.regularize(xnew);
            // need to update X chunks with new X values, it is of size catColJLevel by k
            // checking which x chunks contains the x elements that needed to be updated.
            int trueXRow = level+_yt._catOffsets[tArow];    // absolute Row index into X chunk
            xRow = trueXRow-xRowStart;
            for (int k=0; k<_ncolX; k++)  {
              xFrameVec(cs, k, _parms._k).set(xRow, xnew[k]);
            }
          }
        } else {  // dealing with numerical columns, separate from categoricals
          Arrays.fill(tgrad, 0.0);  // temporary gradient for comparison, need to stay
          rowX = findExpColIndex(tArow, _ncats, _yt) - xRowStart;  // translate to relative row index of x chunk
          int numRow = tArow - _ncats;  // index into numerical rows of T(A) without enum columns expansion
          for (int j=0; j<tAColNum; j++) {
            double cweight = chkweight[j];
            assert !Double.isNaN(cweight) : "User-specified weight cannot be NaN";
            a = tAChunks[j].atd(row);    // take one element of T(A)
            if (Double.isNaN(a)) continue;

            // Inner product of X_i y_j to get an element of T(A)i, j
            double txy = 0;
            for (int k = 0; k < _ncolX; k++) {
              txy += xFrameVec(cs, k, 0).atd(rowX)*yArcheTypeVal(_yt, j, k);
            }
            double weight = cweight * _lossFunc[tArow].lgrad(txy, (a-_normSub[numRow])*_normMul[numRow]);
            for (int k=0; k<_ncolX; k++) {
              tgrad[k] += weight * yArcheTypeVal(_yt, j, k);
            }
          }
          //update row x
          for (int k=0; k<_ncolX; k++) {
            double xold = xFrameVec(cs, k, 0).atd(rowX);
            u[k] = xold-_alpha*tgrad[k];
          }

          // calculate how much update is due to regularization term
          Arrays.fill(xnew, 0.0); // need to stay
          xnew = _parms._regularization_y.rproxgrad(u, xnew,_alpha*_parms._gamma_y, rand);
          _yreg += _parms._regularization_y.regularize(xnew);

          for (int k=0; k<_ncolX; k++)  {
            xFrameVec(cs, k, _parms._k).set(rowX, xnew[k]);
          }
        }
      }
    }

    @Override public void reduce(UpdateXeY other) {
      _yreg += other._yreg;
    }
  }

  private static class UpdateY extends MRTask<UpdateY> {
    // Input
    GLRMParameters _parms;
    GlrmLoss[] _lossFunc;
    final double _alpha;      // Step size divided by num cols in A
    final Archetypes _ytold;  // Old Y' matrix
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final int _ncats;         // Number of categorical cols in training frame
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    final int _weightId;

    // Output
    double[][] _ytnew;  // New Y matrix
    double _yreg;       // Regularization evaluated on new Y

    UpdateY(GLRMParameters parms, Archetypes yt, double alpha, int ncolA, int ncolX, int ncats, double[] normSub,
            double[] normMul, GlrmLoss[] lossFunc, int weightId) {
      assert yt != null && yt.rank() == ncolX;
      _parms = parms;
      _lossFunc = lossFunc;
      _alpha = alpha;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _ytold = yt;
      _yreg = 0;

      // Info on A (cols 1 to ncolA of frame)
      assert ncats <= ncolA;
      _ncats = ncats;
      _weightId = weightId;
      _normSub = normSub;
      _normMul = normMul;
    }

    private Chunk chk_xnew(Chunk[] chks, int c) {
      return chks[_ncolA + _ncolX + c];
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;
      _ytnew = new double[_ytold.nfeatures()][_ncolX];
      Chunk chkweight = _weightId >= 0 ? cs[_weightId]:new C0DChunk(1,cs[0]._len);
      double[] xy = null;
      double[] grad = null;
      if (_ytold._numLevels[0] > 0) {
        xy = new double[_ytold._numLevels[0]];
        grad = new double[_ytold._numLevels[0]];
      }

      // Categorical columns
      for (int j = 0; j < _ncats; j++) {
        int catColJLevel = _ytold._numLevels[j];
        // Compute gradient of objective at column
        for (int row = 0; row < cs[0]._len; row++) {
          double a = cs[j].atd(row);
          if (Double.isNaN(a)) continue;   // Skip missing observations in column
          double cweight = chkweight.atd(row);
          assert !Double.isNaN(cweight) : "User-specified weight cannot be NaN";

          // Calculate x_i * Y_j where Y_j is sub-matrix corresponding to categorical col j
          Arrays.fill(xy, 0.0);  // need to stay
          for (int level = 0; level < catColJLevel; level++) {
            for (int k = 0; k < _ncolX; k++) {
              xy[level] += chk_xnew(cs, k).atd(row) * _ytold.getCat(j,level,k);
            }
          }

          // Gradient for level p is x_i weighted by \grad_p L_{i,j}(x_i * Y_j, A_{i,j})
          double[] weight = _lossFunc[j].mlgrad(xy, (int)a, grad,catColJLevel);
          for (int level = 0; level < catColJLevel; level++) {
            for (int k = 0; k < _ncolX; k++)
              _ytnew[_ytold.getCatCidx(j, level)][k] += cweight * weight[level] * chk_xnew(cs, k).atd(row);
          }
        }
      }

      // Numeric columns
      for (int j = _ncats; j < _ncolA; j++) {
        int js = j - _ncats;
        int yidx = _ytold.getNumCidx(js);

        // Compute gradient of objective at column
        for (int row = 0; row < cs[0]._len; row++) {
          double a = cs[j].atd(row);
          if (Double.isNaN(a)) continue;   // Skip missing observations in column

          // Additional user-specified weight on loss for this row
          double cweight = chkweight.atd(row);
          assert !Double.isNaN(cweight) : "User-specified weight cannot be NaN";

          // Inner product x_i * y_j
          double txy = 0;
          for (int k = 0; k < _ncolX; k++)
            txy += chk_xnew(cs, k).atd(row) * _ytold.getNum(js,k);

          // Sum over x_i weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
          double weight = cweight * _lossFunc[j].lgrad(txy, (a - _normSub[js]) * _normMul[js]);
          for (int k = 0; k < _ncolX; k++)
            _ytnew[yidx][k] += weight * chk_xnew(cs, k).atd(row);
        }
      }
    }

    @Override public void reduce(UpdateY other) {
      ArrayUtils.add(_ytnew, other._ytnew);
    }

    @Override protected void postGlobal() {
      assert _ytnew.length == _ytold.nfeatures() && _ytnew[0].length == _ytold.rank();
      Random rand = RandomUtils.getRNG(_parms._seed);

      // Compute new y_j values using proximal gradient
      for (int j = 0; j < _ytnew.length; j++) {
        double[] u = new double[_ytnew[0].length];  // Do not touch this memory allocation.  Needed for proper function.
        for (int k = 0; k < _ytnew[0].length; k++)
          u[k] = _ytold._archetypes[j][k] - _alpha * _ytnew[j][k];

        _ytnew[j] = _parms._regularization_y.rproxgrad(u, _alpha*_parms._gamma_y, rand);
        _yreg += _parms._regularization_y.regularize(_ytnew[j]);
      }
    }
  }


		/*
		  Calculate the sum over the loss function in the optimization objective for wideDatasets.  Basically, we
		  are looking at T(A) = XY.  In this case, Y is equivalent to T(X) and X is equivalent to T(Y) and we are
		  looking at T(A) and not A.
		  */
		private static class ObjCalcW extends MRTask<ObjCalcW> {
				// Input
				GLRMParameters _parms;
				GlrmLoss[] _lossFunc;
				final Archetypes _yt;     // _yt = Y' (transpose of Y) (YeX, stored as 2D array)
				final int _ncolA;         // Number of cols in training frame
				final int _ncolX;         // Number of cols in X (k)
				final int _ncats;         // Number of categorical cols in training frame
				final double[] _normSub;  // For standardizing training data
				final double[] _normMul;
				final boolean _regX;      // Should I calculate regularization of (old) X matrix?
				final Frame _xVecs;        // store XeY and XeY new
    final int _xOffset;       // which X to use, 0 to use old new, _parms._k to use new one

				// Output
				double _loss;       // Loss evaluated on A - XY using new X (and current Y)
				static double _xold_reg;   // Regularization evaluated on old X st (Y for wide datasets)

				ObjCalcW(GLRMParameters parms, Archetypes yt, int ncolA, int ncolX, int ncats, double[] normSub, double[] normMul,
												GlrmLoss[] lossFunc, boolean regX, Frame xVecs, int xOffset) {
						assert yt != null && yt.rank() == ncolX;
						assert ncats <= ncolA;
						_parms = parms;
						_yt = yt;
						_lossFunc = lossFunc;
						_ncolA = ncolA;
						_ncolX = ncolX;
						_ncats = ncats;
						_regX = regX;
						_xVecs = xVecs;
						_normSub = normSub;
						_normMul = normMul;
						_xOffset = xOffset;
				}

				@SuppressWarnings("ConstantConditions")  // The method is too complex
				@Override public void map(Chunk[] cs) {	// cs now is n by m, x is n_exp by k, y is 2-D array of m by k
      double[] chkweight = _yt._weights; // weight per sample
      int numTArow = (int) cs[0]._len;
      int tArowStart = (int) cs[0].start();
      int tArowEnd = numTArow+tArowStart-1; // last row index
      Chunk[] xChunks = new Chunk[_parms._k*2]; // number of columns, store old and new X
      int startxcidx = cs[0].cidx();

      // here, reg_x is meant for the y vectors and only do it once per obj calculation
      if ((_regX) && (startxcidx == 0)) {
        calXOldReg(_yt._archetypes, _yt._archetypes.length);
      }

        ArrayList<Integer> xChunkIndices = findXChunkIndices(_xVecs, tArowStart, tArowEnd, _yt);  // contains x chunks
        double[] xy = null;   // store the vector of categoricals for one column
        int numColIndexOffset = _yt._catOffsets[_ncats] - _ncats;   // index into xframe numerical columns

        if (_yt._numLevels[tArowStart] > 0) {   // allocate memory only if there are categoricals in T(A) chunks
          xy = new double[_yt._numLevels[tArowStart]];
        }

        getXChunk(_xVecs, xChunkIndices.remove(0), xChunks); // get the first xFrame chunk
        int xChunkRowStart = (int) xChunks[0].start();   // first row index of xFrame
        int xChunkSize = (int) xChunks[0]._len;   // number of rows in xFrame
        int xRow = 0;
        int tARow = 0;
        assert ((tArowStart >= xChunkRowStart) && (tArowStart < (xChunkRowStart + xChunkSize)));  // xFrame has T(A) start

        for (int rowIndex = 0; rowIndex < numTArow; rowIndex++) {  // use indexing of T(A)
          tARow = rowIndex + tArowStart;  // true row index of T(A)


          if (tARow < _ncats) { // dealing with categorical columns now
            // perform comparison for categoricals
            int catRowLevel = _yt._numLevels[tARow];    // number of bits to expand a categorical columns

            for (int colIndex = 0; colIndex < cs.length; colIndex++) {  // look at one element of T(A)
              double a = cs[colIndex].atd(rowIndex);    // grab an element of T(A)
              if (Double.isNaN(a)) continue;
              ;

              Arrays.fill(xy, 0, catRowLevel, 0);   // reset before next accumulated sum, need to stay

              for (int level = 0; level < catRowLevel; level++) { // one element of T(A) composed of several of XY
                xRow = level + _yt._catOffsets[tARow] - xChunkRowStart; // relative row

                if (xRow >= xChunkSize) {  // load in new chunk of xFrame
                  if (xChunkIndices.size() < 1) {
                    Log.err("GLRM train", "Chunks mismatch between A transpose and X frame.");
                  } else {
                    getXChunk(_xVecs, xChunkIndices.remove(0), xChunks); // get a xVec chunk

                    xChunkRowStart = (int) xChunks[0].start();   // first row index of xFrame
                    xChunkSize = (int) xChunks[0]._len;   // number of rows in xFrame
                    xRow = level + _yt._catOffsets[tARow] - xChunkRowStart;
                  }
                }
                for (int innerCol = 0; innerCol < _parms._k; innerCol++) {
                  xy[level] += xFrameVec(xChunks, innerCol, _xOffset).atd(xRow)*yArcheTypeVal(_yt, colIndex, innerCol);
                }
              }
              _loss += chkweight[colIndex] * _lossFunc[tARow].mloss(xy, (int) a, catRowLevel);
            }

          } else {  // looking into numerical columns here
            // perform comparison for numericals
            xRow = tARow - xChunkRowStart + numColIndexOffset; //index into x frame which expanded categoricals

            if (xRow >= xChunkSize) {  // load in new chunk of xFrame
              if (xChunkIndices.size() < 1) {
                Log.err("GLRM train", "Chunks mismatch between A transpose and X frame.");
              } else {
                getXChunk(_xVecs, xChunkIndices.remove(0), xChunks); // get a xVec chunk

                xChunkRowStart = (int) xChunks[0].start();   // first row index of xFrame
                xChunkSize = (int) xChunks[0]._len;   // number of rows in xFrame
                xRow = tARow - xChunkRowStart + numColIndexOffset;
              }
            }

            int numRow = tARow - _ncats;    // index into T(A) without categorical type expansion
            for (int colIndex = 0; colIndex < cs.length; colIndex++) {
              double a = cs[colIndex].atd(rowIndex);  // access dataset T(A)
              if (Double.isNaN(a)) continue;
              double txy = 0.0;
              for (int innerCol = 0; innerCol < _parms._k; innerCol++) {
                txy += xFrameVec(xChunks, innerCol, _xOffset).atd(xRow) * yArcheTypeVal(_yt, colIndex, innerCol);
              }
              _loss += chkweight[colIndex] * _lossFunc[tARow].loss(txy, (a - _normSub[numRow]) * _normMul[numRow]);
            }
          }
        }
				}

				private void calXOldReg(double[][] yvals, int yLen) {
      for (int j = 0; j < yLen; j++) {
        double[] xrow = Arrays.copyOf(yvals[j], _parms._k);
        _xold_reg += _parms._regularization_x.regularize(xrow);
      }
    }

				@Override public void reduce(ObjCalcW other) {
   //   if (_newChunk || other._newChunk)
        _loss += other._loss;
				}
		}

  public static Chunk xFrameVec(Chunk[] chks, int c, int offset) {
    return chks[offset + c];
  }

  /*
  For wide datasets, X is stored in another frame different from frame containing T(A).  Tasks are
  passed chunks containing T(A).  Hence, to match the row numbers in a T(A) chunk, we need to grab
  the X chunk correctly, hence this is what this function does.
  */
  public static void getXChunk(Frame xVecs, int chunkIdx, Chunk[] xChunks) {
    int xWidth = xChunks.length;        // width of x and xold matices
    for (int j = 0; j < xWidth; ++j) {  // read in the relevant xVec chunks
      xChunks[j] = xVecs.vec(j).chunkForChunkIdx(chunkIdx);
    }
  }

  /*
		Recall again, tasks are passed chunks containing chunks of xVec.  Need to find the corresponding
		chunks in TA that contains the same row number of x Vecs.  The correct chunks will be stored as an array.
	*/
  public static ArrayList<Integer> findtAChunkIndices(Frame tAVecs, int xStart, int xEnd, Archetypes yt) {
    ArrayList<Integer> tAcidx = new ArrayList<Integer>();
    int tANChunks = tAVecs.anyVec().nChunks();  // number of chunks of input frame

    if (tANChunks == 1) {  // no need to do anything.
      tAcidx.add(0);
      return tAcidx;   // only one chunk.  Everything should be there.
    }

    int startTAcidx=0;
    int numCats = yt._catOffsets.length-1;  // number of categorical columns in T(A)
    int numCatColumns = yt._catOffsets[numCats];  // number of cat expanded columns

    xStart = findOriginalColIndex(xStart, numCats, numCatColumns, yt);  // translate col indices to expanded column index of enums
    xEnd = findOriginalColIndex(xEnd, numCats, numCatColumns, yt);

    findGoodCidx(tAVecs, tAcidx, false, xStart, tANChunks, startTAcidx);  // find start T(A) cidx
    startTAcidx = tAcidx.get(0);
    if (startTAcidx < (tANChunks-1)) {
      findGoodCidx(tAVecs, tAcidx, true, xEnd, tANChunks, startTAcidx);     // find all blocks
    }

    return tAcidx;
  }

  /*
				Recall again, tasks are passed chunks containing chunks of T(A).  Need to find the corresponding
				chunks that contains the same row number of T(A).  The correct chunks will be stored as an array.
			*/
  public static ArrayList<Integer> findXChunkIndices(Frame xVecs, int taStart, int taEnd, Archetypes yt) {
    ArrayList<Integer> xcidx = new ArrayList<Integer>();
    int xNChunks = xVecs.anyVec().nChunks();

    if (xNChunks == 1) {
      xcidx.add(0);
      return xcidx;   // only one chunk.  Everything should be there.
    }

    int startTAcidx=0;
    int numCats = yt._catOffsets.length-1;

    taStart = findExpColIndex(taStart, numCats, yt);    // translate col indices to expanded column index of enums
    taEnd = findExpColIndex(taEnd, numCats, yt);        // last column of T(A)

    findGoodCidx(xVecs, xcidx, false, taStart, xNChunks, startTAcidx);  // find start xcidx
    startTAcidx = xcidx.get(0);
    if (startTAcidx < (xNChunks-1)) {
      findGoodCidx(xVecs, xcidx, true, taEnd, xNChunks, startTAcidx);     // find all blocks
    }

    return xcidx;
  }

  /*
				For each T(A) chunk, find the chunk indices of X frame that contains the same rows as in the T(A) chunk.
				It will add the correct chunk index into an array.
			*/
  public static void findGoodCidx(Frame xVecs, ArrayList<Integer> currentList, boolean addIndex, int taIndex,
                                  int xNChunks, int startTAcidx) {
    Chunk[] xChunks = new Chunk[1];
    for (int index = startTAcidx; index < xNChunks; index++) {  // check to make sure start row is there
      xChunks[0] = xVecs.vec(0).chunkForChunkIdx(index);
      long xStart = xChunks[0].start();    // start row of xVec Chunks
      long xEnd = xStart + xChunks[0]._len;

      if ((taIndex >= xStart) && (taIndex < xEnd)) { // found end chunk
        if (currentList.size() > 0) {   // do not want to add duplicate indices
          if (!currentList.contains(index))
            currentList.add(index);
        } else {
          currentList.add(index); // first elligible one, add it
        }
        break;
      }

      if (addIndex && (!currentList.contains(index))) {
        currentList.add(index); // add all chunks containing the range of rows between start row and end row.
      }
    }
  }

  /*
				Translate the column index into expanded column index taking care of enum 1-hot encoding expansion.
			*/
  private static int findExpColIndex(int oldIndex, int numCats, Archetypes yt) {
    if (oldIndex < numCats) {  // find true row start considering categorical columns
      return yt._catOffsets[oldIndex];
    } else {  // taStart in the numerical columns now
      return oldIndex-numCats+yt._catOffsets[numCats];
    }
  }

  /*
		Translate the expanded column index back to origin column index before enum 1-hot encoding expansion.
	*/
  private static int findOriginalColIndex(int oldIndex, int numCats, int totEnumColumns, Archetypes yt) {
    if (oldIndex <totEnumColumns) { // x Chunk column index is one of enum columns
      for (int index = 1; index < yt._catOffsets.length; index++) {
        if (oldIndex < yt._catOffsets[index]) {
          return (index-1); // return the corresponding T(A) row index
        }
      }
    }
    return (oldIndex-totEnumColumns+numCats);
  }

		// Calculate the sum over the loss function in the optimization objective
  private static class ObjCalc extends MRTask<ObjCalc> {
    // Input
    GLRMParameters _parms;
    GlrmLoss[] _lossFunc;
    final Archetypes _yt;     // _yt = Y' (transpose of Y)
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final int _ncats;         // Number of categorical cols in training frame
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    final int _weightId;
    final boolean _regX;      // Should I calculate regularization of (old) X matrix?

    // Output
    double _loss;       // Loss evaluated on A - XY using new X (and current Y)
    double _xold_reg;   // Regularization evaluated on old X

    ObjCalc(GLRMParameters parms, Archetypes yt, int ncolA, int ncolX, int ncats, double[] normSub, double[] normMul,
            GlrmLoss[] lossFunc, int weightId) {
      this(parms, yt, ncolA, ncolX, ncats, normSub, normMul, lossFunc, weightId, false);
    }
    ObjCalc(GLRMParameters parms, Archetypes yt, int ncolA, int ncolX, int ncats, double[] normSub, double[] normMul,
            GlrmLoss[] lossFunc, int weightId, boolean regX) {
      assert yt != null && yt.rank() == ncolX;
      assert ncats <= ncolA;
      _parms = parms;
      _yt = yt;
      _lossFunc = lossFunc;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _ncats = ncats;
      _regX = regX;

      _weightId = weightId;
      _normSub = normSub;
      _normMul = normMul;
    }

    private Chunk chk_xnew(Chunk[] chks, int c) {
      return chks[_ncolA + _ncolX + c];
    }

    @SuppressWarnings("ConstantConditions")  // The method is too complex
    @Override public void map(Chunk[] cs) {
						assert (_ncolA + 2 * _ncolX) == cs.length;

      Chunk chkweight = _weightId >= 0 ? cs[_weightId]:new C0DChunk(1,cs[0]._len);  // weight is per data sample
      _loss = _xold_reg = 0;
      double[] xrow = null;
      double[] xy = null;

      if (_yt._numLevels[0] > 0)  // only allocate xy when there are categorical columns
        xy = new double[_yt._numLevels[0]];    // maximum categorical level column is always the first one

      if (_regX)  // allocation memory only if necessary
         xrow = new double[_ncolX];


        for (int row = 0; row < cs[0]._len; row++) {
          // Additional user-specified weight on loss for this row
          double cweight = chkweight.atd(row);  // weight is per row for normal dataset
          assert !Double.isNaN(cweight) : "User-specified weight cannot be NaN";
          // Categorical columns
          for (int j = 0; j < _ncats; j++) {    // contribution from categoricals
            _loss += cweight*lossDueToCategorical(cs, j, row, xy);
          }

          // Numeric columns
          for (int j = _ncats; j < _ncolA; j++) {
            _loss += cweight*lossDueToNumeric(cs, j, row, _ncats);
          }
          // Calculate regularization term for old X if requested
          if (_regX) {
            _xold_reg += regularizationTermOldX(cs, row, xrow, _ncolA, _ncolA + _ncolX, _ncolX);
          }
        }
    }

    private double regularizationTermOldX(Chunk[] cs, int row, double[] xrow, int colStart, int colEnd, int colWidth) {
      int idx = 0;
      for (int j = colStart; j < colEnd; j++) {
        xrow[idx] = cs[j].atd(row);
        idx++;
      }
      assert idx == colWidth;
      return _parms._regularization_x.regularize(xrow);
    }

    private double lossDueToNumeric(Chunk[] cs, int j, int row, int offsetA) {
      double a = cs[j].atd(row);
      if (Double.isNaN(a)) return 0.0;   // Skip missing observations in row

      // Inner product x_i * y_j
      double txy = 0;
      int js = j - offsetA;
      for (int k = 0; k < _ncolX; k++)
        txy += chk_xnew(cs, k).atd(row) * _yt.getNum(js, k);

      return _lossFunc[j].loss(txy, (a - _normSub[js]) * _normMul[js]);
    }

    private double lossDueToCategorical(Chunk[] cs, int colInd, int row, double[] xy) {
      double a = cs[colInd].atd(row);
      if (Double.isNaN(a)) return 0.0;
      int catColJLevel = _yt._numLevels[colInd];
      Arrays.fill(xy, 0, catColJLevel, 0);  // reset before next accumulation sum, need to stay

      // Calculate x_i * Y_j where Y_j is sub-matrix corresponding to categorical col j
      for (int level = 0; level < catColJLevel; level++) {  // level index into extra columns due to categoricals.
        for (int k = 0; k < _ncolX; k++) {
          xy[level] += chk_xnew(cs, k).atd(row) * _yt.getCat(colInd, level, k);
        }
      }
      return _lossFunc[colInd].mloss(xy, (int)a, catColJLevel);
    }

    @Override public void reduce(ObjCalc other) {
      _loss += other._loss;
      _xold_reg += other._xold_reg;
    }
  }

  // Solves XD = AY' for X where A is m x n, Y is k x n, D is k x k, and m >> n > k
  // Resulting matrix X = (AY')D^(-1) will have dimensions m x k.  Used in initialization only, no need to optimize.
  private static class CholMulTask extends MRTask<CholMulTask> {
    final Archetypes _yt;     // _yt = Y' (transpose of Y)
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final int _ncats;         // Number of categorical cols in training frame
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    CholeskyDecomposition _chol;   // Cholesky decomposition of D = D', since we solve D'X' = DX' = AY'

    CholMulTask(CholeskyDecomposition chol, Archetypes yt, int ncolA, int ncolX, int ncats,
                double[] normSub, double[] normMul) {
      assert yt != null && yt.rank() <= ncolX;
      assert ncats <= ncolA;
      _yt = yt;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _ncats = ncats;
      _chol = chol;

      _normSub = normSub;
      _normMul = normMul;
    }

    // [A,X,W] A is read-only training data, X is left matrix in A = XY decomposition, W is working copy of X
    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;
      double[] xrow = new double[_ncolX];

      for (int row = 0; row < cs[0]._len; row++) {
        // 1) Compute single row of AY'
        for (int k = 0; k < _ncolX; k++) {
          // Categorical columns
          double x = 0;
          for (int d = 0; d < _ncats; d++) {
            double a = cs[d].atd(row);
            if (Double.isNaN(a)) continue;
            x += _yt.getCat(d, (int)a, k);
          }

          // Numeric columns
          for (int d = _ncats; d < _ncolA; d++) {
            int ds = d - _ncats;
            double a = cs[d].atd(row);
            if (Double.isNaN(a)) continue;
            x += (a - _normSub[ds]) * _normMul[ds] * _yt.getNum(ds, k);
          }
          xrow[k] = x;
        }

        // 2) Cholesky solve for single row of X
        // _chol.solve(xrow);
        Matrix tmp = _chol.solve(new Matrix(new double[][] {xrow}).transpose());
        xrow = tmp.getColumnPackedCopy();

        // 3) Save row of solved values into X (and copy W = X)
        int i = 0;
        for (int d = _ncolA; d < _ncolA+_ncolX; d++) {
          cs[d].set(row, xrow[i]);
          cs[d+_ncolX].set(row, xrow[i++]);
        }
        assert i == xrow.length;
      }
    }
  }
}

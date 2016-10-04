package hex.glrm;

import hex.*;
import hex.svd.SVDModel.SVDParameters;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;
import water.util.TwoDimTable;

import java.util.Random;

import static hex.glrm.GlrmLoss.*;

/**
 * GLRM (<a href="https://web.stanford.edu/~boyd/papers/pdf/glrm.pdf">Generalized Low Rank Model</a>).
 *
 * The model seeks to represent an input frame A of dimensions m x n as a product of two smaller matrices X (m x k)
 * and Y (k x n) of rank k each. To this end, the model solves a generic optimization problem
 *    Loss(A, XY) + Rx(X) + Ry(Y) -> min_{X,Y}
 * in other words it tries to find X and Y such that XY is close to A (as measured by the loss function), taking into
 * account regularization constraints on X and Y as well.
 *
 * Note that the input frame A may have columns of different types; while output matrices X and Y are always
 * real-valued.
 *
 * The Loss function is assumed to be separable in each element of the matrix, so that
 *    Loss(A, XY) = Sum[L_{ij}(A_{ij}, x_i y_j)  over i=1..m, j=1..n]
 * the individual loss functions can be different for each element; but in our implementation we assume that L_{ij}'s
 * are constant over rows and may only differ by columns. Thus, L_{ij} == L_j.
 *
 * The regularizers Rx and Ry are assumed to be row-separable:
 *    Rx(X) = Sum[rx_i(x_i)  for i=1..m]
 *    Ry(Y) = Sum[ry_j(x_j)  for j=1..n]
 *
 * The output of the model consists of matrices X and Y. There are multiple interpretations of these (see section 5.4
 * in Boyd's paper). In particular,
 *   + The rows of Y (1 x n) can be interpreted as "idealized examples" of input rows (even if rows of Y are always
 *     real-valued, while rows of the input data may have any types). Thus, we call them *archetypes* in the code.
 *   + The rows of X (1 x k) provide an embedding of each original data row into a lower-dimensional space. Thus, we
 *     call them "representations" of the data.
 */
public class GLRMModel extends Model<GLRMModel, GLRMModel.GLRMParameters, GLRMModel.GLRMOutput>
        implements Model.GLRMArchetypes {


  //--------------------------------------------------------------------------------------------------------------------
  // Input parameters
  //--------------------------------------------------------------------------------------------------------------------

  public static class GLRMParameters extends Model.Parameters {
    @Override public String algoName() { return "GLRM"; }
    @Override public String fullName() { return "Generalized Low Rank Modeling"; }
    @Override public String javaName() { return GLRMModel.class.getName(); }
    @Override public long progressUnits() { return 2 + _max_iterations; }

    // Data transformation (demean to compare with PCA)
    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE;
    public int _k = 1;                       // Rank of resulting XY matrix
    public GLRM.Initialization _init = GLRM.Initialization.PlusPlus;  // Initialization of Y matrix
    public SVDParameters.Method _svd_method = SVDParameters.Method.Randomized;  // SVD initialization method (for _init = SVD)
    public Key<Frame> _user_y;               // User-specified Y matrix (for _init = User)
    public Key<Frame> _user_x;               // User-specified X matrix (for _init = User)
    public boolean _expand_user_y = true;    // Should categorical columns in _user_y be expanded via one-hot encoding? (for _init = User)

    // Loss functions
    public GlrmLoss _loss = GlrmLoss.Quadratic;          // Default loss function for numeric cols
    public GlrmLoss _multi_loss = GlrmLoss.Categorical;  // Default loss function for categorical cols
    public int _period = 1;                      // Length of the period when _loss = Periodic
    public GlrmLoss[] _loss_by_col;                  // Override default loss function for specific columns
    public int[] _loss_by_col_idx;

    // Regularization functions
    public Regularizer _regularization_x = Regularizer.None;   // Regularization function for X matrix
    public Regularizer _regularization_y = Regularizer.None;   // Regularization function for Y matrix
    public double _gamma_x = 0;                   // Regularization weight on X matrix
    public double _gamma_y = 0;                   // Regularization weight on Y matrix

    // Optional parameters
    public int _max_iterations = 1000;            // Max iterations
    public int _max_updates = 2*_max_iterations;  // Max number of updates (X or Y)
    public double _init_step_size = 1.0;          // Initial step size (decrease until we hit min_step_size)
    public double _min_step_size = 1e-4;          // Min step size

    // public Key<Frame> _representation_key;     // Key to save X matrix
    public String _representation_name;
    public boolean _recover_svd = false;          // Recover singular values and eigenvectors of XY at the end?
    public boolean _impute_original = false;      // Reconstruct original training data by reversing _transform?
    public boolean _verbose = true;               // Log when objective increases each iteration?

    //----- Enums ------------------------------------------------------------------------------------------------------

    // Non-negative matrix factorization (NNMF): r_x = r_y = NonNegative
    // Orthogonal NNMF: r_x = OneSparse, r_y = NonNegative
    // K-means clustering: r_x = UnitOneSparse, r_y = 0 (\gamma_y = 0)
    // Quadratic mixture: r_x = Simplex, r_y = 0 (\gamma_y = 0)
    public enum Regularizer {
      None, Quadratic, L2, L1, NonNegative, OneSparse, UnitOneSparse, Simplex
    }

    //----- Helper functions -------------------------------------------------------------------------------------------

    // Check if all elements of _loss_by_col are equal to a specific loss function
    private boolean allLossEquals(GlrmLoss loss) {
      if (_loss_by_col == null)
        return _loss == loss;

      for (GlrmLoss a_loss_by_col : _loss_by_col) {
        if (a_loss_by_col != loss)
          return false;
      }
      Frame train = _train.get();
      return train != null && ((train.numCols() == _loss_by_col.length) || (_loss == loss));
    }

    public final boolean hasClosedForm(long na_cnt) {
      return (na_cnt == 0) && allLossEquals(Quadratic) &&
          (_gamma_x == 0 || _regularization_x == Regularizer.None || _regularization_x == Regularizer.Quadratic) &&
          (_gamma_y == 0 || _regularization_y == Regularizer.None || _regularization_y == Regularizer.Quadratic);
    }

    // r_i(x_i), r_j(y_j): Regularization function for single row x_i or column y_j
    public final double regularize_x(double[] u) { return regularize(u, _regularization_x); }
    public final double regularize_y(double[] u) { return regularize(u, _regularization_y); }
    public final double regularize(double[] u, Regularizer regularization) {
      if(u == null) return 0;
      double ureg = 0;

      switch(regularization) {
        case None:
          return 0;
        case Quadratic:
          for (double anU : u) ureg += anU * anU;
          return ureg;
        case L2:
          for (double anU : u) ureg += anU * anU;
          return Math.sqrt(ureg);
        case L1:
          for (double anU : u) ureg += Math.abs(anU);
          return ureg;
        case NonNegative:
          for (double anU : u) {
            if (anU < 0) return Double.POSITIVE_INFINITY;
          }
          return 0;
        case OneSparse:
          int card = 0;
          for (double anU : u) {
            if (anU < 0) return Double.POSITIVE_INFINITY;
            else if (anU > 0) card++;
          }
          return card == 1 ? 0 : Double.POSITIVE_INFINITY;
        case UnitOneSparse:
          int ones = 0, zeros = 0;
          for (double anU : u) {
            if (anU == 1) ones++;
            else if (anU == 0) zeros++;
            else return Double.POSITIVE_INFINITY;
          }
          return ones == 1 && zeros == u.length-1 ? 0 : Double.POSITIVE_INFINITY;
        case Simplex:
          double sum = 0, absum = 0;
          for (double anU : u) {
            if (anU < 0) return Double.POSITIVE_INFINITY;
            else {
              sum += anU;
              absum += Math.abs(anU);
            }
          }
          return MathUtils.equalsWithinRecSumErr(sum, 1.0, u.length, absum) ? 0 : Double.POSITIVE_INFINITY;
        default:
          throw new RuntimeException("Unknown regularization function " + regularization);
      }
    }

    // \sum_i r_i(x_i): Sum of regularization function for all entries of X
    // public final double regularize_x(double[][] u) { return regularize(u, _regularization_x); }
    public final double regularize_y(double[][] u) { return regularize(u, _regularization_y); }
    public final double regularize(double[][] u, Regularizer regularization) {
      if(u == null || regularization == Regularizer.None) return 0;

      double ureg = 0;
      for (double[] anU : u) {
        ureg += regularize(anU, regularization);
        if (Double.isInfinite(ureg)) return ureg;
      }
      return ureg;
    }

    // \prox_{\alpha_k*r}(u): Proximal gradient of (step size) * (regularization function) evaluated at vector u
    public final double[] rproxgrad_x(double[] u, double alpha, Random rand) {
      return rproxgrad(u, alpha, _gamma_x, _regularization_x, rand);
    }
    public final double[] rproxgrad_y(double[] u, double alpha, Random rand) {
      return rproxgrad(u, alpha, _gamma_y, _regularization_y, rand);
    }
    static double[] rproxgrad(double[] u, double alpha, double gamma, Regularizer regularization, Random rand) {
      if (u == null || alpha == 0 || gamma == 0) return u;
      double[] v = new double[u.length];

      switch(regularization) {
        case None:
          return u;
        case Quadratic:
          for (int i = 0; i < u.length; i++)
            v[i] = u[i]/(1+2*alpha*gamma);
          return v;
        case L2:
          // Proof uses Moreau decomposition; see section 6.5.1 of Parikh and Boyd https://web.stanford.edu/~boyd/papers/pdf/prox_algs.pdf
          double weight = 1 - alpha*gamma/ArrayUtils.l2norm(u);
          if (weight < 0) return v;   // Zero vector
          for (int i = 0; i < u.length; i++)
            v[i] = weight * u[i];
          return v;
        case L1:
          for (int i = 0; i < u.length; i++)
            v[i] = Math.max(u[i]-alpha*gamma,0) + Math.min(u[i]+alpha*gamma,0);
          return v;
        case NonNegative:
          for (int i = 0; i < u.length; i++)
            v[i] = Math.max(u[i],0);
          return v;
        case OneSparse:
          int idx = ArrayUtils.maxIndex(u, rand);
          v[idx] = u[idx] > 0 ? u[idx] : 1e-6;
          return v;
        case UnitOneSparse:
          idx = ArrayUtils.maxIndex(u, rand);
          v[idx] = 1;
          return v;
        case Simplex:
          // Proximal gradient algorithm by Chen and Ye in http://arxiv.org/pdf/1101.6081v2.pdf
          // 1) Sort input vector u in ascending order: u[1] <= ... <= u[n]
          int n = u.length;
          int[] idxs = new int[n];
          for(int i = 0; i < n; i++) idxs[i] = i;
          ArrayUtils.sort(idxs, u);

          // 2) Calculate cumulative sum of u in descending order
          // cumsum(u) = (..., u[n-2]+u[n-1]+u[n], u[n-1]+u[n], u[n])
          double[] ucsum = new double[n];
          ucsum[n-1] = u[idxs[n-1]];
          for(int i = n-2; i >= 0; i--)
            ucsum[i] = ucsum[i+1] + u[idxs[i]];

          // 3) Let t_i = (\sum_{j=i+1}^n u[j] - 1)/(n - i)
          // For i = n-1,...,1, set optimal t* to first t_i >= u[i]
          double t = (ucsum[0] - 1)/n;    // Default t* = (\sum_{j=1}^n u[j] - 1)/n
          for (int i = n-1; i >= 1; i--) {
            double tmp = (ucsum[i] - 1)/(n - i);
            if(tmp >= u[idxs[i-1]]) {
              t = tmp; break;
            }
          }

          // 4) Return max(u - t*, 0) as projection of u onto simplex
          double[] x = new double[u.length];
          for (int i = 0; i < u.length; i++)
            x[i] = Math.max(u[i] - t, 0);
          return x;
        default:
          throw new RuntimeException("Unknown regularization function " + regularization);
      }
    }

    // Project X,Y matrices into appropriate subspace so regularizer is finite. Used during initialization.
    public final double[] project_x(double[] u, Random rand) { return project(u, _regularization_x, rand); }
    public final double[] project_y(double[] u, Random rand) { return project(u, _regularization_y, rand); }
    public final double[] project(double[] u, Regularizer regularization, Random rand) {
      if (u == null) return null;

      switch(regularization) {
        // Domain is all real numbers
        case None:
        case Quadratic:
        case L2:
        case L1:
          return u;
        // Proximal operator of indicator function for a set C is (Euclidean) projection onto C
        case NonNegative:
        case OneSparse:
        case UnitOneSparse:
          return rproxgrad(u, 1, 1, regularization, rand);
        case Simplex:
          double reg = regularize(u, regularization);   // Check if inside simplex before projecting since algo is complicated
          if (reg == 0) return u;
          return rproxgrad(u, 1, 1, regularization, rand);
        default:
          throw new RuntimeException("Unknown regularization function " + regularization);
      }
    }

  }


  //--------------------------------------------------------------------------------------------------------------------
  // Outputs
  //--------------------------------------------------------------------------------------------------------------------

  public static class GLRMOutput extends Model.Output {
    // Number of iterations executed
    public int _iterations;

    // Number of updates executed
    public int _updates;

    // Current value of the objective function
    public double _objective;

    // Current value of step_size used
    public double _step_size;

    // Average change in objective function this iteration
    public double _avg_change_obj;
    public double[/*iterations*/] _history_objective = new double[0];

    // Mapping from lower dimensional k-space to training features (Y)
    public TwoDimTable _archetypes;
    public GLRM.Archetypes _archetypes_raw;   // Needed for indexing into Y for scoring

    // Step size each iteration
    public double[/*iterations*/] _history_step_size = new double[0];

    // SVD of output XY
    public double[/*feature*/][/*k*/] _eigenvectors_raw;
    public TwoDimTable _eigenvectors;
    public double[] _singular_vals;

    // Frame key of X matrix
    public String _representation_name;
    public Key<Frame> _representation_key;
    public Key<? extends Model> _init_key;

    // Number of categorical and numeric columns
    public int _ncats;
    public int _nnums;

    // Number of good rows in training frame (not skipped)
    public long _nobs;

    // Categorical offset vector
    public int[] _catOffsets;

    // Standardization arrays for numeric data columns
    public double[] _normSub;   // Mean of each numeric column
    public double[] _normMul;   // One over standard deviation of each numeric column

    // Permutation array mapping adapted to original training col indices
    public int[] _permutation;  // _permutation[i] = j means col i in _adaptedFrame maps to col j of _train

    // Expanded column names of adapted training frame
    public String[] _names_expanded;

    // Loss function for every column in adapted training frame
    public GlrmLoss[] _lossFunc;

    // Training time
    public long[/*iterations*/] _training_time_ms = new long[0];

    public GLRMOutput(GLRM b) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for GLRM all the columns are features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.DimReduction;
    }
  }




  public GLRMModel(Key<GLRMModel> selfKey, GLRMParameters parms, GLRMOutput output) {
    super(selfKey, parms, output);
  }

  @Override protected Futures remove_impl( Futures fs ) {
    if (_output._init_key != null)
      _output._init_key.remove(fs);
    if (_output._representation_key != null)
      _output._representation_key.remove(fs);
    return super.remove_impl(fs);
  }

  /** Write out K/V pairs */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    ab.putKey(_output._init_key);
    ab.putKey(_output._representation_key);
    return super.writeAll_impl(ab);
  }

  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    ab.getKey(_output._init_key,fs);
    ab.getKey(_output._representation_key,fs);
    return super.readAll_impl(ab,fs);
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Scoring
  //--------------------------------------------------------------------------------------------------------------------

  // GLRM scoring is data imputation based on feature domains using reconstructed XY (see Udell (2015), Section 5.3)
  private Frame reconstruct(Frame orig, Frame adaptedFr, Key<Frame> destination_key, boolean save_imputed, boolean reverse_transform) {
    final int ncols = _output._names.length;
    assert ncols == adaptedFr.numCols();
    String prefix = "reconstr_";

    // Need [A,X,P] where A = adaptedFr, X = loading frame, P = imputed frame
    // Note: A is adapted to original training frame, P has columns shuffled so cats come before nums!
    Frame fullFrm = new Frame(adaptedFr);
    Frame loadingFrm = DKV.get(_output._representation_key).get();
    fullFrm.add(loadingFrm);
    String[][] adaptedDomme = adaptedFr.domains();
    Vec anyVec = fullFrm.anyVec();
    assert anyVec != null;
    for (int i = 0; i < ncols; i++) {
      Vec v = anyVec.makeZero();
      v.setDomain(adaptedDomme[i]);
      fullFrm.add(prefix + _output._names[i], v);
    }
    GLRMScore gs = new GLRMScore(ncols, _parms._k, save_imputed, reverse_transform).doAll(fullFrm);

    // Return the imputed training frame
    int x = ncols + _parms._k, y = fullFrm.numCols();
    Frame f = fullFrm.extractFrame(x, y);  // this will call vec_impl() and we cannot call the delete() below just yet

    f = new Frame((destination_key == null ? Key.<Frame>make() : destination_key), f.names(), f.vecs());
    DKV.put(f);
    gs._mb.makeModelMetrics(GLRMModel.this, orig, null, null);   // save error metrics based on imputed data
    return f;
  }

  @Override protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, Job j, boolean computeMetrics) {
    return reconstruct(orig, adaptedFr, Key.<Frame>make(destination_key), true, _parms._impute_original);
  }

  @Override public Frame scoreReconstruction(Frame frame, Key<Frame> destination_key, boolean reverse_transform) {
    Frame adaptedFr = new Frame(frame);
    adaptTestForTrain(adaptedFr, true, false);
    return reconstruct(frame, adaptedFr, destination_key, true, reverse_transform);
  }

  /**
   * Project each archetype into original feature space
   * @param frame Original training data with m rows and n columns
   * @param destination_key Frame Id for output
   * @return Frame containing k rows and n columns, where each row corresponds to an archetype
   */
  @Override public Frame scoreArchetypes(Frame frame, Key destination_key, boolean reverse_transform) {
    final int ncols = _output._names.length;
    Frame adaptedFr = new Frame(frame);
    adaptTestForTrain(adaptedFr, true, false);
    assert ncols == adaptedFr.numCols();
    String[][] adaptedDomme = adaptedFr.domains();
    double[][] proj = new double[_parms._k][_output._nnums + _output._ncats];

    // Categorical columns
    for (int d = 0; d < _output._ncats; d++) {
      double[][] block = _output._archetypes_raw.getCatBlock(d);
      for (int k = 0; k < _parms._k; k++)
        proj[k][_output._permutation[d]] = _output._lossFunc[d].mimpute(block[k]);
    }

    // Numeric columns
    for (int d = _output._ncats; d < (_output._ncats + _output._nnums); d++) {
      int ds = d - _output._ncats;
      for (int k = 0; k < _parms._k; k++) {
        double num = _output._archetypes_raw.getNum(ds, k);
        proj[k][_output._permutation[d]] = _output._lossFunc[d].impute(num);
        if (reverse_transform)
          proj[k][_output._permutation[d]] = proj[k][_output._permutation[d]] / _output._normMul[ds] + _output._normSub[ds];
      }
    }

    // Convert projection of archetypes into a frame with correct domains
    Frame f = ArrayUtils.frame((null == destination_key ? Key.make() : destination_key), adaptedFr.names(), proj);
    for(int i = 0; i < ncols; i++) f.vec(i).setDomain(adaptedDomme[i]);
    return f;
  }

  private class GLRMScore extends MRTask<GLRMScore> {
    final int _ncolA;   // Number of cols in original data A
    final int _ncolX;   // Number of cols in X (rank k)
    final boolean _save_imputed;  // Save imputed data into new vecs?
    final boolean _reverse_transform;   // Reconstruct original training data by reversing transform?
    ModelMetrics.MetricBuilder _mb;

    GLRMScore( int ncolA, int ncolX, boolean save_imputed ) {
      this(ncolA, ncolX, save_imputed, _parms._impute_original);
    }

    GLRMScore( int ncolA, int ncolX, boolean save_imputed, boolean reverse_transform ) {
      _ncolA = ncolA; _ncolX = ncolX;
      _save_imputed = save_imputed;
      _reverse_transform = reverse_transform;
    }

    @Override public void map( Chunk chks[] ) {
      float atmp [] = new float[_ncolA];
      double xtmp [] = new double[_ncolX];
      double preds[] = new double[_ncolA];
      _mb = GLRMModel.this.makeMetricBuilder(null);

      if (_save_imputed) {
        for (int row = 0; row < chks[0]._len; row++) {
          double p[] = impute_data(chks, row, xtmp, preds);
          compute_metrics(chks, row, atmp, p);
          for (int c = 0; c < preds.length; c++)
            chks[_ncolA + _ncolX + c].set(row, p[c]);
        }
      } else {
        for (int row = 0; row < chks[0]._len; row++) {
          double p[] = impute_data(chks, row, xtmp, preds);
          compute_metrics(chks, row, atmp, p);
        }
      }
    }

    @Override public void reduce(GLRMScore other) {
      // FIXME: IntelliJ raises a valid warning here; need to adjust the definition of _mb
      if (_mb != null) _mb.reduce(other._mb);
    }

    @Override protected void postGlobal() {
      if (_mb != null) _mb.postGlobal();
    }

    private float[] compute_metrics(Chunk[] chks, int row_in_chunk, float[] tmp, double[] preds) {
      for (int i = 0; i < tmp.length; i++)
        tmp[i] = (float)chks[i].atd(row_in_chunk);
      _mb.perRow(preds, tmp, GLRMModel.this);
      return tmp;
    }

    private double[] impute_data(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds) {
      for (int i = 0; i < tmp.length; i++ )
        tmp[i] = chks[_ncolA+i].atd(row_in_chunk);
      impute_data(tmp, preds);
      return preds;
    }

    private double[] impute_data(double[] tmp, double[] preds) {
      assert preds.length == _output._nnums + _output._ncats;

      // Categorical columns
      for (int d = 0; d < _output._ncats; d++) {
        double[] xyblock = _output._archetypes_raw.lmulCatBlock(tmp,d);
        preds[_output._permutation[d]] = _output._lossFunc[d].mimpute(xyblock);
      }

      // Numeric columns
      for (int d = _output._ncats; d < preds.length; d++) {
        int ds = d - _output._ncats;
        double xy = _output._archetypes_raw.lmulNumCol(tmp, ds);
        preds[_output._permutation[d]] = _output._lossFunc[d].impute(xy);
        if (_reverse_transform)
          preds[_output._permutation[d]] = preds[_output._permutation[d]] / _output._normMul[ds] + _output._normSub[ds];
      }
      return preds;
    }
  }

  @Override protected double[] score0(double[] data, double[] preds) {
    throw H2O.unimpl();
  }

  public ModelMetricsGLRM scoreMetricsOnly(Frame frame) {
    final int ncols = _output._names.length;

    // Need [A,X] where A = adapted test frame, X = loading frame
    // Note: A is adapted to original training frame
    Frame adaptedFr = new Frame(frame);
    adaptTestForTrain(adaptedFr, true, false);
    assert ncols == adaptedFr.numCols();

    // Append loading frame X for calculating XY
    Frame fullFrm = new Frame(adaptedFr);
    Frame loadingFrm = DKV.get(_output._representation_key).get();
    fullFrm.add(loadingFrm);

    GLRMScore gs = new GLRMScore(ncols, _parms._k, false).doAll(fullFrm);
    ModelMetrics mm = gs._mb.makeModelMetrics(GLRMModel.this, adaptedFr, null, null);   // save error metrics based on imputed data
    return (ModelMetricsGLRM) mm;
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsGLRM.GLRMModelMetrics(_parms._k, _output._permutation, _parms._impute_original);
  }
}

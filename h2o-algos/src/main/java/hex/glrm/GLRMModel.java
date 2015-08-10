package hex.glrm;

import hex.*;
import water.DKV;
import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.TwoDimTable;
import water.util.MathUtils;

import java.util.Random;

public class GLRMModel extends Model<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {

  public static class GLRMParameters extends Model.Parameters {
    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE; // Data transformation (demean to compare with PCA)
    public int _k = 1;                            // Rank of resulting XY matrix
    public Loss _loss = Loss.L2;                  // Loss function for numeric cols
    public MultiLoss _multi_loss = MultiLoss.Categorical;  // Loss function for categorical cols
    public int _period = 1;                       // Length of the period when _loss = Periodic
    public Regularizer _regularization_x = Regularizer.None;   // Regularization function for X matrix
    public Regularizer _regularization_y = Regularizer.None;   // Regularization function for Y matrix
    public double _gamma_x = 0;                   // Regularization weight on X matrix
    public double _gamma_y = 0;                   // Regularization weight on Y matrix
    public int _max_iterations = 1000;            // Max iterations
    public double _init_step_size = 1.0;          // Initial step size (decrease until we hit min_step_size)
    public double _min_step_size = 1e-4;          // Min step size
    public long _seed = System.nanoTime();        // RNG seed
    public GLRM.Initialization _init = GLRM.Initialization.PlusPlus;  // Initialization of Y matrix
    public Key<Frame> _user_points;               // User-specified Y matrix (for _init = User)
    public Key<Frame> _loading_key;               // Key to save X matrix
    public boolean _recover_svd = false;          // Recover singular values and eigenvectors of XY at the end?
    public boolean _verbose = true;               // Log when objective increases each iteration?

    public enum Loss {
      L2, L1, Huber, Poisson, Hinge, Logistic, Periodic
    }

    public enum MultiLoss {
      Categorical, Ordinal
    }

    // Non-negative matrix factorization (NNMF): r_x = r_y = NonNegative
    // Orthogonal NNMF: r_x = OneSparse, r_y = NonNegative
    // K-means clustering: r_x = UnitOneSparse, r_y = 0 (\gamma_y = 0)
    // Quadratic mixture: r_x = Simplex, r_y = 0 (\gamma_y = 0)
    public enum Regularizer {
      None, L2, L1, NonNegative, OneSparse, UnitOneSparse, Simplex
    }

    public final boolean hasClosedForm() {
      return (_loss == GLRMParameters.Loss.L2 && (_gamma_x == 0 || _regularization_x == Regularizer.None || _regularization_x == GLRMParameters.Regularizer.L2)
              && (_gamma_y == 0 || _regularization_y == Regularizer.None || _regularization_y == GLRMParameters.Regularizer.L2));
    }

    // L(u,a): Loss function
    public final double loss(double u, double a) {
      switch(_loss) {
        case L2:
          return (u-a)*(u-a);
        case L1:
          return Math.abs(u - a);
        case Huber:
          return Math.abs(u-a) <= 1 ? 0.5*(u-a)*(u-a) : Math.abs(u-a)-0.5;
        case Poisson:
          return Math.exp(u) - a*u + a*Math.log(a) - a;
        case Hinge:
          return Math.max(1-a*u,0);
        case Logistic:
          return Math.log(1 + Math.exp(-a * u));
        case Periodic:
          return 1-Math.cos((a-u)*(2*Math.PI)/_period);
        default:
          throw new RuntimeException("Unknown loss function " + _loss);
      }
    }

    // \grad_u L(u,a): Gradient of loss function with respect to u
    public final double lgrad(double u, double a) {
      switch(_loss) {
        case L2:
          return 2*(u-a);
        case L1:
          return Math.signum(u - a);
        case Huber:
          return Math.abs(u-a) <= 1 ? u-a : Math.signum(u-a);
        case Poisson:
          return Math.exp(u)-a;
        case Hinge:
          return a*u <= 1 ? -a : 0;
        case Logistic:
          return -a/(1+Math.exp(a*u));
        case Periodic:
          return ((2*Math.PI)/_period) * Math.sin((a - u) * (2 * Math.PI) / _period);
        default:
          throw new RuntimeException("Unknown loss function " + _loss);
      }
    }

    // L(u,a): Multidimensional loss function
    public final double mloss(double[] u, int a) {
      if(a < 0 || a > u.length-1)
        throw new IllegalArgumentException("Index must be between 0 and " + String.valueOf(u.length-1));

      double sum = 0;
      switch(_multi_loss) {
        case Categorical:
          for (int i = 0; i < u.length; i++) sum += Math.max(1 + u[i], 0);
          sum += Math.max(1 - u[a], 0) - Math.max(1 + u[a], 0);
          return sum;
        case Ordinal:
          for (int i = 0; i < u.length-1; i++) sum += Math.max(a>i ? 1-u[i]:1, 0);
          return sum;
        default:
          throw new RuntimeException("Unknown multidimensional loss function " + _multi_loss);
      }
    }

    // \grad_u L(u,a): Gradient of multidimensional loss function with respect to u
    public final double[] mlgrad(double[] u, int a) {
      if(a < 0 || a > u.length-1)
        throw new IllegalArgumentException("Index must be between 0 and " + String.valueOf(u.length-1));

      double[] grad = new double[u.length];
      switch(_multi_loss) {
        case Categorical:
          for (int i = 0; i < u.length; i++) grad[i] = (1+u[i] > 0) ? 1:0;
          grad[a] = (1-u[a] > 0) ? -1:0;
          return grad;
        case Ordinal:
          for (int i = 0; i < u.length-1; i++) grad[i] = (a>i && 1-u[i] > 0) ? -1:0;
          return grad;
        default:
          throw new RuntimeException("Unknown multidimensional loss function " + _multi_loss);
      }
    }

    // r_i(x_i): Regularization function for single row x_i
    public final double regularize_x(double[] u) { return regularize(u, _regularization_x); }
    public final double regularize_y(double[] u) { return regularize(u, _regularization_y); }
    public final double regularize(double[] u, Regularizer regularization) {
      if(u == null) return 0;
      double ureg = 0;

      switch(regularization) {
        case None:
          return 0;
        case L2:
          for(int i = 0; i < u.length; i++)
            ureg += u[i] * u[i];
          return ureg;
        case L1:
          for(int i = 0; i < u.length; i++)
            ureg += Math.abs(u[i]);
          return ureg;
        case NonNegative:
          for(int i = 0; i < u.length; i++) {
            if(u[i] < 0) return Double.POSITIVE_INFINITY;
          }
          return 0;
        case OneSparse:
          int card = 0;
          for(int i = 0; i < u.length; i++) {
            if(u[i] < 0) return Double.POSITIVE_INFINITY;
            else if(u[i] > 0) card++;
          }
          return card == 1 ? 0 : Double.POSITIVE_INFINITY;
        case UnitOneSparse:
          int ones = 0, zeros = 0;
          for(int i = 0; i < u.length; i++) {
            if(u[i] == 1) ones++;
            else if(u[i] == 0) zeros++;
            else return Double.POSITIVE_INFINITY;
          }
          return ones == 1 && zeros == u.length-1 ? 0 : Double.POSITIVE_INFINITY;
        case Simplex:
          double sum = 0;
          for(int i = 0; i < u.length; i++) {
            if(u[i] < 0) return Double.POSITIVE_INFINITY;
            else sum += u[i];
          }
          return MathUtils.equalsWithinOneSmallUlp(sum, 1) ? 0 : Double.POSITIVE_INFINITY;
        default:
          throw new RuntimeException("Unknown regularization function " + regularization);
      }
    }

    // \sum_i r_i(x_i): Sum of regularization function for all entries of X
    public final double regularize_x(double[][] u) { return regularize(u, _regularization_x); }
    public final double regularize_y(double[][] u) { return regularize(u, _regularization_y); }
    public final double regularize(double[][] u, Regularizer regularization) {
      if(u == null || regularization == Regularizer.None) return 0;

      double ureg = 0;
      for(int i = 0; i < u.length; i++) {
        ureg += regularize(u[i], regularization);
        if(Double.isInfinite(ureg)) return ureg;
      }
      return ureg;
    }

    // \prox_{\alpha_k*r}(u): Proximal gradient of (step size) * (regularization function) evaluated at vector u
    public final double[] rproxgrad_x(double[] u, double alpha, Random rand) { return rproxgrad(u, alpha, _gamma_x, _regularization_x, rand); }
    public final double[] rproxgrad_y(double[] u, double alpha, Random rand) { return rproxgrad(u, alpha, _gamma_y, _regularization_y, rand); }
    // public final double[] rproxgrad_x(double[] u, double alpha) { return rproxgrad(u, alpha, _gamma_x, _regularization_x, RandomUtils.getRNG(_seed)); }
    // public final double[] rproxgrad_y(double[] u, double alpha) { return rproxgrad(u, alpha, _gamma_y, _regularization_y, RandomUtils.getRNG(_seed)); }
    public final double[] rproxgrad(double[] u, double alpha, double gamma, Regularizer regularization, Random rand) {
      if(u == null || alpha == 0 || gamma == 0) return u;
      double[] v = new double[u.length];

      switch(regularization) {
        case None:
          return u;
        case L2:
          for(int i = 0; i < u.length; i++)
            v[i] = u[i]/(1+2*alpha*gamma);
          return v;
        case L1:
          for(int i = 0; i < u.length; i++)
            v[i] = Math.max(u[i]-alpha*gamma,0) + Math.min(u[i]+alpha*gamma,0);
          return v;
        case NonNegative:
          for(int i = 0; i < u.length; i++)
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
          for(int i = n-1; i >= 1; i--) {
            double tmp = (ucsum[i] - 1)/(n - i);
            if(tmp >= u[idxs[i-1]]) {
              t = tmp; break;
            }
          }

          // 4) Return max(u - t*, 0) as projection of u onto simplex
          double[] x = new double[u.length];
          for(int i = 0; i < u.length; i++)
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
      if(u == null) return u;

      switch(regularization) {
        // Domain is all real numbers
        case None:
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

    // \hat A_{i,j} = \argmin_a L_{i,j}(x_iy_j, a): Data imputation for real numeric values
    public final double impute(double u) {
      switch(_loss) {
        case L2:
        case L1:
        case Huber:
        case Periodic:
          return u;
        case Poisson:
          return Math.exp(u)-1;
        case Hinge:
          return 1/u;
        case Logistic:
          if (u == 0) return 0;   // Any finite real number is minimizer if u = 0
          return (u > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
        default:
          throw new RuntimeException("Unknown loss function " + _loss);
      }
    }

    // \hat A_{i,j} = \argmin_a L_{i,j}(x_iy_j, a): Data imputation for categorical values {0,1,2,...}
    // TODO: Is there a faster way to find the loss minimizer?
    public final int mimpute(double[] u) {
      switch(_multi_loss) {
        case Categorical:
        case Ordinal:
          double[] cand = new double[u.length];
          for (int a = 0; a < cand.length; a++)
            cand[a] = mloss(u, a);
          return ArrayUtils.minIndex(cand);
        default:
          throw new RuntimeException("Unknown multidimensional loss function " + _multi_loss);
      }
    }
  }

  public static class GLRMOutput extends Model.Output {
    // Iterations executed
    public int _iterations;

    // Current value of objective function
    public double _objective;

    // Average change in objective function this iteration
    public double _avg_change_obj;

    // Mapping from lower dimensional k-space to training features (Y)
    public TwoDimTable _archetypes;
    public GLRM.Archetypes _archetypes_raw;   // Needed for indexing into Y for scoring

    // Final step size
    public double _step_size;

    // SVD of output XY
    public double[/*feature*/][/*k*/] _eigenvectors;
    public double[] _singular_vals;

    // Frame key of X matrix
    public Key<Frame> _loading_key;

    // Number of categorical and numeric columns
    public int _ncats;
    public int _nnums;

    // Number of good rows in training frame (not skipped)
    public long _nobs;

    // Categorical offset vector
    public int[] _catOffsets;

    // If standardized, mean of each numeric data column
    public double[] _normSub;

    // If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

    // Permutation matrix mapping training col indices to adaptedFrame
    public int[] _permutation;

    // Expanded column names of adapted training frame
    public String[] _names_expanded;

    public GLRMOutput(GLRM b) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for GLRM all the columns are features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.DimReduction;
    }
  }

  public GLRMModel(Key selfKey, GLRMParameters parms, GLRMOutput output) { super(selfKey, parms, output); }

  // GLRM scoring is data imputation based on feature domains using reconstructed XY (see Udell (2015), Section 5.3)
  @Override protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key) {
    final int ncols = _output._names.length;
    assert ncols == adaptedFr.numCols();
    String prefix = "reconstr_";

    // Need [A,X,P] where A = adaptedFr, X = loading frame, P = imputed frame
    // Note: A is adapted to original training frame, P has columns shuffled so cats come before nums!
    Frame fullFrm = new Frame(adaptedFr);
    Frame loadingFrm = DKV.get(_output._loading_key).get();
    fullFrm.add(loadingFrm);
    String[][] adaptedDomme = adaptedFr.domains();
    for(int i = 0; i < ncols; i++) {
      Vec v = fullFrm.anyVec().makeZero();
      v.setDomain(adaptedDomme[i]);
      fullFrm.add(prefix + _output._names[i], v);
    }
    GLRMScore gs = new GLRMScore(ncols, _parms._k, true).doAll(fullFrm);

    // Return the imputed training frame
    int x = ncols + _parms._k, y = fullFrm.numCols();
    Frame f = fullFrm.extractFrame(x, y);  // this will call vec_impl() and we cannot call the delete() below just yet

    f = new Frame((null == destination_key ? Key.make() : Key.make(destination_key)), f.names(), f.vecs());
    DKV.put(f);
    gs._mb.makeModelMetrics(GLRMModel.this, adaptedFr);   // save error metrics based on imputed data
    return f;
  }

  private class GLRMScore extends MRTask<GLRMScore> {
    final int _ncolA;   // Number of cols in original data A
    final int _ncolX;   // Number of cols in X (rank k)
    final boolean _save_imputed;  // Save imputed data into new vecs?
    ModelMetrics.MetricBuilder _mb;

    GLRMScore( int ncolA, int ncolX, boolean save_imputed ) {
      _ncolA = ncolA; _ncolX = ncolX; _save_imputed = save_imputed;
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

    @Override public void reduce( GLRMScore other ) { if(_mb != null) _mb.reduce(other._mb); }

    @Override protected void postGlobal() { if(_mb != null) _mb.postGlobal(); }

    private float[] compute_metrics(Chunk[] chks, int row_in_chunk, float[] tmp, double[] preds) {
      for( int i=0; i<tmp.length; i++)
        tmp[i] = (float)chks[i].atd(row_in_chunk);
      _mb.perRow(preds, tmp, GLRMModel.this);
      return tmp;
    }

    private double[] impute_data(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds) {
      for( int i=0; i<tmp.length; i++ )
        tmp[i] = chks[_ncolA+i].atd(row_in_chunk);
      impute_data(tmp, preds);
      return preds;
    }

    private double[] impute_data(double[] tmp, double[] preds) {
      assert preds.length == _output._nnums + _output._ncats;

      // Categorical columns
      for (int d = 0; d < _output._ncats; d++) {
        double[] xyblock = _output._archetypes_raw.lmulCatBlock(tmp,d);
        preds[_output._permutation[d]] = _parms.mimpute(xyblock);
      }

      // Numeric columns
      for (int d = _output._ncats; d < preds.length; d++) {
        int ds = d - _output._ncats;
        double xy = _output._archetypes_raw.lmulNumCol(tmp, ds);
        preds[_output._permutation[d]] = _parms.impute(xy);
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
    Frame loadingFrm = DKV.get(_output._loading_key).get();
    fullFrm.add(loadingFrm);

    GLRMScore gs = new GLRMScore(ncols, _parms._k, false).doAll(fullFrm);
    ModelMetrics mm = gs._mb.makeModelMetrics(GLRMModel.this, adaptedFr);   // save error metrics based on imputed data
    return (ModelMetricsGLRM) mm;
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsGLRM.GLRMModelMetrics(_parms._k, _output._permutation);
  }
}

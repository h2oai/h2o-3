package hex.glrm;

import Jama.CholeskyDecomposition;
import Jama.Matrix;
import hex.DataInfo;
import hex.glrm.GLRM.Archetypes;
import hex.glrm.GLRMModel.GLRMParameters;
import hex.glrm.GLRMModel.GLRMParameters.Loss;
import hex.kmeans.KMeansModel;
import hex.optimization.L_BFGS;
import hex.optimization.L_BFGS.*;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.RandomUtils;

import java.util.Random;

/**
 * All GLRM initialization related distributed tasks:
 *
 * InitialXProj   - initialize X to standard Gaussian random matrix projected into regularizer subspace
 * InitialXSVD    - given A = UDV', initialize X = UD, where U is n by k and D is a diagonal k by k matrix
 * InitialXKMeans - initialize X to matrix of indicator columns for cluster assignments, e.g. k = 4, cluster = 3 -> [0, 0, 1, 0]
 * CholMulTask    - solves XD = AY' for X where A is n by p, Y is k by p, D is k by k, and n >> p > k
 *                  resulting matrix X = (AY')D^(-1) will have dimensions n by k
 *
 * @author anqi_fu
 */
public abstract class GLRMInit {

  static class InitialXProj extends MRTask<InitialXProj> {
    GLRMParameters _parms;
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)

    InitialXProj(GLRMParameters parms, int ncolA, int ncolX) {
      _parms = parms;
      _ncolA = ncolA;
      _ncolX = ncolX;
    }

    @Override public void map( Chunk chks[] ) {
      Random rand = RandomUtils.getRNG(_parms._seed + chks[0].start());

      for(int row = 0; row < chks[0]._len; row++) {
        double[] xrow = ArrayUtils.gaussianVector(_ncolX, _parms._seed);
        xrow = _parms.project_x(xrow, rand);
        for(int c = 0; c < xrow.length; c++) {
          chks[_ncolA+c].set(row, xrow[c]);
          chks[_ncolA+_ncolX+c].set(row, xrow[c]);
        }
      }
    }
  }

  // Initialize X = UD, where U is n by k and D is a diagonal k by k matrix
  static class InitialXSVD extends MRTask<InitialXSVD> {
    final double[] _diag;   // Diagonal of D
    final int _ncolU;       // Number of cols in U = cols in X (k)
    final int _offX;        // Column offset to X matrix
    final int _offW;        // Column offset to W matrix

    InitialXSVD(double[] diag, int ncolA, int ncolU) {
      assert diag != null && diag.length == ncolU;
      _diag = diag;
      _ncolU = ncolU;
      _offX = ncolU + ncolA;    // Input frame is [U,A,X,W]
      _offW = _offX + ncolU;
    }

    @Override public void map(Chunk chks[]) {
      for(int row = 0; row < chks[0]._len; row++) {
        for(int c = 0; c < _ncolU; c++) {
          double ud = chks[c].atd(row) * _diag[c];
          chks[_offX+c].set(row, ud);
          chks[_offW+c].set(row, ud);
        }
      }
    }
  }

  // Initialize X to matrix of indicator columns for cluster assignments, e.g. k = 4, cluster = 3 -> [0, 0, 1, 0]
  static class InitialXKMeans extends MRTask<InitialXKMeans> {
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

    @Override public void map( Chunk chks[] ) {
      double[] tmp = new double[_ncolA];
      Random rand = RandomUtils.getRNG(_parms._seed + chks[0].start());

      for(int row = 0; row < chks[0]._len; row++) {
        // double[] preds = new double[_ncolX];
        // double[] p = _model.score_indicator(chks, row, tmp, preds);
        double[] p = _model.score_ratio(chks, row, tmp);
        p = _parms.project_x(p, rand);  // TODO: Should we restrict indicator cols to regularizer subspace?
        for(int c = 0; c < p.length; c++) {
          chks[_ncolA+c].set(row, p[c]);
          chks[_ncolA+_ncolX+c].set(row, p[c]);
        }
      }
    }
  }

  // Solves XD = AY' for X where A is n by p, Y is k by p, D is k by k, and n >> p > k
  // Resulting matrix X = (AY')D^(-1) will have dimensions n by k
  static class CholMulTask extends MRTask<CholMulTask> {
    GLRMParameters _parms;
    final Archetypes _yt;     // _yt = Y' (transpose of Y)
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final int _ncats;         // Number of categorical cols in training frame
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    CholeskyDecomposition _chol;   // Cholesky decomposition of D = D', since we solve D'X' = DX' = AY'

    CholMulTask(GLRMParameters parms, CholeskyDecomposition chol, Archetypes yt, int ncolA, int ncolX, int ncats, double[] normSub, double[] normMul) {
      assert yt != null && yt.rank() == ncolX;
      assert ncats <= ncolA;
      _parms = parms;
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

      for(int row = 0; row < cs[0]._len; row++) {
        // 1) Compute single row of AY'
        for (int k = 0; k < _ncolX; k++) {
          // Categorical columns
          double x = 0;
          for(int d = 0; d < _ncats; d++) {
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
        for(int d = _ncolA; d < _ncolA+_ncolX; d++) {
          cs[d].set(row, xrow[i]);
          cs[d+_ncolX].set(row, xrow[i++]);
        }
        assert i == xrow.length;
      }
    }
  }

  static final class LossOffsetSolver extends GradientSolver {
    final GLRMParameters _parms;
    final Frame _train;   // Training frame with columns shuffled by DataInfo
    final Loss _loss;
    final int _period;

    public LossOffsetSolver(GLRMParameters parms, Frame train, Loss loss, int period) {
      _parms = parms;
      _train = train;
      _loss = loss;
      _period = period;
    }

    @Override
    public GradientInfo getGradient(double[] beta) {
      LossGradCalc tsk = new LossGradCalc(_parms, _loss, _period, beta[0]).doAll(_train);

      final double[] g = new double[1];
      g[0] = tsk._lgrad;
      return new GradientInfo(tsk._lsum, g);
    }

    @Override
    public double[] getObjVals(double[] beta, double[] pk, int nSteps, double initialStep, double stepDec) {
      return new double[0];
    }

    // Compute \sum_{i,j} L(\mu, A_{i,j}) and its gradient at given \mu
    private static final class LossGradCalc extends MRTask<LossGradCalc> {
      final GLRMParameters _parms;
      final Loss _loss;
      final int _period;
      final double _mu;

      double _lsum;
      double _lgrad;

      LossGradCalc(GLRMParameters parms, Loss loss, int period, double mu) {
        _parms = parms;
        _loss = loss;
        _period = period;
        _mu = mu;
      }

      @Override public void map(Chunk cs) {
        _lsum = 0;
        _lgrad = 0;

        for(int row = 0; row < cs._len; row++) {
          double a = cs.atd(row);
          if(Double.isNaN(a)) continue;
          _lsum += _parms.loss(_mu, a, _loss, _period);
          _lgrad += _parms.lgrad(_mu, a, _loss);
        }
      }

      @Override public void reduce(LossGradCalc other) {
        _lsum += other._lsum;
        _lgrad += other._lgrad;
      }
    }
  }

  static final class MultiLossOffsetSolver extends GradientSolver {
    final GLRMParameters _parms;
    final Frame _train;   // Training frame with columns shuffled by DataInfo
    final Loss _loss;
    final int _period;

    public MultiLossOffsetSolver(GLRMParameters parms, Frame train, Loss loss, int period) {
      _parms = parms;
      _train = train;
      _loss = loss;
      _period = period;
    }

    @Override
    public GradientInfo getGradient(double[] beta) {
      MultiLossGradCalc tsk = new MultiLossGradCalc(_parms, _loss, _period, beta).doAll(_train);
      return new GradientInfo(tsk._lsum, tsk._lgrad);
    }

    @Override
    public double[] getObjVals(double[] beta, double[] pk, int nSteps, double initialStep, double stepDec) {
      return new double[0];
    }

    // Compute \sum_{i,j} L(\mu, A_{i,j}) and its gradient at given \mu
    private static final class MultiLossGradCalc extends MRTask<MultiLossGradCalc> {
      final GLRMParameters _parms;
      final Loss _loss;
      final int _period;
      final double[] _mu;

      double _lsum;
      double[] _lgrad;

      MultiLossGradCalc(GLRMParameters parms, Loss loss, int period, double[] mu) {
        _parms = parms;
        _loss = loss;
        _period = period;
        _mu = mu;
      }

      @Override public void map(Chunk cs) {
        _lsum = 0;
        _lgrad = new double[_mu.length];

        for(int row = 0; row < cs._len; row++) {
          double a = cs.atd(row);
          if(Double.isNaN(a)) continue;
          _lsum += _parms.mloss(_mu, (int)a, _loss);
          double[] grad = _parms.mlgrad(_mu, (int)a, _loss);
          ArrayUtils.add(_lgrad, grad);
        }
      }

      @Override public void reduce(MultiLossGradCalc other) {
        _lsum += other._lsum;
        ArrayUtils.add(_lgrad, other._lgrad);
      }
    }
  }

  // M-estimator for generalized mean of periodic loss: 1-cos((a-u)*(2*PI)/T);
  // Bucy and Mallinckrodt, equation 5.2 in http://www.tandfonline.com/doi/pdf/10.1080/17442507308833101
  static final class PeriodOffset extends MRTask<PeriodOffset> {
    final int _period;
    private double _num;
    private double _den;
    public double _offset;

    PeriodOffset(int period) {
      _period = period;
    }

    @Override public void map(Chunk cs) {
      _num = _den = 0;

      for(int row = 0; row < cs._len; row++) {
        double a = cs.atd(row);
        if(Double.isNaN(a)) continue;
        double inner = 2*Math.PI*a / _period;
        _num += Math.sin(inner);
        _den += Math.cos(inner);
      }
    }

    @Override public void reduce(PeriodOffset other) {
      _num += other._num;
      _den += other._den;
    }

    @Override protected void postGlobal() {
      _offset = (_period / (2*Math.PI)) * Math.atan(_num / _den) + _period/2.0;
    }
  }

  // \sigma_j = 1/(n_j-1) \sum L_{i,j}(\mu_j, A_{i,j}): M-estimator that generalizes variance of column j
  // n_j = number of non-missing elements in col j, \mu_j = generalized column mean from above
  static class LossScaleCalc extends MRTask<LossScaleCalc> {
    final int _ncats;
    final GLRMParameters.Loss[] _lossFunc;
    final int _period;
    final double[][] _offset;

    long[] _count;
    double[] _scale;

    LossScaleCalc(int ncats, GLRMParameters.Loss[] lossFunc, int period, double[][] offset) {
      _ncats = ncats;
      _lossFunc = lossFunc;
      _period = period;
      _offset = offset;
    }

    @Override public void map(Chunk[] cs) {
      _scale = new double[cs.length];
      _count = new long[cs.length];

      for(int row = 0; row < cs[0]._len; row++) {
        for(int col = 0; col < _ncats; col++) {
          double a = cs[col].atd(row);
          if(Double.isNaN(a)) continue;
          _scale[col] += GLRMParameters.mloss(_offset[col], (int) a, _lossFunc[col]);
          _count[col]++;
        }

        for (int col = _ncats; col < cs.length; col++) {
          double a = cs[col].atd(row);
          if(Double.isNaN(a)) continue;
          _scale[col] += GLRMParameters.loss(_offset[col][0], a, _lossFunc[col], _period);
          _count[col]++;
        }
      }
    }

    @Override public void reduce(LossScaleCalc other) {
      ArrayUtils.add(_scale, other._scale);
      ArrayUtils.add(_count, other._count);
    }

    @Override protected void postGlobal() {
      for(int i = 0; i < _scale.length; i++)
        // _scale[i] /= _count[i]-1;
        _scale[i] = _scale[i] != 0 ? (_count[i]-1) / _scale[i] : 1.0;   // Need reciprocal since dividing by generalized variance
    }
  }
}

package hex.hglm;

import Jama.Matrix;
import hex.DataInfo;
import water.Job;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Random;
import static hex.hglm.HGLMUtils.*;
import static water.util.ArrayUtils.copy2DArray;
import static water.util.ArrayUtils.gaussianVector;

public class ComputationStateHGLM {
  /***
   * the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
   * I will be referring to the doc and different parts of it to explain my implementation.
   */
  final int _numFixedCoeffs;   // fixed coefficient length including inactive predictors
  final int _numRandomCoeffs;  // random coefficient length including inactive predictors
  public final HGLMModel.HGLMParameters _parms;
  int _iter;
  private double[] _beta; // fixed, if standardized, normalized coefficients, else, non-normalized coefficients
  private double[][] _ubeta; // random , if standardized, normalized coefficients, else non-normalized coefficients
  private double[][] _T;  // positive definite matrix, size random coefficient length by random coefficient length
  final DataInfo _dinfo;
  private final Job _job;
  double _tauEVarE10 = 0; // variance estimate of random noise calculated from equation 10 of the doc
  double _tauEVarE17 = 0; // variance estimate of random noise calculated from equation 17 of the doc
  String[] _fixedCofficientNames; // include intercept if enabled
  String[] _randomCoefficientNames; // include intercept only if random effect is in intercept
  String[] _level2UnitNames; // enum levels of group column
  final int _numLevel2Unit;
  final int _level2UnitIndex;
  final int _nobs;
  
  public ComputationStateHGLM(Job job, HGLMModel.HGLMParameters parms, DataInfo dinfo, HGLMTask.ComputationEngineTask engTask, int iter) {
    _job = job;
    _parms = parms;
    _dinfo = dinfo;
    _iter = iter;
    _fixedCofficientNames = engTask._fixedCoeffNames;
    _level2UnitNames = engTask._level2UnitNames;
    _randomCoefficientNames = engTask._randomCoeffNames;
    _level2UnitIndex = engTask._level2UnitIndex;
    initComputationStateHGLM(engTask);
    _numFixedCoeffs = _beta.length;
    _numRandomCoeffs = _ubeta[0].length;
    _numLevel2Unit = _ubeta.length;
    _nobs = engTask._nobs;
  }

  /**
   * set initial values for:
   * 1. initial fixed coefficients from user or assigned by us;
   * 2. initial random coefficients from user or randomly assigned;
   * 3. sigma square;
   * 4. T matrix value
   */
  void initComputationStateHGLM(HGLMTask.ComputationEngineTask engineTask) {
    int numRandomCoeff = _randomCoefficientNames.length;
    int numFixCoeff = _fixedCofficientNames.length;
    // need to initialize the coefficients, fixed and random
    if (_parms._seed == -1) // set the seed if not set by user
      _parms._seed = new Random().nextLong();
    Log.info("Random seed: "+_parms._seed);

    Random random = new Random(_parms._seed);
    if (_parms._tau_e_var_init > 0.0)
      _tauEVarE10 = _parms._tau_e_var_init;
    else
      _tauEVarE10 = Math.abs(random.nextGaussian());

    _T = new double[numRandomCoeff][numRandomCoeff];
    if (_parms._initial_t_matrix != null) {
      grabInitValuesFromFrame(_parms._initial_t_matrix, _T);
      double[][] transposeT = ArrayUtils.transpose(_T);
      if (!equal2DArrays(_T, transposeT, 1e-6))
        throw new IllegalArgumentException("initial_t_matrix must be symmetric but is not!");
      // make sure matrix is semi positive definite
      Matrix tMat = new Matrix(_T);
      if ((_parms._max_iterations > 0) && !tMat.chol().isSPD()) // only check this when we actually build the model
        throw new IllegalArgumentException("initial_t_matrix must be positive semi definite but is not!");
    } else {
      if (_parms._tau_u_var_init > 0.0) {
        _tauEVarE10 = _parms._tau_u_var_init;
      } else {
        _tauEVarE10 = Math.abs(random.nextGaussian());
      }
      setDiagValues(_T, _tauEVarE10);
    }
    
    _ubeta = new double[engineTask._numLevel2Units][engineTask._numRandomCoeffs];
    if ( null != _parms._initial_random_effects) {  // read in initial random values
      grabInitValuesFromFrame(_parms._initial_random_effects, _ubeta);
    } else {  // randomly generating random initial values
      gaussianVector(random, _ubeta, _level2UnitNames.length, numRandomCoeff);
      ArrayUtils.mult(_ubeta, Math.sqrt(_T[0][0]));
    }
    // copy over initial fixed coefficient values
    if (null != _parms._initial_fixed_effects) {
      if (_parms._initial_fixed_effects.length != numFixCoeff)
        throw new IllegalArgumentException("initial_fixed_effects must be an double[] array of size "+numFixCoeff);

      _beta = _parms._initial_fixed_effects;
    } else {
      _beta = new double[numFixCoeff];
      _beta[_beta.length-1] = _parms.train().vec(_parms._response_column).mean();
    }
  }
  
  public double[] getBeta() { return _beta; }
  public double[][] getUbeta() { return _ubeta; }
  public double getTauUVar() { return _tauEVarE10; }
  public double getTauEVarE10() { return _tauEVarE10; }
  public String[] getFixedCofficientNames() { return _fixedCofficientNames; }
  public String[] getRandomCoefficientNames() { return _randomCoefficientNames; }
  public String[] getGroupColumnNames() { return _level2UnitNames; }
  public double[][] getT() { return _T; }
  public int getNumFixedCoeffs() { return _numFixedCoeffs; }
  public int getNumRandomCoeffs() { return _numRandomCoeffs; }
  public int getNumLevel2Units() { return _numLevel2Unit; }
  public int getLevel2UnitIndex() { return _level2UnitIndex; }
  public void setBeta(double[] beta) {
    System.arraycopy(beta, 0, _beta, 0, beta.length);
  }
  public void setUbeta(double[][] ubeta) {
    copy2DArray(ubeta, _ubeta);
  }
  public void setT(double[][] tmat) {
    copy2DArray(tmat, _T);
  }
  public void setTauEVarE10(double tEVar) {
    _tauEVarE10 = tEVar;
  }
  
  public static class ComputationStateSimple {
    final public double[] _beta;
    final public double[][] _ubeta;
    final public double[][] _tmat;
    final public double _tauEVar;
    
    public ComputationStateSimple(double[] beta, double[][] ubeta, double[][] tmat, double tauEVar) {
      _beta = beta;
      _ubeta = ubeta;
      _tmat = tmat;
      _tauEVar = tauEVar;
    }
  }
}

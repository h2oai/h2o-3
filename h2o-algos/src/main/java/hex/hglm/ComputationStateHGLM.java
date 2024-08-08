package hex.hglm;

import hex.DataInfo;
import water.DKV;
import water.Job;
import water.Scope;
import water.fvec.Frame;
import water.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static hex.hglm.HGLMUtils.readRandomEffectInitFrame;
import static water.util.ArrayUtils.gaussianVector;

public class ComputationStateHGLM {
  final int _nFixedBetas;   // fixed coefficient length including inactive predictors
  final int _nRandomBetas;  // random coefficient length including inactive predictors
  public final HGLMModel.HGLMParameters _parms;
  int _iter;
  private double[] _beta; // fixed coefficients
  private double[][] _ubeta; // random coefficients;
  final DataInfo _dinfo;
  private final Job _job;
  double _tauUVar = 0; // variance of random coefficients effects;
  double _tauEVar = 0; // variance of random noise
  String[] _fixedCofficientNames; // include intercept
  String[] _randomCoefficientNames; // include intercept only if random effect is in intercept
  String[] _level2UnitNames; // enum levels of group column
  int _numLevel2Unit;
  final int _groupColIndex;
  
  public ComputationStateHGLM(Job job, HGLMModel.HGLMParameters parms, DataInfo dinfo, ComputationEngineTask engTask, int iter) {
    _job = job;
    _parms = parms;
    _dinfo = dinfo;
    _iter = iter;
    _fixedCofficientNames = engTask._fixedCoeffNames;
    _level2UnitNames = engTask._level2UnitNames;
    _randomCoefficientNames = engTask._randomCoeffNames;
    _groupColIndex = engTask._groupColIndex;
    initComputationStateHGLM(engTask);
    _nFixedBetas = _beta.length;
    _nRandomBetas = _ubeta[0].length;
    _numLevel2Unit = _ubeta.length;
  }
  
  void initComputationStateHGLM(ComputationEngineTask engineTask) {
    // need to initialize the coefficients, fixed and random
    if (_parms._seed == -1)
      _parms._seed = new Random().nextLong();
    Log.info("Random seed: "+_parms._seed);

    _ubeta = new double[engineTask._numLevel2Units][engineTask._numRandomCoeffs];
    Random random = new Random(_parms._seed);
    if ( null != _parms._initial_random_effects) {  // read in initial random values
      List<String> randomCoeffNames = Arrays.stream(engineTask._randomCoeffNames).collect(Collectors.toList());

      Frame randomEffects = DKV.getGet(_parms._initial_random_effects);
      Scope.track(randomEffects);
      if (randomEffects.numRows() != _ubeta.length || randomEffects.numCols() != _ubeta[0].length)
        throw new IllegalArgumentException("initial_random_effects: Initial random coefficients must be" +
                " a double[][] array of size "+randomEffects.numRows()+" rows and "+randomEffects.numCols()+" columns" +
                " but is not.");
      
      readRandomEffectInitFrame(randomEffects, _ubeta, randomCoeffNames);
    } else {  // randomly generating random initial values
      gaussianVector(random, _ubeta, _level2UnitNames.length, _randomCoefficientNames.length);
    }
    // copy over initial fixed coefficient values
    if (null != _parms._initial_fixed_effects) {
      if (_parms._initial_fixed_effects.length != _fixedCofficientNames.length)
        throw new IllegalArgumentException("initial_fixed_effects must be an double[] array of size "+_fixedCofficientNames.length);

      _beta = _parms._initial_fixed_effects;
    } else {
      _beta = new double[_fixedCofficientNames.length];
      _beta[_beta.length-1] = _parms.train().vec(_parms._response_column).mean();
    }

    if (_parms._tau_e_var_init != 0.0)
      _tauEVar = _parms._tau_e_var_init;
    else
      _tauEVar = Math.abs(random.nextGaussian());

    if (_parms._tau_u_var_init != 0.0)
      _tauUVar = _parms._tau_u_var_init;
    else
      _tauUVar = Math.abs(random.nextGaussian());
  }
  
  public double[] get_beta() { return _beta; }
  public double[][] get_ubeta() { return _ubeta; }
  public double get_tauUVar() { return _tauUVar; }
  public double get_tauEVar() { return _tauEVar; }
  public String[] get_fixedCofficientNames() { return _fixedCofficientNames; }
  public String[] get_randomCoefficientNames() { return _randomCoefficientNames; }
  public String[] get_groupColumnNames() { return _level2UnitNames; }
}

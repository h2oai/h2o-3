package hex.glm;

import hex.DataInfo;
import hex.glm.GLM.BetaConstraint;
import hex.glm.GLM.GLMGradientInfo;
import hex.glm.GLM.GLMGradientSolver;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.gram.Gram;
import hex.optimization.ADMM;
import hex.optimization.OptimizationUtils.GradientInfo;
import hex.optimization.OptimizationUtils.GradientSolver;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.H2O;
import water.H2ORuntime;
import water.Job;
import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.Log;
import water.util.MathUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static hex.glm.ComputationState.GramGrad.findZeroCols;
import static hex.glm.ConstrainedGLMUtils.*;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.glm.GLMUtils.calSmoothNess;
import static hex.glm.GLMUtils.copyGInfo;
import static water.util.ArrayUtils.*;

public final class ComputationState {
  private static final double R2_EPS = 1e-7;
  public static final double EPS_CS = 1e-6;
  public static final double EPS_CS_SQUARE = EPS_CS*EPS_CS;
  private static final int MIN_PAR = 1000;
  final boolean _intercept;
  final int _nbetas;
  public final GLMParameters _parms;
  private BetaConstraint _bc;
  double _alpha;
  double[] _ymu;
  double [] _u;
  private double [] _zValues;
  private boolean _dispersionEstimated;
  boolean _allIn;
  int _iter;
  private double _lambda = 0;
  private double _lambdaMax = Double.NaN;
  private GLMGradientInfo _ginfo; // gradient info excluding l1 penalty
  private double _likelihood;
  private double _gradientErr;
  private boolean _lambdaNull; // true if lambda was not provided by user
  private double _gMax; // store max value of original gradient without dividing by math.max(1e-2, _parms._alpha[0])
  private DataInfo _activeData;
  private BetaConstraint _activeBC;
  LinearConstraints[] _equalityConstraintsLinear = null;
  LinearConstraints[] _lessThanEqualToConstraintsLinear = null;
  LinearConstraints[] _equalityConstraintsBeta = null;
  LinearConstraints[] _lessThanEqualToConstraintsBeta = null;
  LinearConstraints[] _equalityConstraints = null;
  LinearConstraints[] _lessThanEqualToConstraints = null;
  double[] _lambdaEqual;
  double[] _lambdaLessThanEqualTo;
  ConstraintsDerivatives[] _derivativeEqual = null;
  ConstraintsDerivatives[] _derivativeLess = null;
  ConstraintsGram[] _gramEqual = null;
  ConstraintsGram[] _gramLess = null;
  private final GLM.BetaInfo _modelBetaInfo;
  private double[] _beta; // vector of coefficients corresponding to active data
  final DataInfo _dinfo;
  private GLMGradientSolver _gslvr;
  private final Job _job;
  private int _activeClass = -1;
  double[][][] _penaltyMatrix;
  int[][] _gamBetaIndices;
  int _totalBetaLength; // actual coefficient length without taking into account active columns only
  int _betaLengthPerClass;
  public boolean _noReg;
  public ConstrainedGLMUtils.ConstraintGLMStates _csGLMState;
  
  public ComputationState(Job job, GLMParameters parms, DataInfo dinfo, BetaConstraint bc, GLM.BetaInfo bi){
    _job = job;
    _parms = parms;
    _bc = bc;
    _activeBC = _bc;
    _dinfo = dinfo;
    _activeData = _dinfo;
    _intercept = _parms._intercept;
    _alpha = _parms._alpha[0];
    _nbetas = bi._nBetas;
    _betaLengthPerClass = dinfo.fullN()+1;
    _totalBetaLength = _betaLengthPerClass * _nbetas;
    _modelBetaInfo = bi;
  }

  /**
   * This method calculates 
   * 1. the contribution of constraints to the gradient;
   * 2. the contribution of ||h(beta)||^2 to the gradient and the hessian.
   * 
   * Note that this calculation is only needed once since the contributions to the derivative and hessian depends only
   * on the value of linear constraint coefficients and not the actual glm model parameters.  Refer to the doc, 
   * section VI.
   */
  public void initConstraintDerivatives(LinearConstraints[] equalityConstraints, LinearConstraints[] lessThanEqualToConstraints,
                                        List<String> coeffNames) {
    boolean hasEqualityConstraints = equalityConstraints != null;
    boolean hasLessConstraints = lessThanEqualToConstraints != null;
    _derivativeEqual = hasEqualityConstraints ? calDerivatives(equalityConstraints, coeffNames) : null;
    _derivativeLess = hasLessConstraints ? calDerivatives(lessThanEqualToConstraints, coeffNames) : null;
    // contribution to gradient and hessian from ||h(beta)||^2 without C, stays constant once calculated, active status can change
    _gramEqual = hasEqualityConstraints ? calGram(_derivativeEqual) : null;
    _gramLess = hasLessConstraints ? calGram(_derivativeLess) : null;
  }

  /***
   * Any time when the glm coefficient changes, the constraints values will change and active constraints can be inactive
   * and vice versa.  In addition, the active status of the derivatives and 2nd derivatives can change as well.  The
   * derivative and 2nd derivatives are part of the ComputationState.  It is the purpose of this method to change the
   * active status of the constraint derivatives (transpose(lambda)*h(beta)) and the 2nd order derivatives of 
   * (ck/2*transpose(h(beta))*h(beta)).
   */
  public void updateConstraintInfo(LinearConstraints[] equalityConstraints, LinearConstraints[] lessThanEqualToConstraints) {
    updateDerivativeActive(_derivativeEqual, _gramEqual, equalityConstraints);
    updateDerivativeActive(_derivativeLess, _gramLess, lessThanEqualToConstraints);
  }
  
  public void updateDerivativeActive(ConstraintsDerivatives[] derivativesConst, ConstraintsGram[] gramConst, 
                                     LinearConstraints[] constraints) {
    if (constraints != null) {
      IntStream.range(0, derivativesConst.length).forEach(index -> {
        derivativesConst[index]._active = constraints[index]._active;
        gramConst[index]._active = constraints[index]._active;
      });
    }
  }
  
  public void resizeConstraintInfo(LinearConstraints[] equalityConstraints,
                                   LinearConstraints[] lessThanEqualToConstraints) {
    boolean hasEqualityConstraints = _derivativeEqual != null;
    boolean hasLessConstraints = _derivativeLess != null;
    List<String> coeffNames = Arrays.stream(_activeData.coefNames()).collect(Collectors.toList());
    _derivativeEqual = hasEqualityConstraints ? calDerivatives(equalityConstraints, coeffNames) : null;
    _derivativeLess = hasLessConstraints ? calDerivatives(lessThanEqualToConstraints, coeffNames) : null;
    _gramEqual = hasEqualityConstraints ? calGram(_derivativeEqual) : null;
    _gramLess = hasLessConstraints ? calGram(_derivativeLess) : null;
  }

  public ComputationState(Job job, GLMParameters parms, DataInfo dinfo, BetaConstraint bc, GLM.BetaInfo bi, 
                          double[][][] penaltyMat, int[][] gamColInd){
    this (job, parms, dinfo, bc, bi);
    _penaltyMatrix = penaltyMat;
    _gamBetaIndices = gamColInd;
    _lambdaNull = (_parms._lambda==null) && !(_parms._lambda_search);
  }
  
  // copy over parameters from _model to _state for checkpointing
  // jest of this method is to restore the _state to be the same as before
  void copyCheckModel2State(GLMModel model, int[][] _gamColIndices) {
    GLMModel.GLMOutput modelOutput = model._output;
    int submodelInd;
    int coefLen = _nbetas > 2 ? (_dinfo.fullN() + 1) * _nbetas : (_dinfo.fullN() + 1);
    if (modelOutput._submodels.length > 1)  // lambda search or multiple alpha/lambda cases
      submodelInd = modelOutput._submodels.length - 1; // submodel where the model building ends
    else  // no lambda search or multiple alpha/lambda case
      submodelInd = 0;

    setIter(modelOutput._submodels[submodelInd].iteration);
    setAlpha(modelOutput._submodels[submodelInd].alpha_value);

    if (submodelInd > 0) {
      int preCurrSubmodelInd = gaussian.equals(_parms._family) ? submodelInd : (submodelInd - 1);
      _activeData._activeCols = modelOutput._submodels[preCurrSubmodelInd].idxs;
      double[] betaExpand = Family.multinomial.equals(_parms._family)
              ? ArrayUtils.expandAndScatter(modelOutput._submodels[preCurrSubmodelInd].beta, coefLen, _activeData._activeCols)
              : expandBeta(modelOutput._submodels[preCurrSubmodelInd].beta);
      GLMGradientInfo ginfo = new GLMGradientSolver(_job, _parms, _dinfo, 0, activeBC(), _modelBetaInfo, _penaltyMatrix,
              _gamColIndices).getGradient(betaExpand);  // gradient obtained with zero penalty

      _activeData._activeCols = null;
      updateState(betaExpand, ginfo);
      setLambdaSimple(_parms._lambda[preCurrSubmodelInd]);
    }
    // this part must be done for single model before setting coefficients
    if (!gaussian.equals(_parms._family))  // will build for new lambda for gaussian
      setLambda(modelOutput._submodels[submodelInd].lambda_value);

    // update _state with last submodelInd coefficients
    double[] expandedBeta = modelOutput._submodels[submodelInd].idxs == null
            ? modelOutput._submodels[submodelInd].beta
            : ArrayUtils.expandAndScatter(modelOutput._submodels[submodelInd].beta, coefLen,
            modelOutput._submodels[submodelInd].idxs);
    GLMGradientInfo ginfo = new GLMGradientSolver(_job, _parms, _dinfo, 0, activeBC(), _modelBetaInfo,
            _penaltyMatrix, _gamColIndices).getGradient(expandedBeta);  // gradient obtained with zero penalty
    updateState(expandedBeta, ginfo);
    // make sure model._betaCndCheckpoint is of the right size
    if (model._betaCndCheckpoint != null) {
      if (_activeData._activeCols == null || (_activeData._activeCols.length != model._betaCndCheckpoint.length)) {
        double[] betaCndCheckpoint = ArrayUtils.expandAndScatter(model._betaCndCheckpoint, coefLen,
                modelOutput._submodels[submodelInd].idxs); // expand betaCndCheckpoint out
        if (_activeData._activeCols != null) // contract the betaCndCheckpoint to the right activeCol length
          betaCndCheckpoint = extractSubRange(betaCndCheckpoint.length, 0, activeData()._activeCols, betaCndCheckpoint);
        model._betaCndCheckpoint = betaCndCheckpoint;  
      }
    }
  }
  
  public void setZValues(double[] zValues, boolean dispersionEstimated) {
    _zValues = zValues;
    _dispersionEstimated = dispersionEstimated;
  }

  public boolean getLambdaNull() { return _lambdaNull; }

  public GLMGradientSolver gslvr(){return _gslvr;}
  public double lambda(){return _lambda;}
  public double alpha() {return _alpha;}
  public double[] zValues() {return _zValues;}
  public boolean dispersionEstimated() {return _dispersionEstimated;}
  public void setLambdaMax(double lmax) {
    _lambdaMax = lmax;
  }
  public void setgMax(double gmax) {
    _gMax = gmax;
  }
  public void setAlpha(double alpha) {
    _alpha=alpha;
    setLambdaMax(_gMax/Math.max(1e-2,alpha)); // need to set _lmax every time alpha value changes
  }

  public void setLambda(double lambda) {
    adjustToNewLambda(0, _lambda);
    // strong rules are to be applied on the gradient with no l2 penalty
    // NOTE: we start with lambdaOld being 0, not lambda_max
    // non-recursive strong rules should use lambdaMax instead of _lambda
    // However, it seems tobe working nicely to use 0 instead and be more aggressive on the predictor pruning
    // (shoudl be safe as we check the KKTs anyways)
    applyStrongRules(lambda, _lambda);
    _lambda = lambda;
    if (_penaltyMatrix == null)
      _gslvr = new GLMGradientSolver(_job, _parms, _activeData, l2pen(), _activeBC, _modelBetaInfo);
    else
      _gslvr = new GLMGradientSolver(_job, _parms, _activeData, l2pen(), _activeBC, _modelBetaInfo, _penaltyMatrix, _gamBetaIndices);
    adjustToNewLambda(lambda, 0);
  }
  
  public double [] beta(){
    if(_activeClass != -1)
      return betaMultinomial(_activeClass,_beta);
    return _beta;
  }
  public GLMGradientInfo ginfo(){return _ginfo == null?(_ginfo = gslvr().getGradient(beta())):_ginfo;}
  public BetaConstraint activeBC(){return _activeBC;}
  public double likelihood() {return _likelihood;}
  public boolean ginfoNull() {return _ginfo==null;}

  public DataInfo activeData(){
    if(_activeClass != -1)
      return activeDataMultinomial(_activeClass);
    return _activeData;
  }

  public DataInfo activeDataMultinomial(){return _activeData;}


  public void dropActiveData(){_activeData = null;}

  public String toString() {
    return "iter=" + _iter + " lmb=" + GLM.lambdaFormatter.format(_lambda) + " alpha=" + 
            GLM.lambdaFormatter.format(_alpha)+ " obj=" + MathUtils.roundToNDigits(objective(),4) + " imp=" + 
            GLM.lambdaFormatter.format(_relImprovement) + " bdf=" + GLM.lambdaFormatter.format(_betaDiff);
  }

  private void adjustToNewLambda(double lambdaNew, double lambdaOld) {
    double ldiff = lambdaNew - lambdaOld;
    if(ldiff == 0 || l2pen() == 0) return;
    double l2pen = .5*ArrayUtils.l2norm2(_beta,true);
    if (_parms._family==Family.ordinal)
      l2pen = l2pen/ _nbetas;   // need only one set of parameters

    if(l2pen > 0) {
      if (_ginfo == null) _ginfo = ginfo();
      if(_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
        l2pen = 0;
        int off = 0;
        for(int c = 0; c < _nbetas; ++c) {
          DataInfo activeData = activeDataMultinomial(c);
          for (int i = 0; i < activeData.fullN(); ++i) {
            double b = _beta[off + i];
            _ginfo._gradient[off + i] += ldiff * b;
            l2pen += b*b;
          }
          if (_parms._family == Family.ordinal) // one beta for all classes
            break;

          off += activeData.fullN()+1;
        }
        l2pen *= .5;
      } else  for(int i = 0; i < _activeData.fullN(); ++i)
        _ginfo._gradient[i] += ldiff*_beta[i];
    }
    _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal + ldiff * l2pen, _ginfo._gradient);
  }

  public double l1pen() {return _alpha*_lambda;}
  public double l2pen() {return (1-_alpha)*_lambda;}


  /**
   * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
   *
   * @return indices of expected active predictors.
   */
  protected void applyStrongRules(double lambdaNew, double lambdaOld) {
    lambdaNew = Math.min(_lambdaMax,lambdaNew);
    lambdaOld = Math.min(_lambdaMax,lambdaOld);
    if (_parms._family == Family.multinomial || _parms._family == Family.ordinal/* && _parms._solver != GLMParameters.Solver.L_BFGS */) {
      applyStrongRulesMultinomial(lambdaNew, lambdaOld);
      return;
    }
    int P = _dinfo.fullN();
    _activeBC = _bc;
    _activeData = _activeData != null?_activeData:_dinfo;
    // keep all predictors for the case of beta constraints or linear constraints
    _allIn = _allIn || _alpha*lambdaNew == 0 || _activeBC.hasBounds() || _parms._linear_constraints != null;
    if (!_allIn) {
      int newlySelected = 0;
      final double rhs = Math.max(0,_alpha * (2 * lambdaNew - lambdaOld));
      int [] newCols = MemoryManager.malloc4(P);
      int j = 0;
      int[] oldActiveCols = _activeData._activeCols == null ? new int[]{P} : _activeData.activeCols();
      for (int i = 0; i < P; ++i) {
        if(j < oldActiveCols.length && oldActiveCols[j] == i)
          j++;
        else if (_ginfo._gradient[i] > rhs || -_ginfo._gradient[i] > rhs)
          newCols[newlySelected++] = i; // choose active columns here
      }
      if(_parms._max_active_predictors != -1 && (oldActiveCols.length + newlySelected -1) > _parms._max_active_predictors){
        Integer [] bigInts = ArrayUtils.toIntegers(newCols, 0, newlySelected);
        Arrays.sort(bigInts, new Comparator<Integer>() {
          @Override
          public int compare(Integer o1, Integer o2) {
            return (int)Math.signum(_ginfo._gradient[o2.intValue()]*_ginfo._gradient[o2.intValue()] - _ginfo._gradient[o1.intValue()]*_ginfo._gradient[o1.intValue()]);
          }
        });
        newCols = ArrayUtils.toInt(bigInts,0,_parms._max_active_predictors - oldActiveCols.length + 1);
        Arrays.sort(newCols);
      } else newCols = Arrays.copyOf(newCols,newlySelected);
      newCols = ArrayUtils.sortedMerge(oldActiveCols,newCols);
      // merge already active columns in
      int active = newCols.length;
      _allIn = active == P;
      if(!_allIn) {
        int [] cols = newCols;
        assert cols[active-1] == P; // intercept is always selected, even if it is false (it's gonna be dropped later, it is needed for other stuff too)
        _beta = ArrayUtils.select(_beta, cols);
        if(_u != null) _u = ArrayUtils.select(_u,cols);
        _activeData = _dinfo.filterExpandedColumns(cols);
        assert _activeData.activeCols().length == _beta.length;
        assert _u == null || _activeData.activeCols().length == _u.length;
        _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal, ArrayUtils.select(_ginfo._gradient, cols));
        _activeBC = _bc.filterExpandedColumns(_activeData.activeCols());
        _gslvr = _penaltyMatrix == null ? new GLMGradientSolver(_job,_parms,_activeData,(1-_alpha)*_lambda,_bc,_modelBetaInfo) 
                : new GLMGradientSolver(_job, _parms, _dinfo, (1 - _alpha) * _lambda, _bc, _modelBetaInfo,  _penaltyMatrix,
                _gamBetaIndices);
        assert _beta.length == cols.length;
        return;
      }
    }
    _activeData = _dinfo;
  }

  public boolean _lsNeeded = false;

  public DataInfo [] _activeDataMultinomial;

  public DataInfo activeDataMultinomial(int c) {return _activeDataMultinomial != null?_activeDataMultinomial[c]:_dinfo;}

  /**
   * This method will return a double array that is extracted from src (which includes active and non-active columns)
   * to only include active columns stated in ids.
   * 
   * @param N
   * @param c
   * @param ids
   * @param src
   * @return
   */
  public static double [] extractSubRange(int N, int c, int [] ids, double [] src) {
    if(ids == null) return Arrays.copyOfRange(src,c*N,c*N+N);
    double [] res = MemoryManager.malloc8d(ids.length);
    int j = 0;
    int off = c*N;
    for(int i:ids)
      res[j++] = src[off+i];
    return res;
  }

  /**
   * This method will extract coefficients from multinomial.  The extracted coefficients are only from one class
   * and it contains the active and non-active columns.
   * 
   * @param N
   * @param c
   * @param ids
   * @param src
   * @param dst
   */
   static void fillSubRange(int N, int c, int [] ids, double [] src, double [] dst) {
    if(ids == null) {
      System.arraycopy(src,0,dst,c*N,N);
    } else {
      int j = 0;
      int off = c * N;
      for (int i : ids)
        dst[off + i] = src[j++];
    }
  }

  public double [] betaMultinomial(){return _beta;}
  
  public double [] betaMultinomial(int c, double [] beta) {
     return extractSubRange(_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols(),beta);
   }

  public double [] betaMultinomialFull(int c, double [] beta) {
     if (_parms._remove_collinear_columns)
      return extractSubRange(_betaLengthPerClass,c,_activeDataMultinomial[c].activeCols(),beta);
     else
       return extractSubRange(_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols(),beta);
  }
   
   public double[] shrinkFullArray(double[] fullArray) {
     if (_activeData.activeCols() == null)
       return fullArray;
     int[] activeColsAllClass = genActiveColsAllClass(_activeData.activeCols().length* _nbetas, 
             _betaLengthPerClass, _activeData.activeCols(), _nbetas);
     return ArrayUtils.select(fullArray, activeColsAllClass);
   }

  public static double[] expandToFullArray(double[] shortenArr, int[] activeCols, int _totalBetaLength, int nclasses, 
                                           int betaLengthPerClass) {
    if (activeCols == null)
      return shortenArr;
    int[] activeColsAllClass = genActiveColsAllClass(activeCols.length*nclasses,
            betaLengthPerClass, activeCols, nclasses);
    double[] fullArray = new double[_totalBetaLength];
    fillSubRange(_totalBetaLength, 0, activeColsAllClass, shortenArr, fullArray);
    return fullArray;
  }

  public static int[] genActiveColsAllClass(int activeColsLen, int numBetaPerClass, int[] activeColsOrig, int nclasses) {
    int[] activeCols = new int[activeColsLen];
    int offset = 0;
    int[] activeColsOneClass = activeColsOrig;
    for (int classIndex=0; classIndex < nclasses; classIndex++) {
      int finalOffset = numBetaPerClass*classIndex;
      int[] activeCols1Class = IntStream.of(activeColsOneClass).map(i->i+finalOffset).toArray();
      int num2Copy = activeColsOneClass.length;
      System.arraycopy(activeCols1Class, 0, activeCols, offset, num2Copy);
      offset += num2Copy;
    }
    return activeCols;
  }
  
  public int[] genActiveColsIndClass(int activeColsLen, int numBetaPerClass, int[] activeColsOrig, int activeClass,
                                     int nclasses) {
    int[] activeCols = new int[activeColsLen];// total length
    int offset = 0;
    int[] activeColsOneClass = activeColsOrig;
    for (int classIndex = 0; classIndex < activeClass; classIndex++) {
      int finalOffset = numBetaPerClass*classIndex;
      int num2Copy = activeColsOneClass.length;
      int[] activeCols1Class = IntStream.of(activeColsOneClass).map(i->i+finalOffset).toArray();
      System.arraycopy(activeCols1Class, 0, activeCols, offset, num2Copy);
      offset += num2Copy;
    }
    for (int classInd = activeClass; classInd < nclasses; classInd++) {
      int finalOffset = numBetaPerClass*classInd;
      int[] activeCols1Class = IntStream.range(0, numBetaPerClass).map(i->i+finalOffset).toArray();
      System.arraycopy(activeCols1Class, 0, activeCols, offset, numBetaPerClass);
      offset += numBetaPerClass;
    }
    return activeCols;
  }

  public GLMSubsetGinfo ginfoMultinomial(int c) {
    return new GLMSubsetGinfo(_ginfo,(_activeData.fullN()+1),c,_activeDataMultinomial[c].activeCols());
  }

  public GLMSubsetGinfo ginfoMultinomialRCC(int c) {
    if (_activeData.fullN() + 1 == _activeData.activeCols().length)
      return new GLMSubsetGinfo(_ginfo, (_activeData.fullN() + 1), c, IntStream.range(0, 
              _activeData.activeCols().length).toArray());
    else
      return new GLMSubsetGinfo(_ginfo, (_activeData.fullN() + 1), c, _activeData.activeCols());
  }

  public void setBC(BetaConstraint bc) {
    _bc = bc;
    _activeBC = _bc;
  }
  
  public void setLinearConstraints(LinearConstraints[] equalityC, LinearConstraints[] lessThanEqualToC, boolean forBeta) {
     if (forBeta) {
       _equalityConstraintsBeta = equalityC.length == 0 ? null : equalityC;
       _lessThanEqualToConstraintsBeta = lessThanEqualToC.length == 0 ? null : lessThanEqualToC;
     } else {
       _equalityConstraintsLinear = equalityC.length == 0 ? null : equalityC;
       _lessThanEqualToConstraintsLinear = lessThanEqualToC.length == 0 ? null : lessThanEqualToC;
     }
  }

  public void setActiveClass(int activeClass) {_activeClass = activeClass;}

  public double deviance() {
    switch (_parms._family) {
      case gaussian:
      case binomial:
      case quasibinomial:
      case ordinal:
      case multinomial:
      case fractionalbinomial:
        return 2*likelihood();
      case poisson:
      case gamma:
      case negativebinomial:  
      case tweedie:
        return likelihood();
      default:
        throw new RuntimeException("unknown family " + _parms._family);
    }
  }

  /***
   * This method will grab a subset of the gradient for each multinomial class.  However, if remove_collinear_columns is
   * on, fullInfo will only contains the gradient of active columns.
   */
  public static class GLMSubsetGinfo extends GLM.GLMGradientInfo {
    public final GLMGradientInfo _fullInfo;
    public GLMSubsetGinfo(GLMGradientInfo fullInfo, int N, int c, int [] ids) {
      super(fullInfo._likelihood, fullInfo._objVal, extractSubRange(N,c,ids,fullInfo._gradient));
      _fullInfo = fullInfo; // fullInfo._gradient may not be full
    }
  }
  public GradientSolver gslvrMultinomial(final int c) {
    double[] betaCopy = new double[_totalBetaLength]; // make sure fullbeta is full length
    if (_beta.length < _totalBetaLength) {
      if (_beta.length == _activeData.activeCols().length* _nbetas) {  // all classes converted
        int[] activeCols = genActiveColsAllClass(_beta.length, _betaLengthPerClass, _activeData.activeCols(), _nbetas);
        fillSubRange(_totalBetaLength, 0, activeCols, _beta, betaCopy);
      } else {
        int[] activeCols = genActiveColsIndClass(_beta.length, _betaLengthPerClass, _activeData.activeCols(), c, _nbetas);
        fillSubRange(_totalBetaLength, 0, activeCols, _beta, betaCopy);
      }
    } else {
      System.arraycopy(_beta, 0, betaCopy, 0, _totalBetaLength);
    }
    final double [] fullbeta = betaCopy; // make sure fullbeta contains everything
    return new GradientSolver() {
      // beta is full coeff Per class.  Need to return gradient with full columns
      @Override
      public GradientInfo getGradient(double[] beta) {
        // fill fullbeta with new values of beta for class c
        fillSubRange(_dinfo.fullN()+1,c,_activeDataMultinomial[c].activeCols(),beta,fullbeta); // fullbeta contains everything
        GLMGradientInfo fullGinfo =  _gslvr.getGradient(fullbeta);  // beta contains all columns
        if (fullbeta.length > fullGinfo._gradient.length) {  // fullGinfo only contains gradient for active columns here
          double[] fullGinfoGradient = expandToFullArray(fullGinfo._gradient, _activeData.activeCols(), 
                  _totalBetaLength, _nbetas, _betaLengthPerClass);
          fullGinfo._gradient = fullGinfoGradient;  // make sure fullGinfo contains full gradient
        }
        return new GLMSubsetGinfo(fullGinfo,_betaLengthPerClass,c,_activeData.activeCols());// fullGinfo has full gradient
          //return new GLMSubsetGinfo(fullGinfo,_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols());
      }
      @Override
      public GradientInfo getObjective(double[] beta) {return getGradient(beta);}
    };
  }

  public void setBetaMultinomial(int c, double [] beta, double [] bc) {
    if(_u != null) Arrays.fill(_u,0);
    if (_parms._remove_collinear_columns)
      fillSubRange(_betaLengthPerClass,c,_activeDataMultinomial[c].activeCols(),bc,beta);
    else
      fillSubRange(_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols(),bc,beta);
  }
  /**
   * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
   *
   * @return indices of expected active predictors.
   */
  /**
   * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
   *
   * @return indices of expected active predictors.
   */
  protected int applyStrongRulesMultinomial_old(double lambdaNew, double lambdaOld) {
    int P = _dinfo.fullN();
    int N = P+1;
    int selected = 0;
    _activeBC = _bc;
    _activeData = _dinfo;
    if (!_allIn) {
      if(_activeDataMultinomial == null)
        _activeDataMultinomial = new DataInfo[_nbetas];
      final double rhs = _alpha * (2 * lambdaNew - lambdaOld);
      int[] oldActiveCols = _activeData._activeCols == null ? new int[0] : _activeData.activeCols();
      int [] cols = MemoryManager.malloc4(N* _nbetas);
      int j = 0;

      for(int c = 0; c < _nbetas; ++c) {
        int start = selected;
        for (int i = 0; i < P; ++i) {
          if (j < oldActiveCols.length && i == oldActiveCols[j]) {
            cols[selected++] = i;
            ++j;
          } else if (_ginfo._gradient[c*N+i] > rhs || _ginfo._gradient[c*N+i] < -rhs) {
            cols[selected++] = i;
          }
        }
        cols[selected++] = P;// intercept
        _activeDataMultinomial[c] = _dinfo.filterExpandedColumns(Arrays.copyOfRange(cols,start,selected));
        for(int i = start; i < selected; ++i)
          cols[i] += c*N;
      }
      _allIn = selected == cols.length;
    }
    return selected;
  }

  /**
   * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
   *
   * @return indices of expected active predictors.
   */
  protected void applyStrongRulesMultinomial(double lambdaNew, double lambdaOld) {
    int P = _dinfo.fullN();
    int N = P+1;
    int selected = 0;
    _activeBC = _bc;
    _activeData = _dinfo;
    if (!_allIn) {
      if(_activeDataMultinomial == null)
        _activeDataMultinomial = new DataInfo[_nbetas];
      final double rhs = _alpha * (2 * lambdaNew - lambdaOld);
      int [] cols = MemoryManager.malloc4(N* _nbetas);

      int oldActiveColsTotal = 0;
      for(int c = 0; c < _nbetas; ++c) {
        int j = 0;
        int[] oldActiveCols = _activeDataMultinomial[c] == null ? new int[]{P} : _activeDataMultinomial[c]._activeCols;
        oldActiveColsTotal += oldActiveCols.length;
        for (int i = 0; i < P; ++i) {
          if (j < oldActiveCols.length && i == oldActiveCols[j]) {
            ++j;
          } else {  // need access to _ginfo
            if (_ginfo == null) _ginfo = ginfo();
            if (_ginfo._gradient[c * N + i] > rhs || _ginfo._gradient[c * N + i] < -rhs) {
              cols[selected++] = c * N + i;
            }
          }
        }
      }
      if(_parms._max_active_predictors != -1 && _parms._max_active_predictors - oldActiveColsTotal + _nbetas < selected) {
        Integer[] bigInts = ArrayUtils.toIntegers(cols, 0, selected);
        Arrays.sort(bigInts, new Comparator<Integer>() {
          @Override
          public int compare(Integer o1, Integer o2) {
            return (int) Math.signum(_ginfo._gradient[o2.intValue()] * _ginfo._gradient[o2.intValue()] - _ginfo._gradient[o1.intValue()] * _ginfo._gradient[o1.intValue()]);
          }
        });
        cols = ArrayUtils.toInt(bigInts, 0, _parms._max_active_predictors - oldActiveColsTotal + _nbetas);
        Arrays.sort(cols);
        selected = cols.length;
      }
      int i = 0;
      int [] cs = new int[P+1];
      int sum = 0;
      for(int c = 0; c < _nbetas; ++c){
        int [] classcols = cs;
        int[] oldActiveCols = _activeDataMultinomial[c] == null ? new int[]{P} : _activeDataMultinomial[c]._activeCols;
        int k = 0;
        while(i < selected && cols[i] < (c+1)*N)
          classcols[k++] = cols[i++]-c*N;
        classcols = ArrayUtils.sortedMerge(oldActiveCols,Arrays.copyOf(classcols,k));
        sum += classcols.length;
        _activeDataMultinomial[c] = _dinfo.filterExpandedColumns(classcols);
      }
      assert _parms._max_active_predictors == -1 || sum <= _parms._max_active_predictors + _nbetas :"sum = " + sum + " max_active_preds = " + _parms._max_active_predictors + ", nclasses = " + _nbetas;
      _allIn = sum == N* _nbetas;
    }
  }

  protected boolean checkKKTsMultinomial(){
    return true;
    //if(_activeData._activeCols == null) return true;
   // throw H2O.unimpl();
  }

  protected boolean checkKKTs() {
    if(_parms._family == Family.multinomial || _parms._family == Family.ordinal)  // always return true?
      return checkKKTsMultinomial();
    double [] beta = _beta;
    double [] u = _u;
    if(_activeData._activeCols != null) {
      beta = ArrayUtils.expandAndScatter(beta, _dinfo.fullN() + 1, _activeData._activeCols);
      if(_u != null)
        u =  ArrayUtils.expandAndScatter(_u, _dinfo.fullN() + 1, _activeData._activeCols);
    }
    int [] activeCols = _activeData.activeCols();
    if(beta != _beta || _ginfo == null) {
      _gslvr = _penaltyMatrix == null ? new GLMGradientSolver(_job, _parms, _dinfo, (1 - _alpha) * _lambda, _bc, _modelBetaInfo)
              : new GLMGradientSolver(_job, _parms, _dinfo, (1 - _alpha) * _lambda, _bc, _modelBetaInfo, _penaltyMatrix, _gamBetaIndices);
      _ginfo = _gslvr.getGradient(beta);
    }
    double[] grad = _ginfo._gradient.clone();
    double err = 1e-4;
    if(u != null && u != _u){ // fill in u for missing variables
      int k = 0;
      for(int i = 0; i < u.length; ++i) {
        if(_activeData._activeCols[k] == i){
          ++k; continue;
        }
        assert u[i] == 0;
        u[i] = -grad[i];
      }
    }
    ADMM.subgrad(_alpha * _lambda, beta, grad);
    for (int c : activeCols) // set the error tolerance to the highest error of included columns
      if (grad[c] > err) err = grad[c];
      else if (grad[c] < -err) err = -grad[c];
    _gradientErr = err;
    _beta = beta;
    _u = u;
    _activeBC = null;
    if(_parms._max_active_predictors == _activeData.fullN()){
      Log.info("skipping KKT check, reached maximum number of active predictors ("  + _parms._max_active_predictors + ")");
    } else if(!_allIn) {
      int[] failedCols = new int[64];
      int fcnt = 0;
      for (int i = 0; i < grad.length - 1; ++i) {
        if (Arrays.binarySearch(activeCols, i) >= 0) continue; // always include all previously active columns
        if (grad[i] > err || -grad[i] > err) {
          if (fcnt == failedCols.length)
            failedCols = Arrays.copyOf(failedCols, failedCols.length << 1);
          failedCols[fcnt++] = i;
        }
      }
      if (fcnt > 0) {
        Log.info(fcnt + " variables failed KKT conditions, adding them to the model and recomputing.");
        final int n = activeCols.length;
        int[] newCols = Arrays.copyOf(activeCols, activeCols.length + fcnt);
        for (int i = 0; i < fcnt; ++i)
          newCols[n + i] = failedCols[i];
        Arrays.sort(newCols);
        _beta = ArrayUtils.select(beta, newCols);
        if(_u != null) _u = ArrayUtils.select(_u,newCols);
        _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal, ArrayUtils.select(_ginfo._gradient, newCols));
        _activeData = _dinfo.filterExpandedColumns(newCols);
        _activeBC = _bc.filterExpandedColumns(_activeData.activeCols());
        _gslvr = _penaltyMatrix == null ? new GLMGradientSolver(_job, _parms, _activeData, 
                (1 - _alpha) * _lambda, _activeBC, _modelBetaInfo) : new GLMGradientSolver(_job, _parms, _activeData, 
                (1 - _alpha) * _lambda, _activeBC, _modelBetaInfo, _penaltyMatrix, _gamBetaIndices);
        return false;
      }
    }
    return true;
  }
  public void addOffset2Cols(int[] cols) {
    int offset = _activeClass*_activeData.activeCols().length;
    int colsLen = cols.length;
    for (int index = 0; index < colsLen; index++)
      cols[index] = cols[index]+offset;
  }
  public int []  removeCols(int [] cols) { // cols is per class, not overall
    int[] activeCols;
    int[] colsWOffset = cols.clone();
    if (_nbetas > 2 && _parms._remove_collinear_columns) {
      activeCols = ArrayUtils.removeIds(_activeDataMultinomial[_activeClass].activeCols(), cols);
      addOffset2Cols(colsWOffset);
    } else {
      activeCols = ArrayUtils.removeIds(_activeData.activeCols(), cols);
    }
      if (_beta != null)
        _beta = ArrayUtils.removeIds(_beta, colsWOffset);
      
    if(_u != null)
      _u = ArrayUtils.removeIds(_u,colsWOffset);
    if(_ginfo != null && _ginfo._gradient != null)
      _ginfo._gradient = ArrayUtils.removeIds(_ginfo._gradient,colsWOffset);
    _activeData = _dinfo.filterExpandedColumns(activeCols);  // changed _adaptedFrame to excluded inactive columns
    _activeBC = _bc.filterExpandedColumns(activeCols);
    _gslvr = _penaltyMatrix == null ? new GLMGradientSolver(_job, _parms, _activeData,
            (1 - _alpha) * _lambda, _activeBC, _modelBetaInfo) : new GLMGradientSolver(_job, _parms, _activeData,
            (1 - _alpha) * _lambda, _activeBC, _modelBetaInfo, _penaltyMatrix, _gamBetaIndices);
    _currGram = null;
    return activeCols;
  }

  private double penalty(double [] beta) {
    if(_lambda == 0) return 0;
    double l1norm = 0, l2norm = 0;
    if(_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
      int len = beta.length/ _nbetas;
      assert len* _nbetas == beta.length;
      for(int c = 0; c < _nbetas; ++c) {
        for(int i = c*len; i < (c+1)*len-1; ++i) {
          double d = beta[i];
          l1norm += d >= 0?d:-d;
          l2norm += d*d;
        }

        if (_parms._family == Family.ordinal) // done for ordinal, only one set of beta but numclass-1 intercepts
          break;
      }
    } else
      for(int i = 0; i < beta.length-1; ++i) {
        double d = beta[i];
        l1norm += d >= 0?d:-d;
        l2norm += d*d;
      }
    return l1pen()*l1norm + .5*l2pen()*l2norm;
  }
  public double objective() {return _beta == null?Double.MAX_VALUE:objective(_beta,_likelihood);}

  public double objective(double [] beta, double likelihood) {
    double gamVal = 0;
    if (_parms._glmType == GLMParameters.GLMType.gam) {
      if (beta.length == _totalBetaLength)
        gamVal = calSmoothNess(beta, _penaltyMatrix, _gamBetaIndices);
      else
        gamVal = calSmoothNess(expandBeta(beta), _penaltyMatrix, _gamBetaIndices);  // take up memory
    }
    if (_csGLMState != null && (_equalityConstraints != null || _lessThanEqualToConstraints != null))
      return _ginfo._objVal;
    else
      return likelihood * _parms._obj_reg + gamVal + penalty(beta) + (_activeBC == null?0:_activeBC.proxPen(beta));
  }

  /***
   *
   *  This methold will calculate the first derivative of h(beta). Refer to the doc, section VI.I
   *  
   */
  public static ConstrainedGLMUtils.ConstraintsDerivatives[] calDerivatives(LinearConstraints[] constraints, List<String> coefNames) {
    int numConstraints = constraints.length;
    ConstrainedGLMUtils.ConstraintsDerivatives[] constDeriv = new ConstrainedGLMUtils.ConstraintsDerivatives[numConstraints];
    LinearConstraints oneConstraint;
    for (int index=0; index<numConstraints; index++) {
      oneConstraint = constraints[index];
      constDeriv[index] = genOneDerivative(oneConstraint, coefNames);
    }
    return constDeriv;
  }

   /***
   * Given a constraint, this method will calculate the first order derivative.  Note that this derivative does not 
    * depend on the lambda applied to the constraint.  It only changes when the number of coefficients in beta changes and it
    * needs to be called again.
   */
  public static ConstrainedGLMUtils.ConstraintsDerivatives genOneDerivative(LinearConstraints oneConstraints, List<String> coeffNames) {
    ConstrainedGLMUtils.ConstraintsDerivatives constraintDerivative = new ConstrainedGLMUtils.ConstraintsDerivatives(oneConstraints._active);
    IcedHashMap<String, Double> coeffNameValues = oneConstraints._constraints;
    int index;
    for (String coefName: coeffNameValues.keySet()) {
      index = coeffNames.indexOf(coefName);
      if (index >= 0)
        constraintDerivative._constraintsDerivative.put(index, coeffNameValues.get(coefName));
    }
    return constraintDerivative;
  }

  /***
   * This method to calculate contribution of penalty to gram (d2H/dbidbj), refer to the doc Section VI.II
   */
  public static ConstrainedGLMUtils.ConstraintsGram[] calGram(ConstrainedGLMUtils.ConstraintsDerivatives[] derivativeEqual) {
    return Arrays.stream(derivativeEqual).map(x -> constructGram(x)).toArray(ConstrainedGLMUtils.ConstraintsGram[]::new);
  }

  /***
   * This method is not called often.  If called, it will calculate the contribution of constraints to the 
   * hessian.  Whenever there is a predictor number change, this function should be called again as it only looks 
   * at the predictor index.  This predictor index will change when the number of predictors change.  It calculates
   * the second derivative regardless of the active status because an inactive constraint may become active in the 
   * future.  Note that here, only half of the 2nd derivatives are calculated, namely d(tranpose(h(beta))*h(beta)/dCidCj
   * and not d(tranpose(h(beta))*h(beta)/dCjdCi since they are symmetric.
   */
  public static ConstrainedGLMUtils.ConstraintsGram constructGram(ConstrainedGLMUtils.ConstraintsDerivatives constDeriv) {
    ConstrainedGLMUtils.ConstraintsGram cGram = new ConstrainedGLMUtils.ConstraintsGram();
    List<Integer> predictorIndexc = constDeriv._constraintsDerivative.keySet().stream().collect(Collectors.toList());
    Collections.sort(predictorIndexc);
    while (!predictorIndexc.isEmpty()) {
      Integer firstEle = predictorIndexc.get(0);
      for (Integer oneCoeff : predictorIndexc) {
        ConstrainedGLMUtils.CoefIndices coefPairs = new ConstrainedGLMUtils.CoefIndices(firstEle, oneCoeff);
        cGram._coefIndicesValue.put(coefPairs, constDeriv._constraintsDerivative.get(firstEle)*constDeriv._constraintsDerivative.get(oneCoeff));
      }
      predictorIndexc.remove(0);
    }
    cGram._active = constDeriv._active; // calculate for active/inactive constraints, inactive may be active in future
    return cGram;
  }
  
  
  protected double updateState(double [] beta, double likelihood) {
    _betaDiff = ArrayUtils.linfnorm(_beta == null?beta:ArrayUtils.subtract(_beta,beta),false);
    double objOld = objective();
    _beta = beta;
    _ginfo = null;
    _likelihood = likelihood;
    return (_relImprovement = (objOld - objective())/Math.abs(objOld));
  }
  private double _betaDiff;
  private double _relImprovement;

  String convergenceMsg = "";
  public boolean converged(){
    boolean converged = false;
    if(_betaDiff < _parms._beta_epsilon) {
      convergenceMsg = "betaDiff < eps; betaDiff = " + _betaDiff + ", eps = " + _parms._beta_epsilon;
      converged = true;
    } else if(_relImprovement < _parms._objective_epsilon) {
      convergenceMsg = "relImprovement < eps; relImprovement = " + _relImprovement + ", eps = " + _parms._objective_epsilon;
      converged = true;
    } else convergenceMsg = "not converged, betaDiff = " + _betaDiff + ", relImprovement = " + _relImprovement;
    return converged;
  }

  public double updateState(double [] beta,GLMGradientInfo ginfo) {
    double objOld;
    if (_beta != null && beta.length > _beta.length) { // beta is full while _beta only contains active columns
      double[] shortBeta = shrinkFullArray(beta);
      _betaDiff = ArrayUtils.linfnorm(_beta == null ? beta : ArrayUtils.subtract(_beta, shortBeta), false);
      objOld = objective();
      if(_beta == null)_beta = shortBeta.clone();
      else System.arraycopy(shortBeta,0,_beta,0,shortBeta.length);
    } else {
      _betaDiff = ArrayUtils.linfnorm(_beta == null ? beta : ArrayUtils.subtract(_beta, beta), false);
      objOld = objective();
      if(_beta == null)_beta = beta.clone();
      else System.arraycopy(beta,0,_beta,0,beta.length);
    }
    _ginfo = ginfo;
    _likelihood = ginfo._likelihood;
    _relImprovement = (objOld - objective()) / Math.abs(objOld);
    return _relImprovement;
  }
  
  double getBetaDiff() {return _betaDiff;}
  protected void setBetaDiff(double betaDiff) { _betaDiff = betaDiff; }
  protected void setGradientErr(double gErr) { _gradientErr = gErr; }
  protected void setGinfo(GLMGradientInfo ginfo) {
    _ginfo = copyGInfo(ginfo);
  }
  protected void setBeta(double[] beta) {
    if(_beta == null)_beta = beta.clone();
    else System.arraycopy(beta,0, _beta, 0, beta.length);
  }
  
  protected void setIter(int iteration) {
    _iter = iteration;
  }
  
  protected void setLikelihood(double llk) { _likelihood = llk; }
  protected void setAllIn(boolean val) { _allIn = val; }
  protected void setGslvrNull() { _gslvr = null; }
  protected void setActiveDataMultinomialNull() { _activeDataMultinomial = null; }
  protected void setActiveDataNull() { _activeData = null; }
  protected void setLambdaSimple(double lambda) { _lambda=lambda; }
  
  public double [] expandBeta(double [] beta) { // for multinomials
    int fullCoefLen = (_dinfo.fullN() + 1) * _nbetas;
    if(_activeData._activeCols == null || beta.length == fullCoefLen)
      return beta;
    if (_nbetas <= 2 || !_parms._remove_collinear_columns)
      return ArrayUtils.expandAndScatter(beta, (_dinfo.fullN() + 1) * _nbetas,_activeData._activeCols);
    else 
      return expandToFullArray(beta, _activeData.activeCols(), _totalBetaLength, _nbetas, _betaLengthPerClass);
  }
  
  public static class GramGrad {
    public double[][] _gram;
    public double[] beta;
    public double[] _grad;
    public double objective;
    public double _sumOfRowWeights;
    public double[] _xy;
    
    public GramGrad(double[][] gramM, double[] grad, double[] b, double obj, double sumOfRowWeights, double[] xy) {
      _gram = gramM;
      beta = b;
      _grad = grad;
      objective = obj;
      _sumOfRowWeights = sumOfRowWeights;
      _xy = xy;
    }

    public Gram.Cholesky cholesky(Gram.Cholesky chol, double[][] xx) {
      if( chol == null ) {
        for( int i = 0; i < xx.length; ++i )
          xx[i] = xx[i].clone();
        chol = new Gram.Cholesky(xx, new double[0]);
      }
      final Gram.Cholesky fchol = chol;
      final int sparseN = 0;
      final int denseN = xx.length - sparseN;
      // compute the cholesky of the diagonal and diagonal*dense parts
      ForkJoinTask[] fjts = new ForkJoinTask[denseN];
      // compute the outer product of diagonal*dense
      //Log.info("SPARSEN = " + sparseN + "    DENSEN = " + denseN);
      final int[][] nz = new int[denseN][];
      for( int i = 0; i < denseN; ++i ) {
        final int fi = i;
        fjts[i] = new RecursiveAction() {
          @Override protected void compute() {
            int[] tmp = new int[sparseN];
            double[] rowi = fchol._xx[fi];
            int n = 0;
            for( int k = 0; k < sparseN; ++k )
              if (rowi[k] != .0) tmp[n++] = k;
            nz[fi] = Arrays.copyOf(tmp, n);
          }
        };
      }
      ForkJoinTask.invokeAll(fjts);
      for( int i = 0; i < denseN; ++i ) {
        final int fi = i;
        fjts[i] = new RecursiveAction() {
          @Override protected void compute() {
            double[] rowi = fchol._xx[fi];
            int[]    nzi  = nz[fi];
            for( int j = 0; j <= fi; ++j ) {
              double[] rowj = fchol._xx[j];
              int[]    nzj  = nz[j];
              double s = 0;
              for (int t=0,z=0; t < nzi.length && z < nzj.length; ) {
                int k1 = nzi[t];
                int k2 = nzj[z];
                if (k1 < k2) { t++; continue; }
                else if (k1 > k2) { z++; continue; }
                else {
                  s += rowi[k1] * rowj[k1];
                  t++; z++;
                }
              }
              rowi[j + sparseN] = xx[fi][j + sparseN] - s;
            }
          }
        };
      }
      ForkJoinTask.invokeAll(fjts);
      // compute the cholesky of dense*dense-outer_product(diagonal*dense)
      double[][] arr = new double[denseN][];
      for( int i = 0; i < arr.length; ++i )
        arr[i] = Arrays.copyOfRange(fchol._xx[i], sparseN, sparseN + denseN);
      final int p = H2ORuntime.availableProcessors();
      Gram.InPlaceCholesky d = Gram.InPlaceCholesky.decompose_2(arr, 10, p);
      fchol.setSPD(d.isSPD());
      arr = d.getL();
      for( int i = 0; i < arr.length; ++i ) {
        // See PUBDEV-5585: we use a manual array copy instead of System.arraycopy because of behavior on Java 10
        // Used to be: System.arraycopy(arr[i], 0, fchol._xx[i], sparseN, i + 1);
        for (int j = 0; j < i + 1; j++)
          fchol._xx[i][sparseN + j] = arr[i][j];
      }

      return chol;
    }

    public Gram.Cholesky qrCholesky(List<Integer> dropped_cols, double[][] Z, boolean standardized) {
      final double [][] R = new double[Z.length][];
      final double [] Zdiag = new double[Z.length];
      final double [] ZdiagInv = new double[Z.length];
      for(int i = 0; i < Z.length; ++i)
        ZdiagInv[i] = 1.0/(Zdiag[i] = Z[i][i]);
      for(int j = 0; j < Z.length; ++j) {
        final double [] gamma = R[j] = new double[j+1];
        for(int l = 0; l <= j; ++l) // compute gamma_l_j
          gamma[l] = Z[j][l]*ZdiagInv[l];
        double zjj = Z[j][j];
        for(int k = 0; k < j; ++k) // only need the diagonal, the rest is 0 (dot product of orthogonal vectors)
          zjj += gamma[k] * (gamma[k] * Z[k][k] - 2*Z[j][k]);
        // Check R^2 for the current column and ignore if too high (1-R^2 too low), R^2 = 1- rs_res/rs_tot
        // rs_res = zjj (the squared residual)
        // rs_tot = sum((yi - mean(y))^2) = mean(y^2) - mean(y)^2,
        //   mean(y^2) is on diagonal
        //   mean(y) is in the intercept (0 if standardized)
        //   might not be regularized with number of observations, that's why dividing by intercept diagonal
        double rs_tot = standardized
                ?ZdiagInv[j]
                :1.0/(Zdiag[j]-Z[j][0]*ZdiagInv[0]*Z[j][0]);
        if (j > 0 && zjj*rs_tot < R2_EPS) {
          zjj=0;
          dropped_cols.add(j-1);
          ZdiagInv[j] = 0;
        } else {
          ZdiagInv[j] = 1. / zjj;
        }
        Z[j][j] = zjj;
        int jchunk = Math.max(1,MIN_PAR/(Z.length-j));
        int nchunks = (Z.length - j - 1)/jchunk;
        nchunks = Math.min(nchunks, H2O.NUMCPUS);
        if(nchunks <= 1) { // single threaded update
          updateZ(gamma,Z,j);
        } else { // multi-threaded update
          final int fjchunk = (Z.length - 1 - j)/nchunks;
          int rem = Z.length - 1 - j - fjchunk*nchunks;
          for(int i = Z.length-rem; i < Z.length; ++i)
            updateZij(i,j,Z,gamma);
          RecursiveAction[] ras = new RecursiveAction[nchunks];
          final int fj = j;
          int k = 0;
          for (int i = j + 1; i < Z.length-rem; i += fjchunk) { // update xj to zj //
            final int fi = i;
            ras[k++] = new RecursiveAction() {
              @Override
              protected final void compute() {
                int max_i = Math.min(fi+fjchunk,Z.length);
                for(int i = fi; i < max_i; ++i)
                  updateZij(i,fj,Z,gamma);
              }
            };
          }
          ForkJoinTask.invokeAll(ras);
        }
      }
      // update the R - we computed Rt/sqrt(diag(Z)) which we can directly use to solve the problem
      if(R.length < 500)
        for(int i = 0; i < R.length; ++i)
          for (int j = 0; j <= i; ++j)
            R[i][j] *= Math.sqrt(Z[j][j]);
      else {
        RecursiveAction[] ras = new RecursiveAction[R.length];
        for(int i = 0; i < ras.length; ++i) {
          final int fi = i;
          final double [] Rrow = R[i];
          ras[i] = new RecursiveAction() {
            @Override
            protected void compute() {
              for (int j = 0; j <= fi; ++j)
                Rrow[j] *= Math.sqrt(Z[j][j]);
            }
          };
        }
        ForkJoinTask.invokeAll(ras);
      }
      // deal with dropped_cols if present
      if (dropped_cols.isEmpty()) 
        return new Gram.Cholesky(R, new double[0], true);
      else
        return new Gram.Cholesky(dropIgnoredCols(R, Z, dropped_cols),new double[0], true);
    }
    
    public static double[][] dropIgnoredCols(double[][] R, double[][] Z, List<Integer> dropped_cols) {
      double[][] Rnew = new double[R.length-dropped_cols.size()][];
      for(int i = 0; i < Rnew.length; ++i)
        Rnew[i] = new double[i+1];
      int j = 0;
      for(int i = 0; i < R.length; ++i) {
        if(Z[i][i] == 0) continue;
        int k = 0;
        for(int l = 0; l <= i; ++l) {
          if(k < dropped_cols.size() && l == (dropped_cols.get(k)+1)) {
            ++k;
            continue;
          }
          Rnew[j][l - k] = R[i][l];
        }
        ++j;
      }
      return Rnew;
    }

    private final void updateZij(int i, int j, double [][] Z, double [] gamma) {
      double [] Zi = Z[i];
      double Zij = Zi[j];
      for (int k = 0; k < j; ++k)
        Zij -= gamma[k] * Zi[k];
      Zi[j] = Zij;
    }
    private final void updateZ(final double [] gamma, final double [][] Z, int j){
      for (int i = j + 1; i < Z.length; ++i)  // update xj to zj //
        updateZij(i,j,Z,gamma);
    }

    public static double[][] dropCols(int[] cols, double[][] xx) {
      Arrays.sort(cols);
      int newXXLen = xx.length-cols.length;
      double [][] xxNew = new double[newXXLen][newXXLen];
      int oldXXLen = xx.length;
      List<Integer> newIndices = IntStream.range(0, newXXLen).boxed().collect(Collectors.toList());
      for (int index:cols)
        newIndices.add(index,-1);
      int newXindexX, newXindexY;
      for (int rInd=0; rInd<oldXXLen; rInd++) {
        newXindexX = newIndices.get(rInd);
        for (int cInd=rInd; cInd<oldXXLen; cInd++) {
          newXindexY = newIndices.get(cInd);
          if (newXindexY >= 0 && newXindexX >= 0) {
            xxNew[newXindexX][newXindexY] = xx[rInd][cInd];
            xxNew[newXindexY][newXindexX] = xx[cInd][rInd];
          }
        }
      }
      return xxNew;
    }

    public static int[] findZeroCols(double[][] xx){
      ArrayList<Integer> zeros = new ArrayList<>();
      for(int i = 0; i < xx.length; ++i) {
        if (sum(xx[i]) == 0)
          zeros.add(i);
      }
      if(zeros.size() == 0) return new int[0];
      int [] ary = new int[zeros.size()];
      for(int i = 0; i < zeros.size(); ++i)
        ary[i] = zeros.get(i);
      return ary;
    }
  }

  /**
   * Cached state of COD (with covariate updates) solver.
   */
  public static final class GramXY {
    public final Gram gram;
    final double[] beta;
    final int[] activeCols;
    int [] newCols;
    public final double[] xy;
    private double [] grads;
    public double yy;
    public final double likelihood;
    public double sumOfRowWeights;  // sum of all r.weight



    public GramXY(Gram gram, double[] xy, double [] grads, double[] beta, int[] activeCols, int [] newActiveCols, double yy, double likelihood) {
      this.gram = gram;
      this.xy = xy;
      this.grads = grads;
      this.beta = beta == null ? null : beta.clone();
      this.activeCols = activeCols == null ? null : activeCols.clone();
      this.newCols = newActiveCols;
      this.yy = yy;
      this.likelihood = likelihood;
    }

    public final double [] getCODGradients(){
      if(grads == null){
        double [][] xx = gram.getXX();
        grads = new double[xy.length];
        for(int i = 0; i < grads.length; ++i)
          grads[i] = xy[i] - ArrayUtils.innerProduct(xx[i], beta) + xx[i][i] * beta[i];
      }
      if(newCols != null) {
        double [][] xx = gram.getXX();
        for (int i : newCols)
          grads[i] = xy[i] - ArrayUtils.innerProduct(xx[i], beta) + xx[i][i] * beta[i];
      }
      return grads;
    }

    public boolean match(double[] beta, int[] activeCols) {
      return Arrays.equals(this.beta, beta) && Arrays.equals(this.activeCols, activeCols);
    }

    static double [] mergeRow(int k, double [] xrowOld, double [] xrow,int [] newColsIds, double [][] xxUpdate){
      for(int i = 0; i < newColsIds.length; ++i){
        int j = newColsIds[i];
        xrow[j] = xxUpdate[i][k];
        for(int l = i == 0?0:newColsIds[i-1]+1; l < j; ++l)
          xrow[l] = xrowOld[l-i];
      }
      int l = newColsIds.length;
      for(int j = newColsIds[newColsIds.length-1]+1; j < xrow.length; ++j)
        xrow[j] = xrowOld[j-l];
      return xrow;
    }
    public static GramXY addCols(double[] beta, final int[] newActiveCols, final int[] newColsIds, final GramXY oldGram, final double[][] xxUpdate, final double[] xyUpdate) {
      // update the expanded matrix cache
      final double[][] xxCacheNew = new double[newActiveCols.length][];
      final double[] xyNew = new double[xxCacheNew.length];
      final double[] gradsNew = oldGram.grads == null?null:new double[xxCacheNew.length];
      double [][] xx = oldGram.gram.getXX();
      for (int k = 0; k < newColsIds.length; ++k) {
        int j = newColsIds[k];
        xxCacheNew[j] = xxUpdate[k];
        xyNew[j] = xyUpdate[k];
        for (int i = k == 0 ? 0 : newColsIds[k - 1] + 1; i < j; i++) {
          xxCacheNew[i] = mergeRow(i, xx[i - k], new double[newActiveCols.length], newColsIds, xxUpdate);
          xyNew[i] = oldGram.xy[i - k];
          if(oldGram.grads != null)gradsNew[i] = oldGram.grads[i - k];
        }
      }
      int k = newColsIds.length;
      for (int i = newColsIds[newColsIds.length - 1] + 1; i < xyNew.length; ++i) {
        xxCacheNew[i] = mergeRow(i, xx[i - k], new double[newActiveCols.length], newColsIds, xxUpdate);
        xyNew[i] = oldGram.xy[i - k];
        if(oldGram.grads != null)gradsNew[i] = oldGram.grads[i - k];
      }
      return new GramXY(new Gram(xxCacheNew), xyNew, gradsNew, beta, newActiveCols, newColsIds, oldGram.yy, oldGram.likelihood);
    }
  }

  protected GramXY computeNewGram(DataInfo activeData, double [] beta, GLMParameters.Solver s){
    double obj_reg = _parms._obj_reg;
    if(_glmw == null) _glmw = new GLMModel.GLMWeightsFun(_parms);
    GLMTask.GLMIterationTask gt = new GLMTask.GLMIterationTask(_job._key, activeData, _glmw, beta,
            _activeClass).doAll(activeData._adaptedFrame);
    gt._gram.mul(obj_reg);
    if (_parms._glmType.equals(GLMParameters.GLMType.gam)) { // add contribution from GAM smoothness factor
        Integer[] activeCols=null;
        int[] activeColumns = activeData.activeCols();
        if (activeColumns.length<_dinfo.fullN()) { // columns are deleted
          activeCols = ArrayUtils.toIntegers(activeColumns, 0, activeColumns.length);
        }
        gt._gram.addGAMPenalty(activeCols , _penaltyMatrix, _gamBetaIndices);
    }
    mult(gt._xy,obj_reg);
    int [] activeCols = activeData.activeCols();
    int [] zeros = gt._gram.findZeroCols();
    GramXY res;
    if(_parms._family != Family.multinomial && zeros.length > 0 && zeros.length <= activeData.activeCols().length) {
      gt._gram.dropCols(zeros);
      removeCols(zeros);
      res = new ComputationState.GramXY(gt._gram,ArrayUtils.removeIds(gt._xy, zeros),null,gt._beta == null?null:ArrayUtils.removeIds(gt._beta, zeros),activeData().activeCols(),null,gt._yy,gt._likelihood);
    } else res = new GramXY(gt._gram,gt._xy,null, beta,activeCols,null,gt._yy,gt._likelihood);
    if (gaussian.equals(_parms._family))
      res.sumOfRowWeights = gt.sumOfRowWeights;
    return res;
  }

  GramXY _currGram;
  GLMModel.GLMWeightsFun _glmw;

  /***
   * This method is used only for multinomial family.  It differs from computeGram because it calls on _activeData
   * which only contains only active columns in its _adaptedFrame.  Note activeDataMultinomial(_activeClass) will
   * always contains all predictors in its _adaptedFrame.
   * @param beta
   * @param s
   * @return
   */
  public GramXY computeGramRCC(double[] beta, GLMParameters.Solver s) {
      return computeNewGram(_activeData, ArrayUtils.select(beta, _activeData.activeCols()), s);
  }

  /***
   * This function calculates the following values:
   * 1. the hessian
   * 2. the xy which is basically (hessian * old_beta + gradient)
   */
  protected GramGrad computeGram(double [] beta, GLMGradientInfo gradientInfo){
    DataInfo activeData = activeData();
    double obj_reg = _parms._obj_reg;
    if(_glmw == null) _glmw = new GLMModel.GLMWeightsFun(_parms);
    GLMTask.GLMIterationTask gt = new GLMTask.GLMIterationTask(_job._key, activeData, _glmw, beta,
            _activeClass).doAll(activeData._adaptedFrame);
    double[][] fullGram = gt._gram.getXX(); // only extract gram matrix
    mult(fullGram, obj_reg);
    if (_gramEqual != null)
      elementwiseSumSymmetricArrays(fullGram, mult(sumGramConstribution(_gramEqual, fullGram.length), _csGLMState._ckCS));
    if (_gramLess != null)
      elementwiseSumSymmetricArrays(fullGram, mult(sumGramConstribution(_gramLess, fullGram.length), _csGLMState._ckCS));
    if (_parms._glmType.equals(GLMParameters.GLMType.gam)) { // add contribution from GAM smoothness factor
      gt._gram.addGAMPenalty(_penaltyMatrix, _gamBetaIndices, fullGram);
    }
    // form xy which is (Gram*beta_current + gradient)
    double[] xy = formXY(fullGram, beta, gradientInfo._gradient);
    // remove zeros in Gram matrix and throw an error if that coefficient is included in the constraint
    int[] zeros = findZeroCols(fullGram);
    if (_parms._family != Family.multinomial && zeros.length > 0 && zeros.length <= activeData.activeCols().length) {
      fullGram = GramGrad.dropCols(zeros, fullGram); // shrink gram matrix
      removeCols(zeros);  // update activeData.activeCols(), _beta
      return new GramGrad(fullGram, ArrayUtils.removeIds(gradientInfo._gradient, zeros), 
              ArrayUtils.removeIds(beta, zeros), gradientInfo._objVal, gt.sumOfRowWeights, ArrayUtils.removeIds(xy, zeros));
    }
    return new GramGrad(fullGram, gradientInfo._gradient, beta, gradientInfo._objVal, gt.sumOfRowWeights, xy);
  }

  /***
   * 
   * This method adds to objective function the contribution of 
   *    transpose(lambda)*constraint vector + ck/2*transpose(constraint vector)*constraint vector
   */
  public static double addConstraintObj(double[] lambda, LinearConstraints[] constraints, double ckHalf) {
    int numConstraints = constraints.length;
    LinearConstraints oneC;
    double objValueAdd = 0;
    for (int index=0; index<numConstraints; index++) {
      oneC = constraints[index];
      if (oneC._active) {
        objValueAdd += lambda[index]*oneC._constraintsVal;               // from linear constraints
        objValueAdd += ckHalf*oneC._constraintsVal*oneC._constraintsVal; // from penalty
      }
    }
    return objValueAdd;
  }
  
  public static double[] formXY(double[][] fullGram, double[] beta, double[] grad) {
    int len = grad.length;
    double[] xy = new double[len];
    multArrVec(fullGram, beta, xy);
    return IntStream.range(0, len).mapToDouble(x -> xy[x]-grad[x]).toArray();
  }
  


  // get cached gram or incrementally update or compute new one
  public GramXY computeGram(double [] beta, GLMParameters.Solver s){
    double obj_reg = _parms._obj_reg;
    boolean weighted = !gaussian.equals(_parms._family) || !GLMParameters.Link.identity.equals(_parms._link);
    if(Family.multinomial.equals(_parms._family)) // no caching
      return computeNewGram(activeDataMultinomial(_activeClass),beta,s); // activeDataMultinomial(_activeClass) returns all predictors
    if(s != GLMParameters.Solver.COORDINATE_DESCENT)
      // only cache for solver==COD
      //    caching only makes difference when running with lambda search
      //    and COD and IRLSM need matrix in different shape
      //    and COD is better for lambda search
      return computeNewGram(activeData(),beta,s);
    if(_currGram == null) // no cached value, compute new one and store
      return _currGram = computeNewGram(activeData(),beta,s);
    DataInfo activeData = activeData();
    assert beta == null || beta.length == activeData.fullN()+1;
    int [] activeCols = activeData.activeCols();
    if (Arrays.equals(_currGram.activeCols,activeCols))
      return (!weighted || Arrays.equals(_currGram.beta, beta)) ? _currGram : (_currGram = computeNewGram(activeData,
              beta, s));
    if(_glmw == null) _glmw = new GLMModel.GLMWeightsFun(_parms);
    // check if we need full or just incremental update
    if(_currGram != null){
      int [] newCols = ArrayUtils.sorted_set_diff(activeCols,_currGram.activeCols);
      int [] newColsIds = newCols.clone();
      int jj = 0;
      boolean matches = true;
      int k = 0;
      for (int i = 0; i < activeCols.length; ++i) {
        if (jj < newCols.length && activeCols[i] == newCols[jj]) {
          newColsIds[jj++] = i;
          matches = matches && (beta == null || beta[i] == 0);
        } else {
          matches = matches && (beta == null || beta[i] == _currGram.beta[k++]);
        }
      }
      if(!weighted || matches) {
        GLMTask.GLMIncrementalGramTask gt = new GLMTask.GLMIncrementalGramTask(newColsIds, activeData, _glmw, beta).doAll(activeData._adaptedFrame); // dense
        for (double[] d : gt._gram)
          mult(d, obj_reg);
        mult(gt._xy, obj_reg);
        // glue the update and old gram together
        return _currGram = GramXY.addCols(beta, activeCols, newColsIds, _currGram, gt._gram, gt._xy);
      }
    }
    return _currGram = computeNewGram(activeData,beta,s);
  }
  
  public void setConstraintInfo(GLMGradientInfo gradientInfo, LinearConstraints[] equalityConstraints, 
                                LinearConstraints[] lessThanEqualToConstraints, double[] lambdaEqual, double[] lambdaLessThan) {
    _ginfo = gradientInfo;
    _lessThanEqualToConstraints = lessThanEqualToConstraints;
    _equalityConstraints = equalityConstraints;
    _lambdaEqual = lambdaEqual;
    _lambdaLessThanEqualTo = lambdaLessThan;
    _likelihood = gradientInfo._likelihood;
  }
}

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
import water.H2O;
import water.Job;
import water.MemoryManager;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

import static hex.glm.GLMUtils.calSmoothNess;
import static hex.glm.GLMUtils.copyGInfo;

public final class ComputationState {
  final boolean _intercept;
  final int _nclasses;
  private final GLMParameters _parms;
  private BetaConstraint _bc;
  double _alpha;
  double[] _ymu;
  double [] _u;
  double [] _z;
  boolean _allIn;
  int _iter;
  int _iterHGLM_GLMMME; // keep track of iterations used in estimating fixed/random coefficients
  private double _lambda = 0;
  private double _lambdaMax = Double.NaN;
  private GLMGradientInfo _ginfo; // gradient info excluding l1 penalty
  private double _likelihood;
  private double _gradientErr;
  private boolean _lambdaNull; // true if lambda was not provided by user
  private double _gMax; // store max value of original gradient without dividing by math.max(1e-2, _parms._alpha[0])
  private DataInfo _activeData;
  private BetaConstraint _activeBC = null;
  private double[] _beta; // vector of coefficients corresponding to active data
  private double[] _ubeta;  // HGLM, store coefficients of random effects;
  private double[] _psi; // HGLM, psi
  private double[] _phi; // HGLM, size random columns levels
  private double _tau; // HGLM for ei
  private double _correction_HL; // HGLM
  double[] _sumEtaSquareConvergence;  // HGLM: sotre sumEtaSquare, convergence
  double[] _likelihoodInfo; // HGLM: stores 4 elements: hlik, pvh, pbvh, cAIC
  public String[] _randCoeffNames; // store random coefficient names
  private Frame _priorw_wpsi;  // weight calculated for psi
  final DataInfo _dinfo;
  private GLMGradientSolver _gslvr;
  private final Job _job;
  private int _activeClass = -1;
  double[][][] _penaltyMatrix;
  int[][] _gamBetaIndices;
  int _totalBetaLength; // actual coefficient length without taking into account active columns only
  int _betaLengthPerClass;

  /**
   *
   * @param nclasses - number of classes for multinomial, 1 for everybody else
   */
  public ComputationState(Job job, GLMParameters parms, DataInfo dinfo, BetaConstraint bc, int nclasses){
    _job = job;
    _parms = parms;
    _bc = bc;
    _activeBC = _bc;
    _dinfo = dinfo;
    _activeData = _dinfo;
    _intercept = _parms._intercept;
    _nclasses = Family.fractionalbinomial == parms._family ? 2 :
            ((Family.multinomial == parms._family || Family.ordinal == parms._family) ? nclasses : 1);
    _alpha = _parms._alpha[0];
    _totalBetaLength = (dinfo.fullN()+1)*_nclasses;
    _betaLengthPerClass = dinfo.fullN()+1;
    if (_parms._HGLM) {
      _sumEtaSquareConvergence = new double[2];
      if (_parms._calc_like)
        _likelihoodInfo = new double[4];
    }
  }

  public ComputationState(Job job, GLMParameters parms, DataInfo dinfo, BetaConstraint bc, int nclasses, 
                          double[][][] penaltyMat, int[][] gamColInd){
    this (job, parms, dinfo, bc, nclasses);
    _penaltyMatrix = penaltyMat;
    _gamBetaIndices = gamColInd;
    _lambdaNull = (_parms._lambda==null) && !(_parms._lambda_search);
  }
  
  // copy over parameters from _model to _state for checkpointing
  // jest of this method is to restore the _state to be the same as before
  void copyCheckModel2State(GLMModel model, int[][] _gamColIndices) {
    GLMModel.GLMOutput modelOutput = model._output;
    int submodelInd;
    int coefLen = _nclasses > 2 ? (_dinfo.fullN() + 1) * _nclasses : (_dinfo.fullN() + 1);
    if (modelOutput._submodels.length > 1)  // lambda search or multiple alpha/lambda cases
      submodelInd = modelOutput._submodels.length - 1; // submodel where the model building ends
    else  // no lambda search or multiple alpha/lambda case
      submodelInd = 0;

    setIter(modelOutput._submodels[submodelInd].iteration);
    setAlpha(modelOutput._submodels[submodelInd].alpha_value);

    if (submodelInd > 0) {
      int preCurrSubmodelInd = Family.gaussian.equals(_parms._family) ? submodelInd : (submodelInd - 1);
      _activeData._activeCols = modelOutput._submodels[preCurrSubmodelInd].idxs;
      double[] betaExpand = Family.multinomial.equals(_parms._family)
              ? ArrayUtils.expandAndScatter(modelOutput._submodels[preCurrSubmodelInd].beta, coefLen, _activeData._activeCols)
              : expandBeta(modelOutput._submodels[preCurrSubmodelInd].beta);
      GLMGradientInfo ginfo = new GLMGradientSolver(_job, _parms, _dinfo, 0, activeBC(), _penaltyMatrix,
              _gamColIndices).getGradient(betaExpand);  // gradient obtained with zero penalty

      _activeData._activeCols = null;
      updateState(betaExpand, ginfo);
      setLambdaSimple(_parms._lambda[preCurrSubmodelInd]);
    }
    // this part must be done for single model before setting coefficients
    if (!Family.gaussian.equals(_parms._family))  // will build for new lambda for gaussian
      setLambda(modelOutput._submodels[submodelInd].lambda_value);

    // update _state with last submodelInd coefficients
    double[] expandedBeta = modelOutput._submodels[submodelInd].idxs == null
            ? modelOutput._submodels[submodelInd].beta
            : ArrayUtils.expandAndScatter(modelOutput._submodels[submodelInd].beta, coefLen,
            modelOutput._submodels[submodelInd].idxs);
    GLMGradientInfo ginfo = new GLMGradientSolver(_job, _parms, _dinfo, 0, activeBC(),
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

  public void set_sumEtaSquareConvergence(double[] sumInfo) {
    _sumEtaSquareConvergence = sumInfo;
  }

  /***
   * Copy GLM coefficients stored in beta to _beta of computationState
   * @param beta: store coefficients to be copied from
   * @param startIdx: first index of beta to copy from
   * @param len: length of coefficients to copy from beta
   * @param interceptFirst: true if the first index of beta stored the intercept term
   */
  public void set_beta_HGLM(double[] beta, int startIdx, int len, boolean interceptFirst) {
    if (_beta==null)
      _beta = new double[len];
    if (interceptFirst) {
      int lastIndex = len-1;
      System.arraycopy(beta, startIdx+1, _beta, 0, lastIndex);
      _beta[lastIndex] = beta[startIdx];
    } else {
      System.arraycopy(beta, startIdx, _beta, 0, len);
    }
  }
  
  public void set_likelihoodInfo(double hlik, double pvh, double pbvh, double cAIC) {
    _likelihoodInfo[0] = hlik;
    _likelihoodInfo[1] = pvh;
    _likelihoodInfo[2] = pbvh;
    _likelihoodInfo[3] = cAIC;
  }
  
  public void set_ubeta_HGLM(double[] ubeta, int startIdx, int len) {
    if (_ubeta==null)
      _ubeta = new double[len];
    System.arraycopy(ubeta, startIdx, _ubeta, 0, len);
  }
  
  public double[] get_psi() {
    return _psi;
  }

  public double get_correction_HL() {
    return _correction_HL;
  }
  
  public double[] get_phi() {
    return _phi;
  }
  
  public Frame get_priorw_wpsi() {
    return _priorw_wpsi;
  }
  
  public double get_tau() {
    return _tau;
  }
  
  public boolean getLambdaNull() { return _lambdaNull; }

  public void set_tau(double tau) {
    _tau=tau;
  }
  
  public void set_psi(double[] psi) {
    assert _psi.length==psi.length:"Length of _psi and psi should be the same.";
    System.arraycopy(psi, 0, _psi, 0, psi.length);
  }

  public void set_phi(double[] phi) {
    assert _phi.length==phi.length:"Length of _phi and phi should be the same.";
    System.arraycopy(phi, 0, _phi, 0, phi.length);
  }

  public GLMGradientSolver gslvr(){return _gslvr;}
  public double lambda(){return _lambda;}
  public double alpha() {return _alpha;}
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
      _gslvr = new GLMGradientSolver(_job, _parms, _activeData, l2pen(), _activeBC);
    else
      _gslvr = new GLMGradientSolver(_job, _parms, _activeData, l2pen(), _activeBC, _penaltyMatrix, _gamBetaIndices);
    adjustToNewLambda(lambda, 0);
  }
  
  public double [] beta(){
    if(_activeClass != -1)
      return betaMultinomial(_activeClass,_beta);
    return _beta;
  }
  public double[] ubeta(){
    return _ubeta;  // could be null.  Be careful
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
      l2pen = l2pen/_nclasses;   // need only one set of parameters

    if(l2pen > 0) {
      if (_ginfo == null) _ginfo = ginfo();
      if(_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
        l2pen = 0;
        int off = 0;
        for(int c = 0; c < _nclasses; ++c) {
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
    _allIn = _allIn || _alpha*lambdaNew == 0 || _activeBC.hasBounds();
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
        _gslvr = _penaltyMatrix == null ? new GLMGradientSolver(_job,_parms,_activeData,(1-_alpha)*_lambda,_bc) 
                : new GLMGradientSolver(_job, _parms, _dinfo, (1 - _alpha) * _lambda, _bc, _penaltyMatrix,
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
   
   public double[] shrinkFullArray(double[] fullArray) {
     if (_activeData.activeCols() == null)
       return fullArray;
     int[] activeColsAllClass = genActiveColsAllClass(_activeData.activeCols().length*_nclasses, 
             _betaLengthPerClass);
     return ArrayUtils.select(fullArray, activeColsAllClass);
   }
   
   public int[] genActiveColsAllClass(int activeColsLen, int numBetaPerClass) {
     int[] activeCols = new int[activeColsLen];
     int offset = 0;
     int[] activeColsOneClass = _activeData._activeCols;
     for (int classIndex=0; classIndex < _nclasses; classIndex++) {
       int finalOffset = numBetaPerClass*classIndex;
       int[] activeCols1Class = IntStream.of(activeColsOneClass).map(i->i+finalOffset).toArray();
       int num2Copy = activeColsOneClass.length;
       System.arraycopy(activeCols1Class, 0, activeCols, offset, num2Copy);
       offset += num2Copy;
     }
     return activeCols;
   }

  public GLMSubsetGinfo ginfoMultinomial(int c) {
    return new GLMSubsetGinfo(_ginfo,(_activeData.fullN()+1),c,_activeDataMultinomial[c].activeCols());
  }

  public void setBC(BetaConstraint bc) {
    _bc = bc;
    _activeBC = _bc;
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
  public static class GLMSubsetGinfo extends GLMGradientInfo {
    public final GLMGradientInfo _fullInfo;
    public GLMSubsetGinfo(GLMGradientInfo fullInfo, int N, int c, int [] ids) {
      super(fullInfo._likelihood, fullInfo._objVal, extractSubRange(N,c,ids,fullInfo._gradient));
      _fullInfo = fullInfo;
    }
  }
  public GradientSolver gslvrMultinomial(final int c) {
    final double [] fullbeta = _beta.clone();
    return new GradientSolver() {
      // beta is full coeff Per class.  Need to return gradient with full columns
      @Override
      public GradientInfo getGradient(double[] beta) {
        // fill fullbeta with new values of beta for class c
        fillSubRange(_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols(),beta,fullbeta);
        GLMGradientInfo fullGinfo =  _gslvr.getGradient(fullbeta);  // may contain only active columns
        if (fullbeta.length > fullGinfo._gradient.length) {  // fullGinfo only contains gradient for active columns here
          double[] fullGinfoGradient = new double[fullbeta.length];
          for (int cInd = 0; cInd < _nclasses; cInd++)
            fillSubRange(beta.length, cInd, _activeData.activeCols(), fullGinfo._gradient, fullGinfoGradient);
          fullGinfo._gradient = fullGinfoGradient;
        }
        return new GLMSubsetGinfo(fullGinfo,beta.length,c,_activeData.activeCols());
          //return new GLMSubsetGinfo(fullGinfo,_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols());
      }
      @Override
      public GradientInfo getObjective(double[] beta) {return getGradient(beta);}
    };
  }

  public void setBetaMultinomial(int c, double [] beta, double [] bc) {
    if(_u != null) Arrays.fill(_u,0);
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
        _activeDataMultinomial = new DataInfo[_nclasses];
      final double rhs = _alpha * (2 * lambdaNew - lambdaOld);
      int[] oldActiveCols = _activeData._activeCols == null ? new int[0] : _activeData.activeCols();
      int [] cols = MemoryManager.malloc4(N*_nclasses);
      int j = 0;

      for(int c = 0; c < _nclasses; ++c) {
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
        _activeDataMultinomial = new DataInfo[_nclasses];
      final double rhs = _alpha * (2 * lambdaNew - lambdaOld);
      int [] cols = MemoryManager.malloc4(N*_nclasses);

      int oldActiveColsTotal = 0;
      for(int c = 0; c < _nclasses; ++c) {
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
      if(_parms._max_active_predictors != -1 && _parms._max_active_predictors - oldActiveColsTotal + _nclasses < selected) {
        Integer[] bigInts = ArrayUtils.toIntegers(cols, 0, selected);
        Arrays.sort(bigInts, new Comparator<Integer>() {
          @Override
          public int compare(Integer o1, Integer o2) {
            return (int) Math.signum(_ginfo._gradient[o2.intValue()] * _ginfo._gradient[o2.intValue()] - _ginfo._gradient[o1.intValue()] * _ginfo._gradient[o1.intValue()]);
          }
        });
        cols = ArrayUtils.toInt(bigInts, 0, _parms._max_active_predictors - oldActiveColsTotal + _nclasses);
        Arrays.sort(cols);
        selected = cols.length;
      }
      int i = 0;
      int [] cs = new int[P+1];
      int sum = 0;
      for(int c = 0; c < _nclasses; ++c){
        int [] classcols = cs;
        int[] oldActiveCols = _activeDataMultinomial[c] == null ? new int[]{P} : _activeDataMultinomial[c]._activeCols;
        int k = 0;
        while(i < selected && cols[i] < (c+1)*N)
          classcols[k++] = cols[i++]-c*N;
        classcols = ArrayUtils.sortedMerge(oldActiveCols,Arrays.copyOf(classcols,k));
        sum += classcols.length;
        _activeDataMultinomial[c] = _dinfo.filterExpandedColumns(classcols);
      }
      assert _parms._max_active_predictors == -1 || sum <= _parms._max_active_predictors + _nclasses:"sum = " + sum + " max_active_preds = " + _parms._max_active_predictors + ", nclasses = " + _nclasses;
      _allIn = sum == N*_nclasses;
    }
  }

  protected boolean checkKKTsMultinomial(){
    if(_activeData._activeCols == null) return true;
    throw H2O.unimpl();
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
      _gslvr = _penaltyMatrix == null ? new GLMGradientSolver(_job, _parms, _dinfo, (1 - _alpha) * _lambda, _bc)
              : new GLMGradientSolver(_job, _parms, _dinfo, (1 - _alpha) * _lambda, _bc, _penaltyMatrix, 
              _gamBetaIndices);
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
    for (int c : activeCols) // set the error tolerance to the highest error og included columns
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
                (1 - _alpha) * _lambda, _activeBC) : new GLMGradientSolver(_job, _parms, _activeData, 
                (1 - _alpha) * _lambda, _activeBC, _penaltyMatrix, _gamBetaIndices);
        return false;
      }
    }
    return true;
  }
  public int []  removeCols(int [] cols) {
    int [] activeCols = ArrayUtils.removeIds(_activeData.activeCols(),cols);
    if(_beta != null)
      _beta = ArrayUtils.removeIds(_beta,cols);
    if(_u != null)
      _u = ArrayUtils.removeIds(_u,cols);
    if(_ginfo != null && _ginfo._gradient != null)
      _ginfo._gradient = ArrayUtils.removeIds(_ginfo._gradient,cols);
    _activeData = _dinfo.filterExpandedColumns(activeCols);
    _activeBC = _bc.filterExpandedColumns(activeCols);
    _gslvr = _penaltyMatrix == null ? new GLMGradientSolver(_job, _parms, _activeData,
            (1 - _alpha) * _lambda, _activeBC) : new GLMGradientSolver(_job, _parms, _activeData,
            (1 - _alpha) * _lambda, _activeBC, _penaltyMatrix, _gamBetaIndices);
    _currGram = null;
    return activeCols;
  }

  private double penalty(double [] beta) {
    if(_lambda == 0) return 0;
    double l1norm = 0, l2norm = 0;
    if(_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
      int len = beta.length/_nclasses;
      assert len*_nclasses == beta.length;
      for(int c = 0; c < _nclasses; ++c) {
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
    return likelihood * _parms._obj_reg + gamVal + penalty(beta) + (_activeBC == null?0:_activeBC.proxPen(beta));
  }
  protected double  updateState(double [] beta, double likelihood) {
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

  protected double updateState(double [] beta,GLMGradientInfo ginfo){
    _betaDiff = ArrayUtils.linfnorm(_beta == null?beta:ArrayUtils.subtract(_beta,beta),false);
    double objOld = objective();
    if(_beta == null)_beta = beta.clone();
    else System.arraycopy(beta,0,_beta,0,beta.length);
    _ginfo = ginfo;
    _likelihood = ginfo._likelihood;
    return (_relImprovement = (objOld - objective())/Math.abs(objOld));
  }
  
  double getBetaDiff() {return _betaDiff;}
  double getGradientErr() {return _gradientErr;}
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

  protected void setHGLMComputationState(double [] beta, double[] ubeta, double[] psi, double[] phi, 
                                         double hlcorrection, double tau, Frame wpsi, String[] randCoeffNames){
    _beta = Arrays.copyOf(beta, beta.length);
    _ubeta = Arrays.copyOf(ubeta, ubeta.length);
    _randCoeffNames = Arrays.copyOf(randCoeffNames, randCoeffNames.length);
    _psi = Arrays.copyOf(psi, psi.length);
    _phi = Arrays.copyOf(phi, phi.length);
    _correction_HL = hlcorrection;
    _tau = tau;
    _priorw_wpsi = wpsi;  // store prior_weight and calculated wpsi value for coefficients of random columns
    _iterHGLM_GLMMME = 0;
  }
  
  public double [] expandBeta(double [] beta) { // for multinomials
    int fullCoefLen = (_dinfo.fullN() + 1) * _nclasses;
    if(_activeData._activeCols == null || beta.length == fullCoefLen)
      return beta;
    return ArrayUtils.expandAndScatter(beta, (_dinfo.fullN() + 1) * _nclasses,_activeData._activeCols);
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
    ArrayUtils.mult(gt._xy,obj_reg);
    int [] activeCols = activeData.activeCols();
    int [] zeros = gt._gram.findZeroCols();
    GramXY res;
    if(_parms._family != Family.multinomial && zeros.length > 0) {
      gt._gram.dropCols(zeros);
      removeCols(zeros);
      res = new ComputationState.GramXY(gt._gram,ArrayUtils.removeIds(gt._xy, zeros),null,gt._beta == null?null:ArrayUtils.removeIds(gt._beta, zeros),activeData().activeCols(),null,gt._yy,gt._likelihood);
    } else res = new GramXY(gt._gram,gt._xy,null,beta == null?null:beta,activeCols,null,gt._yy,gt._likelihood);

    return res;
  }

  GramXY _currGram;
  GLMModel.GLMWeightsFun _glmw;


  // get cached gram or incrementally update or compute new one
  public GramXY computeGram(double [] beta, GLMParameters.Solver s){
    double obj_reg = _parms._obj_reg;
    boolean weighted = _parms._family != Family.gaussian || _parms._link != GLMParameters.Link.identity;
    if(_parms._family == Family.multinomial) // no caching
      return computeNewGram(activeDataMultinomial(_activeClass),beta,s);
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
          ArrayUtils.mult(d, obj_reg);
        ArrayUtils.mult(gt._xy, obj_reg);
        // glue the update and old gram together
        return _currGram = GramXY.addCols(beta, activeCols, newColsIds, _currGram, gt._gram, gt._xy);
      }
    }
    return _currGram = computeNewGram(activeData,beta,s);
  }
}

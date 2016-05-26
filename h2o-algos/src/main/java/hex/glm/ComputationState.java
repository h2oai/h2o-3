package hex.glm;

import hex.DataInfo;
import hex.glm.GLM.BetaConstraint;
import hex.glm.GLM.GLMGradientInfo;
import hex.glm.GLM.GLMGradientSolver;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.optimization.ADMM;
import hex.optimization.OptimizationUtils.GradientInfo;
import hex.optimization.OptimizationUtils.GradientSolver;
import water.H2O;
import water.Job;
import water.Key;
import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;

public final class ComputationState {
  final boolean _intercept;
  final int _nclasses;
  private final GLMParameters _parms;
  private BetaConstraint _bc;
  final double _alpha;
  double[] _ymu;
  double [] _u;
  double [] _z;
  boolean _allIn;
  int _iter;
  private double _lambda = 0;
  private double _lambdaMax = Double.NaN;
  private GLMGradientInfo _ginfo; // gradient info excluding l1 penalty
  private double _likelihood;
  private double _gradientErr;
  private DataInfo _activeData;
  private BetaConstraint _activeBC = null;
  private double[] _beta; // vector of coefficients corresponding to active data
  final DataInfo _dinfo;
  private GLMGradientSolver _gslvr;
  private final Job _job;
  private int _activeClass = -1;

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
    _nclasses = parms._family == Family.multinomial?nclasses:1;
    _alpha = _parms._alpha[0];
  }

  public GLMGradientSolver gslvr(){return _gslvr;}
  public double lambda(){return _lambda;}
  public void setLambdaMax(double lmax) {
    _lambdaMax = lmax;
  }
  public void setLambda(double lambda) {
    adjustToNewLambda(_lambda, 0);
    // strong rules are to be applied on the gradient with no l2 penalty
    // NOTE: we start with lambdaOld being 0, not lambda_max
    // non-recursive strong rules should use lambdaMax instead of _lambda
    // However, it seems tobe working nicely to use 0 instead and be more aggressive on the predictor pruning
    // (shoudl be safe as we check the KKTs anyways)
    applyStrongRules(lambda, _lambda);
    adjustToNewLambda(0, lambda);
    _lambda = lambda;
    _gslvr = new GLMGradientSolver(_job,_parms,_activeData,l2pen(),_activeBC);
  }
  public double [] beta(){
    if(_activeClass != -1)
      return betaMultinomial(_activeClass,_beta);
    return _beta;
  }
  public GLMGradientInfo ginfo(){return _ginfo == null?(_ginfo = gslvr().getGradient(beta())):_ginfo;}
  public BetaConstraint activeBC(){return _activeBC;}
  public double likelihood() {return _likelihood;}


  public DataInfo activeData(){
    if(_activeClass != -1)
      return activeDataMultinomial(_activeClass);
    return _activeData;
  }

  public DataInfo activeDataMultinomial(){return _activeData;}


  public void dropActiveData(){_activeData = null;}

  public String toString() {
    return "iter=" + _iter + " lmb=" + GLM.lambdaFormatter.format(_lambda) + " obj=" + MathUtils.roundToNDigits(objective(),4) + " imp=" + GLM.lambdaFormatter.format(_relImprovement) + " bdf=" + GLM.lambdaFormatter.format(_betaDiff);
  }

  private void adjustToNewLambda(double lambdaNew, double lambdaOld) {
    double ldiff = lambdaNew - lambdaOld;
    if(ldiff == 0 || l2pen() == 0) return;
    double l2pen = .5*ArrayUtils.l2norm2(_beta,true);
    if(l2pen > 0) {
      if(_parms._family == Family.multinomial) {
        int off = 0;
        for(int c = 0; c < _nclasses; ++c) {
          DataInfo activeData = activeDataMultinomial(c);
          for (int i = 0; i < activeData.fullN(); ++i)
            _ginfo._gradient[off+i] += ldiff * _beta[off+i];
          off += activeData.fullN()+1;
        }
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
  protected int applyStrongRules(double lambdaNew, double lambdaOld) {
    if(_parms._family == Family.multinomial)
      return applyStrongRulesMultinomial(lambdaNew,lambdaOld);
    int P = _dinfo.fullN();
    int newlySelected = 0;
    _activeBC = _bc;
    _activeData = _activeData != null?_activeData:_dinfo;
    _allIn = _allIn || _parms._alpha[0]*lambdaNew == 0 || _activeBC.hasBounds();
    if (!_allIn) {
      final double rhs = _alpha * (2 * lambdaNew - lambdaOld);
      int [] newCols = MemoryManager.malloc4(P);
      int j = 0;

      int[] oldActiveCols = _activeData._activeCols == null ? new int[0] : _activeData.activeCols();
      for (int i = 0; i < P; ++i) {
        if (j < oldActiveCols.length && i == oldActiveCols[j]) {
          ++j;
          newCols[newlySelected++] = i; // todo
        } else if (_ginfo._gradient[i] > rhs || -_ginfo._gradient[i] > rhs) {
          newCols[newlySelected++] = i;
        }
      }
      // merge already active columns in
      int active = newlySelected;
      _allIn = active == P;
      if(!_allIn) {
        int [] cols = newCols;
        cols[newlySelected++] = P; // intercept is always selected, even if it is false (it's gonna be dropped later, it is needed for other stuff too)
        cols = Arrays.copyOf(cols, newlySelected);
        _beta = ArrayUtils.select(_beta, cols);
        if(_u != null) _u = ArrayUtils.select(_u,cols);
        _activeData = _dinfo.filterExpandedColumns(cols);
        assert _activeData.activeCols().length == _beta.length;
        assert _u == null || _activeData.activeCols().length == _u.length;
        _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal, ArrayUtils.select(_ginfo._gradient, cols));
        _activeBC = _bc.filterExpandedColumns(_activeData.activeCols());
        _gslvr = new GLMGradientSolver(_job,_parms,_activeData,(1-_alpha)*_lambda,_bc);
        assert _beta.length == newlySelected;
        return newlySelected;
      }
    }
    _activeData = _dinfo;
    return _dinfo.fullN();
  }

  public boolean _lsNeeded = false;

  private DataInfo [] _activeDataMultinomial;
//  private int [] _classOffsets = new int[]{0};


  public DataInfo activeDataMultinomial(int c) {return _activeDataMultinomial != null?_activeDataMultinomial[c]:_dinfo;}

  private static double [] extractSubRange(int N, int c, int [] ids, double [] src) {
    if(ids == null) return Arrays.copyOfRange(src,c*N,c*N+N);
    double [] res = MemoryManager.malloc8d(ids.length);
    int j = 0;
    int off = c*N;
    for(int i:ids)
      res[j++] = src[off+i];
    return res;
  }

  private static void fillSubRange(int N, int c, int [] ids, double [] src, double [] dst) {
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

  public double [] betaMultinomial(int c, double [] beta) {return extractSubRange(_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols(),beta);}

  public GLMSubsetGinfo ginfoMultinomial(int c) {
    return new GLMSubsetGinfo(_ginfo,(_activeData.fullN()+1),c,_activeDataMultinomial[c].activeCols());
  }

  public void setBC(BetaConstraint bc) {
    _bc = bc;
    _activeBC = _bc;
  }

  public void setActiveClass(int activeClass) {_activeClass = activeClass;}

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
      @Override
      public GradientInfo getGradient(double[] beta) {
        fillSubRange(_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols(),beta,fullbeta);
        GLMGradientInfo fullGinfo =  _gslvr.getGradient(fullbeta);
        return new GLMSubsetGinfo(fullGinfo,_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols());
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
  protected int applyStrongRulesMultinomial(double lambdaNew, double lambdaOld) {
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

  protected boolean checkKKTsMultinomial(){
    if(_activeData._activeCols == null) return true;
    throw H2O.unimpl();
  }

  protected boolean checkKKTs() {
    if(_parms._family == Family.multinomial)
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
      _gslvr = new GLMGradientSolver(_job, _parms, _dinfo, (1 - _alpha) * _lambda, _bc);
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
    if(!_allIn) {
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
        _gslvr = new GLMGradientSolver(_job, _parms, _activeData, (1 - _alpha) * _lambda, _activeBC);
        return false;
      }
    }
    return true;
  }
  public int []  removeCols(int [] cols) {
    int [] activeCols = ArrayUtils.removeIds(_activeData.activeCols(),cols);
    if(_beta != null)
      _beta = ArrayUtils.removeIds(_beta,cols);
    if(_ginfo != null && _ginfo._gradient != null)
      _ginfo._gradient = ArrayUtils.removeIds(_ginfo._gradient,cols);
    _activeData = _dinfo.filterExpandedColumns(activeCols);
    _activeBC = _bc.filterExpandedColumns(activeCols);
    _gslvr = new GLMGradientSolver(_job, _parms, _activeData, (1 - _alpha) * _lambda, _activeBC);
    return activeCols;
  }

  private double penalty(double [] beta) {
    if(_lambda == 0) return 0;
    double l1norm = 0, l2norm = 0;
    if(_parms._family == Family.multinomial) {
      for(int c = 0; c < _nclasses; ++c) {

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
    return likelihood * _parms._obj_reg + penalty(beta) + (_activeBC == null?0:_activeBC.proxPen(beta));
  }
  protected double  updateState(double [] beta, double likelihood) {
    _betaDiff = ArrayUtils.linfnorm(_beta == null?beta:ArrayUtils.subtract(_beta,beta),false);
    double objOld = objective();
    _beta = beta;
    _ginfo = null;
    _likelihood = likelihood;
    return (_relImprovement = (objOld - objective())/objOld);
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

    return (_relImprovement = (objOld - objective())/objOld);
  }

  public double [] expandBeta(double [] beta) {
    if(_activeData._activeCols == null)
      return beta;
    return ArrayUtils.expandAndScatter(beta, _dinfo.fullN() + 1 * _nclasses,_activeData._activeCols);
  }

}

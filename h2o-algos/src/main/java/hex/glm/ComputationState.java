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
  boolean _allIn;
  int _iter;
  private double _lambda;
  private double _previousLambda;
  private GLMGradientInfo _ginfo; // gradient info excluding l1 penalty
  private double _likelihood;
  private double _gradientErr;
  private DataInfo _activeData;
  private BetaConstraint _activeBC = null;
  private double[] _beta; // vector of coefficients corresponding to active data
  final DataInfo _dinfo;
  private GLMGradientSolver _gslvr;
  private final Key _jobKey;

  /**
   *
   * @param nclasses - number of classes for multinomial, 1 for everybody else
   */
  public ComputationState(Key jobKey, GLMParameters parms, DataInfo dinfo, BetaConstraint bc,  int nclasses){
    _jobKey = jobKey;
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
  public double previousLambda() {return _previousLambda;}
  public void setLambda(double lambda) {
    _previousLambda = _lambda;
    _lambda = lambda;
    applyStrongRules();
    adjustToNewLambda();
    _gslvr = new GLMGradientSolver(_jobKey,_parms,_activeData,l2pen(),_activeBC);
  }
  public double [] beta(){
    return _beta;
  }
  public GLMGradientInfo ginfo(){return _ginfo == null?(_ginfo = gslvr().getGradient(beta())):_ginfo;}
  public BetaConstraint activeBC(){return _activeBC;}
  public double likelihood() {return _likelihood;}


  public DataInfo activeData(){return _activeData;}



  public void dropActiveData(){_activeData = null;}

  public String toString() {
    return "iter=" + _iter + " lmb=" + GLM.lambdaFormatter.format(_lambda) + " obj=" + MathUtils.roundToNDigits(objective(),4) + " imp=" + GLM.lambdaFormatter.format(_relImprovement) + " bdf=" + GLM.lambdaFormatter.format(_betaDiff);
  }

  private void adjustToNewLambda() {
    double ldiff = _lambda - _previousLambda;
    if(ldiff == 0) return;
    double l2pen = .5*l2pen();
    _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal + ldiff * l2pen, _ginfo._gradient);
  }

  public double l1pen() {return _alpha*_lambda;}
  public double l2pen() {return (1-_alpha)*_lambda;}


  /**
   * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
   *
   * @return indices of expected active predictors.
   */
  protected int applyStrongRules() {
    if(_parms._family == Family.multinomial)
      return applyStrongRulesMultinomial();
    int P = _dinfo.fullN();
    int newlySelected = 0;
    _activeBC = _bc;
    _activeData = _activeData != null?_activeData:_dinfo;
    if (!_allIn) {
      final double rhs = Math.abs(_alpha * (2 * _lambda - _previousLambda));
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
//        if(newlySelected != active) {
//          cols = new int[active];
//
//        }
        cols[newlySelected++] = P; // intercept is always selected, even if it is false (it's gonna be dropped later, it is needed for other stuff too)
        cols = Arrays.copyOf(cols, newlySelected);
        double [] b = ArrayUtils.select(_beta, cols);
        assert Arrays.equals(_beta,ArrayUtils.expandAndScatter(b,_dinfo.fullN()+1,cols));
        _beta = b;
        _activeData = _dinfo.filterExpandedColumns(Arrays.copyOf(cols, newlySelected));
        _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal, ArrayUtils.select(_ginfo._gradient, cols));
        _activeBC = _bc.filterExpandedColumns(_activeData.activeCols());
        _gslvr = new GLMGradientSolver(_jobKey,_parms,_activeData,(1-_alpha)*_lambda,_bc);
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

  public double [] betaMultinomial(int c) {return extractSubRange(_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols(),_beta);}

  public GLMSubsetGinfo ginfoMultinomial(int c) {
    return new GLMSubsetGinfo(_ginfo,(_activeData.fullN()+1),c,_activeDataMultinomial[c].activeCols());
  }

  public void setBC(BetaConstraint bc) {
    _bc = bc;
    _activeBC = _bc;
  }

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

  public void setBetaMultinomial(int c, double [] b, GLMSubsetGinfo ginfo) {
    fillSubRange(_activeData.fullN()+1,c,_activeDataMultinomial[c].activeCols(),b,_beta);
    double objOld = objective();
    _ginfo = ginfo._fullInfo;
    _likelihood = ginfo._likelihood;
    _relImprovement = (objOld - objective())/objOld;
  }
  /**
   * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
   *
   * @return indices of expected active predictors.
   */
  protected int applyStrongRulesMultinomial() {
    int P = _dinfo.fullN();
    int N = P+1;
    int selected = 0;
    _activeBC = _bc;
    _activeData = _dinfo;
    if (!_allIn) {
      if(_activeDataMultinomial == null)
        _activeDataMultinomial = new DataInfo[_nclasses];
      final double rhs = _alpha * (2 * _lambda - _previousLambda);
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
    if(_activeData._activeCols != null)
      beta = ArrayUtils.expandAndScatter(beta,_dinfo.fullN() + 1,_activeData._activeCols);
    int [] activeCols = _activeData.activeCols();
    _gslvr = new GLMGradientSolver(_jobKey,_parms,_dinfo,(1-_alpha)*_lambda,_bc);
    GLMGradientInfo ginfo = _gslvr.getGradient(beta);
    double[] grad = ginfo._gradient.clone();
    double err = 1e-4;
    ADMM.subgrad(_alpha * _lambda, beta, grad);
    for (int c : activeCols) // set the error tolerance to the highest error og included columns
      if (grad[c] > err) err = grad[c];
      else if (grad[c] < -err) err = -grad[c];
    _gradientErr = err;
    _beta = beta;
    _ginfo = ginfo;
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
        _ginfo = new GLMGradientInfo(ginfo._likelihood, ginfo._objVal, ArrayUtils.select(ginfo._gradient, newCols));
        _activeData = _dinfo.filterExpandedColumns(newCols);
        _activeBC = _bc.filterExpandedColumns(_activeData.activeCols());
        _gslvr = new GLMGradientSolver(_jobKey, _parms, _activeData, (1 - _alpha) * _lambda, _activeBC);
        return false;
      }
    }
    return true;
  }
  public int []  removeCols(int [] cols) {
    int [] activeCols = ArrayUtils.removeIds(_activeData.activeCols(),cols);
    if(_beta != null)
      _beta = ArrayUtils.removeIds(_beta,cols);
    if(_ginfo != null)
      _ginfo._gradient = ArrayUtils.removeIds(_ginfo._gradient,cols);
    _activeData = _dinfo.filterExpandedColumns(activeCols);
    _activeBC = _bc.filterExpandedColumns(activeCols);
    _gslvr = new GLMGradientSolver(_jobKey, _parms, _activeData, (1 - _alpha) * _lambda, _activeBC);
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

  public boolean converged(){return _betaDiff < _parms._beta_epsilon || _relImprovement < _parms._objective_epsilon;}

  protected double updateState(double [] beta,GLMGradientInfo ginfo){
    _betaDiff = ArrayUtils.linfnorm(_beta == null?beta:ArrayUtils.subtract(_beta,beta),false);
    double objOld = objective();
    _beta = beta;
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

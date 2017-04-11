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
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.util.Arrays;
import java.util.Comparator;

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
  protected double _obj_reg;


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
    adjustToNewLambda(0, _lambda);
    // strong rules are to be applied on the gradient with no l2 penalty
    // NOTE: we start with lambdaOld being 0, not lambda_max
    // non-recursive strong rules should use lambdaMax instead of _lambda
    // However, it seems tobe working nicely to use 0 instead and be more aggressive on the predictor pruning
    // (should be safe as we check the KKTs anyways)
    applyStrongRules(lambda, _lambda);
    adjustToNewLambda(lambda, 0);
    _lambda = lambda;
    _gslvr = new GLMGradientSolver(_job,_obj_reg,_parms,_activeData,l2pen(),_activeBC);
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

  /*
   * Two max reasonable sizes for gram. Computing gram matrix is often the most efficient way of cfitting GLM, especially in distributed settings, since gram is usually much smalelr than the original dataset and is LOCAL.
   * However, that only applies if the gram is belowe certain limit. There are two limits for dense and sparse because there are two different bottlenecks, memory and computational cost.
   *
   * 1. The overall size of gram has to be limited because GLM will make several copies while building it, size grows as O(P^2) and quickly outgrows available RAM.
   * 2. The dense size (the expected number of nonzeros per observation) is limited since the cost of building the matrix grows as (M*P^2) and quickly becomes unfeasible (meaning other solvers will be much faster).
   *
   */
  public int MAX_GRAM_N = 5000; // should be update in GLM init according to the amount of free ram
  public int MAX_GRAM_DENSE = 500;

  /**
   * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
   *
   * @return indices of expected active predictors.
   */
  protected void applyStrongRules(double lambdaNew, double lambdaOld) {
    lambdaNew = Math.min(_lambdaMax,lambdaNew);
    lambdaOld = Math.min(_lambdaMax,lambdaOld);
    if (_parms._family == Family.multinomial /* && _parms._solver != GLMParameters.Solver.L_BFGS */) {
      applyStrongRulesMultinomial(lambdaNew, lambdaOld);
      return;
    }
    int P = _dinfo.fullN();
    _activeBC = _bc;
    _activeData = _activeData != null?_activeData:_dinfo;
    _allIn = _allIn || _parms._alpha[0]*lambdaNew == 0 || _activeBC.hasBounds();
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
          newCols[newlySelected++] = i;
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
        assert cols[active-1] == P:"cols = " + Arrays.toString(cols); // intercept is always selected, even if it is false (it's gonna be dropped later, it is needed for other stuff too)
        _beta = ArrayUtils.select(_beta, cols);
        if(_u != null) _u = ArrayUtils.select(_u,cols);
        _activeData = _dinfo.filterExpandedColumns(cols);
        assert _activeData.activeCols().length == _beta.length;
        assert _u == null || _activeData.activeCols().length == _u.length;
        _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal, ArrayUtils.select(_ginfo._gradient, cols));
        _activeBC = _bc.filterExpandedColumns(_activeData.activeCols());
        _gslvr = new GLMGradientSolver(_job,_obj_reg,_parms,_activeData,(1-_alpha)*_lambda,_bc);
        assert _beta.length == cols.length;
        return;
      }
    }
    _activeData = _dinfo;
  }






  private GLMModel.GLMWeightsFun _glmw;

  GramXY _fullGram;
  GramXY _currGram;

  public static final class GramXY {
    public final Gram gram;
    final double [] beta;
    final int [] activeCols;
    public final double[]  xy;
    public double yy;
    public GramXY(Gram gram, double [] xy, double [] beta, int [] activeCols, double yy){
      this.gram = gram;
      this.xy = xy;
      this.beta = beta == null?null:beta.clone();
      this.activeCols = activeCols;
      this.yy = yy;
    }

    public boolean match(double[] beta, int[] activeCols) {
      return Arrays.equals(this.beta,beta) && Arrays.equals(this.activeCols,activeCols);
    }

    public GramXY deep_clone() {
      return new GramXY(gram.deep_clone(),xy.clone(),beta==null?null:beta.clone(),activeCols == null?null:activeCols.clone(),yy);
    }
  }



  // get cached gram or incrementally update or compute new one
  public GramXY computeGram(double [] beta){
    DataInfo activeData = activeData();
    int [] activeCols = activeData.activeCols();
    if(_currGram != null && _currGram.match(beta,activeCols))
        return _currGram.deep_clone();
    if(_fullGram != null){
      assert _parms._family == Family.gaussian;
      return (_currGram = new GramXY(_fullGram.gram.selectCols(activeCols,true),ArrayUtils.select(_fullGram.xy,activeCols),beta == null?null:beta.clone(),activeCols,_fullGram.yy)).deep_clone();
    }
    if(_glmw == null) _glmw = new GLMModel.GLMWeightsFun(_parms);
    // check if we need full or just incremental matrix update
    if(_currGram != null && !Arrays.equals(_currGram.activeCols,activeCols)){
      int [] newCols = ArrayUtils.sorted_set_diff(activeCols,_currGram.activeCols);
      int [] newColsIds = newCols.clone();
      // todo: should double check beta matches except for newCols which must be 0s
      int jj = 0;
      for(int i = 0; jj < newCols.length && i < activeCols.length; ++i){
        if(activeCols[i] == newCols[jj]){
          newColsIds[jj++] = i;
        }
      }
      long t0 = System.currentTimeMillis();
      GLMTask.GLMIncrementalGramTask gt = new GLMTask.GLMIncrementalGramTask(newColsIds,activeData,_glmw,beta).doAll(activeData._adaptedFrame); // dense
      Log.info("incremental gram task of size " + gt._gram.length + " x " + gt._gram[0].length + " done in " + (System.currentTimeMillis() - t0) + "ms");
//      GLMTask.GLMIncrementalGramTask gt1 = new GLMTask.GLMIncrementalGramTask(newColsIds,activeData,_glmw,beta,true).doAll(activeData._adaptedFrame); // dense

//      System.out.println("incremental update dense ");
//      System.out.println(ArrayUtils.pprint(gt0._gram));
//      System.out.println("incremental update sparse ");
//      System.out.println(ArrayUtils.pprint(gt1._gram));
//      GLMTask.GLMIncrementalGramTask gt = gt0;

      // need to glue the incremental update and old gram together
      Gram updatedGram = new Gram(activeData.fullN(), activeData.largestCat(), activeData.numNums(), activeData._cats,true);
      double [] updatedXY = new double[activeData.fullN()+1];
      int k = 0; // new col counter
      // start with the diagonal
      for(int i = 0; i < updatedGram._diagN; ++i){
        if(k < newCols.length && activeCols[i] == newCols[k]){
          updatedGram._diag[i] = gt._gram[k][i];
          k++;
        } else {
          updatedGram._diag[i] = _currGram.gram._diag[i-k];
        }
      }
      for(int i = updatedGram._diagN; i < activeData.fullN()+1; i++){
        if(k < newCols.length && activeCols[i] == newCols[k]) {
          for (int j = 0; j <= i; j++)
            updatedGram._xx[i-updatedGram._diagN][j] = gt._gram[k][j];
          k++;
        } else {
          int l = 0;
          for(int j = 0; j <= i; j++){
            if(l < newCols.length && activeCols[j] == newCols[l]){
              updatedGram._xx[i-updatedGram._diagN][j] = gt._gram[l][i];
              l++;
            } else {
              updatedGram._xx[i-updatedGram._diagN][j] = _currGram.gram._xx[i-k-_currGram.gram._diagN][j-l];
            }
          }
        }
      }
      // now xy vec
      k = 0;
      for(int i = 0; i < updatedXY.length; i++){
        if(k < newCols.length && activeCols[i] == newCols[k]){
          updatedXY[i] = gt._xy[k];
          k++;
        } else {
          updatedXY[i] = _currGram.xy[i-k];
        }
      }
//      System.out.println("oldGram" );
//      System.out.println(ArrayUtils.pprint(_currGram.gram.getXX()));
      _currGram = new GramXY(updatedGram,updatedXY,beta == null?null:beta,activeCols,_currGram.yy);
//      GLMTask.GLMIterationTask gt2 = new GLMTask.GLMIterationTask(_job._key, activeData, _glmw, beta).doAll(activeData()._adaptedFrame);
//
//      System.out.println("newcols = " + Arrays.toString(newCols));
//      System.out.println("activeCols = " + Arrays.toString(activeCols));
//      System.out.println("incremental gram");
//      System.out.println(ArrayUtils.pprint(updatedGram.getXX()));
//      System.out.println("fully computed gram");
//      System.out.println(ArrayUtils.pprint(gt2._gram.getXX()));
      return _currGram.deep_clone();
    }
    GLMTask.GLMIterationTask gt = new GLMTask.GLMIterationTask(_job._key, activeData, _glmw, beta).doAll(activeData()._adaptedFrame);
    _currGram = new GramXY(gt._gram,gt._xy,beta == null?null:beta,activeCols,gt._yy);
    return _currGram.deep_clone();
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

  public double deviance() {
    switch (_parms._family) {
      case gaussian:
      case binomial:
      case quasibinomial:
      case multinomial:
        return 2*likelihood();
      case poisson:
      case gamma:
      case tweedie:
        return likelihood();
      default:
        throw new RuntimeException("unknown family " + _parms._family);
    }
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
          } else if (_ginfo._gradient[c*N+i] > rhs || _ginfo._gradient[c*N+i] < -rhs) {
            cols[selected++] = c*N + i;
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
      _gslvr = new GLMGradientSolver(_job, _obj_reg, _parms, _dinfo, (1 - _alpha) * _lambda, _bc);
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
        _gslvr = new GLMGradientSolver(_job, _obj_reg, _parms, _activeData, (1 - _alpha) * _lambda, _activeBC);
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
    _gslvr = new GLMGradientSolver(_job, _obj_reg, _parms, _activeData, (1 - _alpha) * _lambda, _activeBC);
    return activeCols;
  }

  private double penalty(double [] beta) {
    if(_lambda == 0) return 0;
    double l1norm = 0, l2norm = 0;
    if(_parms._family == Family.multinomial) {
      int len = beta.length/_nclasses;
      assert len*_nclasses == beta.length;
      for(int c = 0; c < _nclasses; ++c) {
        for(int i = c*len; i < (c+1)*len-1; ++i) {
          double d = beta[i];
          l1norm += d >= 0?d:-d;
          l2norm += d*d;
        }
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
    return likelihood * _obj_reg + penalty(beta) + (_activeBC == null?0:_activeBC.proxPen(beta));
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
    return ArrayUtils.expandAndScatter(beta, (_dinfo.fullN() + 1) * _nclasses,_activeData._activeCols);
  }

}

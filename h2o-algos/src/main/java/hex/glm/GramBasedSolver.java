package hex.glm;

import hex.DataInfo;
import hex.gram.Gram;
import hex.optimization.ADMM;
import hex.optimization.OptimizationUtils;
import water.*;
import water.fvec.Chunk;
import water.fvec.ChunkUtils;
import water.util.*;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by tomas on 7/13/17.
 */
class GramBasedSolver extends GLMSolver {
  final GLM.Solver _slvr;
  private long COD_time;
  boolean _lsNeeded;
//  private double _betaEpsilon;
//  private double _objectiveEpsilon;

  public GramBasedSolver(GLM.Solver slvr, GLMModel.GLMParameters parms){
    _slvr = slvr;

  }

  @Override protected int defaultMaxIterations(){return 50;}

  @Override
  protected long estimateMemoryForDriver(DataInfo dinfo){
    long res = ((dinfo.fullN()+1)*(dinfo.fullN()+1) + dinfo.fullN()+1)*8;
    // there can be some copies -> multiply by 4 just in case
    return 4*res;
  }
  @Override
  protected long estimateMemoryPerWorker(DataInfo activeData) {
    int p = activeData.fullN();
    // gram consists of:
    //    diagonal for largest cat (size==largest_cat)
    //    lower diagonal of square matrix for pred/largest_cat X pred/largest_cat
    //    rectangle for pred/largest_cat X largest_cat
    int gram_lower_diag_size = (p-activeData.largestCat())*(p-activeData.largestCat());
    gram_lower_diag_size -= (gram_lower_diag_size - p + activeData.largestCat())/2;
    int gram_size =  gram_lower_diag_size + activeData.largestCat()*p + activeData.largestCat();
    int xy_size = p;
    return (gram_size + xy_size)*8; // * 8 because doubles
  }

  private void fitLSM(GLM.GLMState state){
    long t0 = System.currentTimeMillis();
    GramXY gramXY = computeGram(null,state);
    Log.info(state.LogMsg("Gram computed in " + (System.currentTimeMillis()-t0) + "ms"));
    double [] beta = solve(gramXY,state);
    // compute mse
    double [] x = ArrayUtils.mmul(gramXY.gram.getXX(),beta);
    for(int i = 0; i < x.length; ++i)
      x[i] = (x[i] - 2*gramXY.xy[i]);
    double l = .5*(ArrayUtils.innerProduct(x,beta)/state.objReg() + gramXY.yy );
    state.update(beta, l,1);
  }

  private int _iter;
  final private int _maxIter;

  private void fitIRLSM(GLM.GLMState state){
    double betaEpsilon = state.betaEpsilon(1e-4);
    double objectiveEpsilon = state.objectiveEpsilon(1e-4);
    double [] betaCnd = state.beta();
    boolean firstIter = true;

    String l1Solver = _slvr == GLM.Solver.COORDINATE_DESCENT?"COD":"ADMM";
    int i = _iter;
    while(true) {
      long t1 = System.currentTimeMillis();
      GramXY gram = computeGram(betaCnd, state);
      long t2 = System.currentTimeMillis();
      if (!_lsNeeded && (Double.isNaN(gram.likelihood) || state.objective(gram.beta,gram.likelihood) > state.objective() + state.objectiveEpsilon())) {
        _lsNeeded = true;
      } else {
        if (!firstIter && !_lsNeeded && !state.update(gram.beta, gram.likelihood,1)) {
          return;
        }
        betaCnd = solve(gram, state);
        System.out.println("betaCnd = " + Arrays.toString(betaCnd));
      }
      firstIter = false;
      long t3 = System.currentTimeMillis();
      if (_lsNeeded) {
        OptimizationUtils.LineSearchSolver ls = state.lsSolver();
        if (!ls.evaluate(ArrayUtils.subtract(betaCnd, ls.getX(), betaCnd))) {
          Log.info(state.LogMsg("Ls failed " + ls));
          return;
        }
        betaCnd = ls.getX();
        if (!state.update(betaCnd, (GLM.GLMGradientInfo)ls.ginfo(),1))
          return;
        long t4 = System.currentTimeMillis();
        Log.info(state.LogMsg("computed in " + (t2 - t1) + "+" + (t3 - t2) + "+" + (t4 - t3) + "=" + (t4 - t1) + "ms, step = " + ls.step() + ", l1solver " + l1Solver));
      } else
        Log.info(state.LogMsg("computed in " + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1) + "ms, step = " + 1 + ", l1solver " + l1Solver));
    }
    if(++i == _maxIter){
      state.addWarning("reached maximum number of iterations (" + i + ") before converging");

    }

  }

  static class DataChunk extends Iced {
    public DataChunk(Chunk[] cs){
      _chks = cs;
      int len = cs[0].len();
      etaSum = MemoryManager.malloc8d(len);
      maxEta = MemoryManager.malloc8d(len);
//      mu = MemoryManager.malloc8d(len);
//      w = MemoryManager.malloc8d(len);
//      wz = MemoryManager.malloc8d(len);
    }
    Chunk[] _chks ; // original dataset
    double [] etaSum;
    double [] maxEta;
//    double [] mu;
//    double [] w;
//    double [] wz;
  }

  VecUtils.RawVec<DataChunk> _multinomialData;

  @Override
  protected Futures cleanup(Futures fs){
    if(_multinomialData != null)
      _multinomialData.remove(fs);
    return fs;
  }
  private void fitIRLSM_multinomial(GLM.GLMState state){
    double [] beta_full = state.beta();
    int nclass = state.nclasses();
    int P = beta_full.length/nclass;
    assert P*nclass == beta_full.length;
    double [][] betaMultinomial = ArrayUtils.convertTo2DMatrix(beta_full,P);
    double [] intercepts = new double[betaMultinomial.length];
    for(int i = 0; i < betaMultinomial.length; ++i)
      intercepts[i] = betaMultinomial[i][P-1];
    System.out.println("intercepts = " + Arrays.toString(intercepts));
    if(_multinomialData == null){
      for(double [] b:betaMultinomial){
        for(int i = 0; i < b.length-1; ++i)
          if(b[i] != 0) throw H2O.unimpl("expected null beta");
      }
      final DataInfo allData = state.activeData();
      final UpdateMultinomial f = new UpdateMultinomial(allData,betaMultinomial,new double[betaMultinomial[0].length],ArrayUtils.maxIndex(intercepts));
      _multinomialData = VecUtils.RawVec.makeChunks(allData._adaptedFrame.vecs(), false, new VecUtils.RawVec.ChunkedMaker<DataChunk>() {
        @Override
        public DataChunk make(Chunk[] cs) {
          DataChunk data = new DataChunk(cs);
          Arrays.fill(data.maxEta,Double.NEGATIVE_INFINITY);
          f.map(data,new IcedDouble(0));
          return data;
        }
      });
    }
    OptimizationUtils.LineSearchSolver ls = state.lsSolver();
    final double objReg = state.objReg();
    int [] clazzes = ArrayUtils.seq(0,nclass);//ArrayUtils.append(new int[]{nclass-1},ArrayUtils.seq(0,nclass));
    double likelihood = state.likelihood();
    long t0 = System.currentTimeMillis();
    double [] beta = null;
    DataInfo [] activeDataMultinomial = state.activeDataMultinomial();
    do {
      for (int c:clazzes) {
        DataInfo activeData = activeDataMultinomial[c];
        double[] beta_c = activeData.isFiltered() ? ArrayUtils.select(betaMultinomial[c], activeData.activeCols()) : betaMultinomial[c];
        long t1 = System.currentTimeMillis();
        int iter = 0;
        double [] beta_old = null;
        while(iter++<1) {
          GramXY gram = _multinomialData.eval(new ComputeGramMultinomial(activeData, beta_c, c));
          gram.gram.mul(objReg);
          beta_old = betaMultinomial[c].clone();
          ArrayUtils.mult(gram.xy, objReg);
          beta_c = solve(gram, state, beta_c);
          betaMultinomial[c] = activeData.isFiltered() ? ArrayUtils.expandAndScatter(beta_c, P, activeData.activeCols()) : beta_c;
          double obj = _multinomialData.eval(new UpdateMultinomial(state.activeData(), betaMultinomial, beta_old, c))._val;
          if(true || obj < 0){
            double [] beta_new = ArrayUtils.flat(betaMultinomial);
            double objective_new = state.objective(beta_new,likelihood - obj);
            final double [] b =  beta_old;
            beta_old = betaMultinomial[c];
            betaMultinomial[c] = b;
            double objective_old = state.objective(ArrayUtils.flat(betaMultinomial),likelihood);
            if(objective_old < objective_new) { // need line search
              _multinomialData.eval(new UpdateMultinomial(state.activeData(), betaMultinomial, beta_old, c));
              double [] beta_old_2 = ArrayUtils.flat(betaMultinomial);
              if(!ls.evaluate(beta_old_2,beta_new)) {
                System.out.println(ls);
                break;
              }
              beta_new = ls.getX();
              betaMultinomial = ArrayUtils.convertTo2DMatrix(beta_new,P);
              beta_old = b;
              obj = _multinomialData.eval(new UpdateMultinomial(state.activeData(), betaMultinomial, beta_old, c))._val;
            } else {
              betaMultinomial[c] = beta_old;
            }
          }
          likelihood -= obj;
        }
      }
      long t1 = System.currentTimeMillis();
      Log.info(state.LogMsg("iteration done in " + (t1-t0)+"ms"));
      t0 = t1;
    } while(state.update(ArrayUtils.flat(betaMultinomial),likelihood,1));
  }



  public void computePValues(GLM.GLMState state){
    // compute p-values
      double se = 1;
      boolean seEst = false;
      double [] beta = state.beta();
      if(state.family() != GLM.Family.binomial && state.family() != GLM.Family.poisson) {
        seEst = true;
        GLMTask.ComputeSETsk ct = new GLMTask.ComputeSETsk(null, state.activeData(), state.jobKey(), beta, state.glmWeightsFun()).doAll(state.activeData()._adaptedFrame);
        se = ct._sumsqe / (state.nobs() - 1 - state.activeData().fullN());
      }
      double [] zvalues = MemoryManager.malloc8d(state.activeData().fullN()+1);
      DataInfo activeData = state.activeData();
      double [] beta_nostd = activeData.denormalizeBeta(beta);
      DataInfo.TransformType transform = activeData._predictor_transform;
      activeData.setPredictorTransform(DataInfo.TransformType.NONE);
      Gram g = computeNewGram(beta_nostd,activeData,state.glmWeightsFun(),state.objReg(),state.jobKey()).gram;
      activeData.setPredictorTransform(transform); // just in case, restore the trasnform
      Gram.Cholesky chol = g.cholesky(null);
      beta = beta_nostd;
      double [][] inv = chol.getInv();
      ArrayUtils.mult(inv,state.objReg()*se);
      for(int i = 0; i < zvalues.length; ++i)
        zvalues[i] = beta[i]/Math.sqrt(inv[i][i]);
      state.setZValues(zvalues,se, seEst,inv);
  }
  /**
   * Main GLM loop fitting one (lambda) model using gram-based method.
   * @param state
   */
  @Override protected GLM.GLMState fit(GLM.GLMState state) {
    try{
      if(state.family() == GLM.Family.gaussian && state.link() == GLM.Link.identity) {
        fitLSM(state);
      } else if(state.family() == GLM.Family.multinomial) {
        fitIRLSM_multinomial(state);
      } else fitIRLSM(state);
      // compute p-values if requested

    } catch(Gram.NonSPDMatrixException e) {
      Log.warn(state.LogMsg("Got Non SPD matrix, stopped."));
    }
    return state;
  }

  protected double [] solve(GramXY gram, GLM.GLMState state){
    return solve(gram,state,state.beta());
  }
  protected double [] solve(GramXY gram, GLM.GLMState state, double [] beta){
    if(_slvr == GLM.Solver.COORDINATE_DESCENT)
      return solveCOD(gram,state,beta);
    else if(_slvr == GLM.Solver.IRLSM)
      return solveADMM(gram,state);
    else throw H2O.unimpl("unexpected/unknonw solver " + _slvr);
  }

  private static double [] doUpdateCD(double [] grads, double [] ary, double diff , int variable_min, int variable_max) {
    for (int i = 0; i < variable_min; i++)
      grads[i] += diff * ary[i];
    for (int i = variable_max; i < grads.length; i++)
      grads[i] += diff * ary[i];
    return grads;
  }

  protected double [] solveCOD(GramXY gram, GLM.GLMState state, double [] beta) {
    double [][] xx = gram.gram.getXX();
    double [] xy = gram.xy;
    double [] grads = gram.getCODGradients();
    int [] newCols = gram.newCols;
    double lambda = state.lambda();
    double alpha = state.alpha();
    boolean intercept = state.hasIntercept();
    double wsumInv = 1.0/(xx[xx.length-1][xx.length-1]);
    final double betaEpsilon = state.betaEpsilon()*state.betaEpsilon();
    double l1pen = lambda * alpha;
    double l2pen = lambda*(1-alpha);
    long t0 = System.currentTimeMillis();
    double [] diagInv = MemoryManager.malloc8d(xx.length);
    for(int i = 0; i < diagInv.length; ++i)
      diagInv[i] = 1.0/(xx[i][i] + l2pen);
    DataInfo activeData = state.activeData();
    int [][] nzs = new int[activeData.numStart()][];
    int sparseCnt = 0;
    if(nzs.length > 1000) {
      final int [] nzs_ary = new int[xx.length];
      for (int i = 0; i < activeData._cats; ++i) {
        int var_min = activeData._catOffsets[i];
        int var_max = activeData._catOffsets[i + 1];
        for(int l = var_min; l < var_max; ++l) {
          int k = 0;
          double [] x = xx[l];
          for (int j = 0; j < var_min; ++j)
            if (x[j] != 0) nzs_ary[k++] = j;
          for (int j = var_max; j < activeData.numStart(); ++j)
            if (x[j] != 0) nzs_ary[k++] = j;
          if (k < ((nzs_ary.length - var_max + var_min) >> 3)) {
            sparseCnt++;
            nzs[l] = Arrays.copyOf(nzs_ary, k);
          }
        }
      }
    }
    Log.info("COD::nzs done in " + (System.currentTimeMillis()-t0) + "ms, found " + sparseCnt + " sparse columns");
    final BetaConstraint bc = state.activeBC();
    int numStart = activeData.numStart();
    if(newCols != null) {
      for (int id : newCols) {
        double b = bc.applyBounds(ADMM.shrinkage(grads[id], l1pen) * diagInv[id], id);
        if (b != 0) {
          doUpdateCD(grads, xx[id], -b, id, id + 1);
          beta[id] = b;
        }
      }
    }
    int iter1 = 0;
    int P = xy.length - 1;
    long t2 = System.currentTimeMillis();
    // CD loop
    while (iter1++ < Math.max(P,500)) {
      double maxDiff = 0;
      for (int i = 0; i < activeData._cats; ++i) {
        for(int j = activeData._catOffsets[i]; j < activeData._catOffsets[i+1]; ++j) { // can do in parallel
          double b = bc.applyBounds(ADMM.shrinkage(grads[j], l1pen) * diagInv[j],j);
          double bd = beta[j] - b;
          if(bd != 0) {
            double diff = bd*bd*xx[j][j];
            if(diff > maxDiff) maxDiff = diff;
            if (nzs[j] == null)
              doUpdateCD(grads, xx[j], bd, activeData._catOffsets[i], activeData._catOffsets[i + 1]);
            else {
              double[] x = xx[j];
              int[] ids = nzs[j];
              for (int id : ids) grads[id] += bd * x[id];
              doUpdateCD(grads, x, bd, 0, activeData.numStart());
            }
            beta[j] = b;
          }
        }
      }
      for (int i = numStart; i < P; ++i) {
        double b = bc.applyBounds(ADMM.shrinkage(grads[i], l1pen) * diagInv[i],i);
        double bd = beta[i] - b;
        double diff = bd * bd * xx[i][i];
        if (diff > maxDiff) maxDiff = diff;
        if(diff > .01*betaEpsilon) {
          doUpdateCD(grads, xx[i], bd, i, i + 1);
          beta[i] = b;
        }
      }
      if(intercept) {
        double b = bc.applyBounds(grads[P] * wsumInv,P);
        double bd = beta[P] - b;
        double diff = bd * bd * xx[P][P];
        if (diff > maxDiff) maxDiff = diff;
        doUpdateCD(grads, xx[P], bd, P, P + 1);
        beta[P] = b;
      }
      if (maxDiff < betaEpsilon)
        break;
    }
    long tend = System.currentTimeMillis();
    long tdelta = (tend-t0);
    Log.info(state.LogMsg("COD done after " + iter1 + " iterations and " + tdelta + "ms") + ", main loop took " + (tend-t2) + "ms, overall COD time = " + (COD_time += tdelta));
    gram.newCols = new int[0];
    return beta;
  }

  ADMM.L1Solver _lslvr;

  protected double [] solveADMM(GramXY gramXY, GLM.GLMState state){
    Gram gram = gramXY.gram;
    double [] xy = gramXY.xy;
    if(state.removeCollinearColumns() || state.computePValues()) {
      if(!state.hasIntercept()) throw H2O.unimpl();
      ArrayList<Integer> ignoredCols = new ArrayList<>();
      Gram.Cholesky chol = ((state.iter() == 0)?gram.qrCholesky(ignoredCols, state.activeData()._predictor_transform == DataInfo.TransformType.STANDARDIZE):gram.cholesky(null));
      if(!ignoredCols.isEmpty() && !state.removeCollinearColumns()) {
        int [] collinear_cols = new int[ignoredCols.size()];
        for(int i = 0; i < collinear_cols.length; ++i)
          collinear_cols[i] = ignoredCols.get(i);
        throw new Gram.CollinearColumnsException("Found collinear columns in the dataset. P-values can not be computed with collinear columns in the dataset. Set remove_collinear_columns flag to true to remove collinear columns automatically. Found collinear columns " + Arrays.toString(ArrayUtils.select(state.activeData().coefNames(),collinear_cols)));
      }
      if(!chol.isSPD()) throw new Gram.NonSPDMatrixException();
      if(!ignoredCols.isEmpty()) { // got some redundant cols
        int [] collinear_cols = new int[ignoredCols.size()];
        for(int i = 0; i < collinear_cols.length; ++i)
          collinear_cols[i] = ignoredCols.get(i);
        String [] collinear_col_names = ArrayUtils.select(state.activeData().coefNames(),collinear_cols);
        // need to drop the cols from everywhere
        state.addWarning("Removed collinear columns " + Arrays.toString(collinear_col_names));
        Log.warn("Removed collinear columns " + Arrays.toString(collinear_col_names));
        state.removeCols(collinear_cols);
        gram.dropCols(collinear_cols);
        xy = ArrayUtils.removeIds(xy,collinear_cols);
      }
      xy = xy.clone();
      chol.solve(xy);
    } else {
      gram = gram.deep_clone();
      xy = xy.clone();
      GramSolver slvr = new GramSolver(gram.clone(), xy.clone(), state.hasIntercept(), state.l2pen(), state.l1pen(), state.activeBC()._betaGiven, state.activeBC()._rho, state.activeBC()._betaLB, state.activeBC()._betaUB);
      if(state.l1pen() == 0 && !state.activeBC().hasBounds()) {
        slvr.solve(xy);
      } else {
        xy = MemoryManager.malloc8d(xy.length);
        (_lslvr = new ADMM.L1Solver(1e-4, 10000, null)).solve(slvr, xy, state.l1pen(), state.hasIntercept(), state.activeBC()._betaLB, state.activeBC()._betaUB);
      }
    }
    return xy;
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
    public double likelihood;


    public GramXY(Gram gram, double[] xy) {
      this(gram,xy,null,null,null,null,-1,-1);
    }
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

    public void addRow(DataInfo.Row r, double w, double wz){
      for(int i = 0; i < r.nBins; ++i)
        xy[r.binIds[i]] += wz;
      for(int i = 0; i < r.nNums; ++i)
        xy[r.numId(i)] += wz*r.numVal(i);
      // intercept
      xy[xy.length-1] += wz;
      gram.addRow(r,w);
    }

    public GramXY reduce(GramXY y) {
      gram.add(y.gram);
      ArrayUtils.add(xy,y.xy);
      likelihood += y.likelihood;
      yy += y.yy;
      return this;
    }

    public void adjustForSparseZeros(DataInfo dinfo) {
      int ns = dinfo.numStart();
      int interceptIdx = xy.length - 1;
      double[] interceptRow = gram._xx[interceptIdx - gram._diagN];
      double nobs = interceptRow[interceptRow.length - 1]; // weighted _nobs
      for (int i = ns; i < dinfo.fullN(); ++i) {
        double iMean = dinfo._normSub[i - ns] * dinfo._normMul[i - ns];
        for (int j = 0; j < ns; ++j)
          gram._xx[i - gram._diagN][j] -= interceptRow[j] * iMean;
        for (int j = ns; j <= i; ++j) {
          double jMean = dinfo._normSub[j - ns] * dinfo._normMul[j - ns];
          gram._xx[i - gram._diagN][j] -= interceptRow[i] * jMean + interceptRow[j] * iMean - nobs * iMean * jMean;
        }
      }
      if (dinfo._intercept) { // do the intercept row
        for (int j = ns; j < dinfo.fullN(); ++j)
          interceptRow[j] -= nobs * dinfo._normSub[j - ns] * dinfo._normMul[j - ns];
      }
      // and the xy vec as well
      for (int i = ns; i < dinfo.fullN(); ++i) {
        xy[i] -= xy[xy.length - 1] * dinfo._normSub[i - ns] * dinfo._normMul[i - ns];
      }
    }
  }


  protected GramXY computeNewGram(double [] beta, DataInfo data, GLMModel.GLMWeightsFun glmf, double obj_reg, Key<Job> jobKey){
    GLMTask.GLMIterationTask gt = new GLMTask.GLMIterationTask(jobKey, data, glmf, beta,-1).doAll(data._adaptedFrame);
    ArrayUtils.mult(gt._xy,obj_reg);
    int [] activeCols = data.activeCols();
    return new GramXY(gt._gram,gt._xy,null,beta == null?null:beta,activeCols,null,gt._yy,gt._likelihood);
  }
  protected GramXY computeNewGram(GLM.GLMState state){
    return computeNewGram(state.beta(),state.activeData(),state.glmWeightsFun(),state.objReg(),state.jobKey());
  }

  GramXY _currGram;
  GLMModel.GLMWeightsFun _glmw;

  // get cached gram or incrementally update or compute new one
  public GramXY computeGram(double [] beta, GLM.GLMState state){
    double obj_reg = state.objReg();
    boolean weighted = state.family() != GLM.Family.gaussian || state.link() != GLM.Link.identity;
    if(state.family() == GLM.Family.multinomial) // no caching
      return computeNewGram(state);
    if(_slvr != GLM.Solver.COORDINATE_DESCENT)
      // only cache for solver==COD
      //    caching only makes difference when running with lambda search
      //    and COD and IRLSM need matrix in different shape
      //    and COD is better for lambda search
      return computeNewGram(state);
    DataInfo activeData = state.activeData();
    if(_currGram == null) // no cached value, compute new one and store
      return _currGram = computeNewGram(state);

    int [] activeCols = activeData.activeCols();
    if (Arrays.equals(_currGram.activeCols,activeCols)) {
      if(weighted && !Arrays.equals(_currGram.beta, beta)) {
        _currGram = computeNewGram(state);
        _currGram.gram.getXX();
      }
      return _currGram;
    }
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
    _currGram = computeNewGram(state);
    _currGram.gram.getXX();
    return _currGram;
  }

/*
  private static final class IcedVoid extends Iced{
    private IcedVoid(){}
    // empty class used only as a filler in type parameter
    public static final IcedVoid instance = new IcedVoid();
  };


  private static class ComputeMultinomialLikelihood extends VecUtils.RawVec.RawVecFun<DataChunk,IcedDouble> {
    final DataInfo _dinfo;
    final double [][] _beta;

    public ComputeMultinomialLikelihood(DataInfo dinfo, double [][] beta){
      _dinfo = dinfo;
      _beta = beta;
    }
    @Override
    public IcedDouble makeNew() {return new IcedDouble(0);}
    @Override
    public IcedDouble map(DataChunk data, IcedDouble accum) {
      double sparseRatio = ChunkUtils.sparseRatio(data._chks);
      boolean sparse = sparseRatio < .125;
      DataInfo.Rows rows = _dinfo.rowsFromFullData(data._chks, sparse);
      double[] sparseOffsets = MemoryManager.malloc8d(_beta.length);
      if (sparse) {
        for (int i = 0; i < _beta.length; ++i) {
          sparseOffsets[i] = GLMImpl.sparseOffset(_beta[i], _dinfo);
        }
      }
      for (int i = 0; i < rows._nrows; ++i) {
        DataInfo.Row r = rows.row(i);
        int c = (int) r.response(0);
        double eta = r.innerProduct(_beta[c]) + sparseOffsets[c];
        accum._val += eta - Math.log(data.etaSum[i]) - data.maxEta[i];
      }
      return accum;
    }
    @Override
    public IcedDouble reduce(IcedDouble x, IcedDouble y) {
      x._val += y._val;
      return x;
    }
  }

  private static class ComputeGradientMultinomial extends VecUtils.RawVec.RawVecFun<DataChunk,GLMImpl.GLMGradientInfo> {
    final DataInfo _dinfo;
    final double [] _beta;
    final double [] _betaOld;
    int _c;

    public ComputeGradientMultinomial(DataInfo dinfo, double [] beta, double [] betaOld){
      _dinfo = dinfo;
      _beta = beta;
      _betaOld = betaOld;
    }

    @Override
    public GLMImpl.GLMGradientInfo makeNew() {
      return new GLMImpl.GLMGradientInfo(0,Double.NaN,MemoryManager.malloc8d(_beta.length));
    }
    @Override
    public GLMImpl.GLMGradientInfo map(DataChunk data, GLMImpl.GLMGradientInfo ginfo) {
      double [] grad = ginfo._gradient;
      double likelihood = ginfo._likelihood;
      double sparseRatio = ChunkUtils.sparseRatio(data._chks);
      boolean sparse = sparseRatio < .125;
      DataInfo.Rows rows = _dinfo.rowsFromFullData(data._chks, sparse);
      double sparseOffset = sparse? GLMImpl.sparseOffset(_beta, _dinfo):0;
      for (int i = 0; i < rows._nrows; ++i) {
        DataInfo.Row r = rows.row(i);
        int c = (int) r.response(0);
        double eta = r.innerProduct(_beta) + sparseOffset;
        double g = r.weight*((eta-data.maxEta[i])/data.etaSum[i] - (c==_c?1:0));
        for(int j = 0; j < r.nBins; ++j)
          grad[r.binIds[j]] += j;
        int numStart = _dinfo.numStart();
        if(sparse){
          for(int j = 0; j < r.nNums; ++j)
            grad[r.numIds[j]] += g*r.numVals[j];
        } else {
          for(int j = 0; j < r.nNums; ++j)
            grad[numStart+j] += g*r.numVals[j];
        }
        grad[grad.length-1] += g; // intercept
        if(_c == c) // _likelihood is really likelihood diff
          likelihood += eta - (r.innerProduct(_betaOld) + sparseOffset);
      }
      return new GLMImpl.GLMGradientInfo(likelihood,Double.NaN,grad);
    }

    @Override
    public GLMImpl.GLMGradientInfo reduce(GLMImpl.GLMGradientInfo x, GLMImpl.GLMGradientInfo y) {
      return new GLMImpl.GLMGradientInfo(x._likelihood+y._likelihood,Double.NaN,ArrayUtils.add(x._gradient,y._gradient));
    }
  }

*/

  private static class UpdateMultinomial extends VecUtils.RawVec.RawVecFun<DataChunk,IcedDouble> {
    final DataInfo _dinfo;
    final double [][] _beta;
    final double [] _betaOld;
    final int _c;

    public UpdateMultinomial(DataInfo dinfo, double [][] beta, double [] betaOld, int c){
      _dinfo = dinfo; _beta = beta; _betaOld = betaOld; _c = c;
    }
    @Override
    public IcedDouble makeNew() {return new IcedDouble(0);}

    @Override
    public IcedDouble map(DataChunk data, IcedDouble x) {
      double sparseRatio = ChunkUtils.sparseRatio(data._chks);
      boolean sparse = sparseRatio < .125;
      DataInfo.Rows rows = _dinfo.rowsFromFullData(data._chks,sparse);
      double [] sparseOffsets = MemoryManager.malloc8d(_beta.length);
      double [] etas = MemoryManager.malloc8d(_beta.length);
      if(sparse)
        for(int i = 0; i < _beta.length; ++i)
          sparseOffsets[i] = GLM.sparseOffset(_beta[i],_dinfo);
      final double [] beta_c_old = _betaOld;
      final double [] beta_c_new = _beta[_c];
      final double sparseOffset_c_new = sparseOffsets[_c];
      final double sparseOffset_c_old = sparse? GLM.sparseOffset(beta_c_old,_dinfo):0;
      for (int i = 0; i < rows._nrows; ++i) {
        double maxDiff = 0;
        DataInfo.Row r = rows.row(i);
        double max = data.maxEta[i];
        double y = r.response(0) == _c?1:0;
        final double eta_c_new = r.innerProduct(beta_c_new) + sparseOffset_c_new;
        final double eta_c_old = r.innerProduct(beta_c_old) + sparseOffset_c_old;
        double sumOld = data.etaSum[i];
        double sum;
        if (eta_c_new > max || eta_c_old == max) { // need to recompute everything
          for(int c = 0; c < etas.length; ++c)
            etas[c] = c == _c?eta_c_new:r.innerProduct(_beta[c]) + sparseOffsets[c];
          max = ArrayUtils.maxValue(etas);
          maxDiff = max - data.maxEta[i];
          data.maxEta[i] = max;
          sum = 0;
          for (double e:etas)
            sum += Math.exp(e - max);
          data.etaSum[i] = sum;
        } else{ // max does not change, just update etaSum
          data.etaSum[i] = sum = data.etaSum[i] + Math.exp(eta_c_new-max) - Math.exp(eta_c_old-max);
        }
        x._val += y*(eta_c_new-eta_c_old) - maxDiff + Math.log(sumOld/sum);
      }
      return x;
    }
    @Override
    public IcedDouble reduce(IcedDouble x, IcedDouble y) {return x.add(y);}
  }

  private static class ComputeGramMultinomial extends VecUtils.RawVec.RawVecFun<DataChunk,GramXY> {
    final DataInfo _dinfo;
    final double [] _beta;
    final int _c;

    public ComputeGramMultinomial(DataInfo dinfo, double [] beta, int c){
      _dinfo = dinfo; _beta = beta; _c = c;
    }
    @Override
    public GramXY makeNew() {
      Gram gram = new Gram(_dinfo.fullN(), _dinfo.largestCat(), _dinfo.numNums(), _dinfo._cats,true);
      double [] xy = MemoryManager.malloc8d(_dinfo.fullN()+1); // + 1 is for intercept
      return new GramXY(gram,xy);
    }
    @Override
    public GramXY map(DataChunk data, GramXY gram) {
      double sparseRatio = ChunkUtils.sparseRatio(data._chks);
      boolean sparse = sparseRatio < .125;
      double sparseOffset = sparse? GLM.sparseOffset(_beta,_dinfo):0;
      DataInfo.Rows rows = _dinfo.rowsFromFullData(data._chks,sparse);
      for(int i = 0; i < rows._nrows; ++i){
        DataInfo.Row r = rows.row(i);
        double y = r.response(0) == _c?1:0;
        double eta = r.innerProduct(_beta) + sparseOffset;
        assert eta <= data.maxEta[i]:"eta = " + eta + " max = " + data.maxEta[i];
        double mu = Math.exp(eta-data.maxEta[i])/data.etaSum[i];
        double d = mu*(1-mu);
        if(d == 0) d = 1e-10;
        gram.addRow(r,r.weight*d,r.weight*(eta * d + (y-mu)));
      }
      if(sparse && _dinfo._normSub != null)
        gram.adjustForSparseZeros(_dinfo);
      return gram;
    }
    @Override
    public GramXY reduce(GramXY x, GramXY y) {
      return x.reduce(y);
    }

    @Override public GramXY getResult(GramXY g){
      return new GramXY(g.gram,g.xy,null,_beta,null,null,Double.NaN,Double.NaN);
    }
  }

  /**
   * Created by tomasnykodym on 3/30/15.
   */
  public static final class GramSolver implements ADMM.ProximalSolver {
    private final Gram _gram;
    Gram.Cholesky _chol;

    private final double[] _xy;
    final double _lambda;
    double[] _rho;
    boolean _addedL2;
    double _betaEps;

    private static double boundedX(double x, double lb, double ub) {
      if (x < lb) x = lb;
      if (x > ub) x = ub;
      return x;
    }

    public GramSolver(Gram gram, double[] xy, double lmax, double betaEps, boolean intercept) {
      _gram = gram;
      _lambda = 0;
      _betaEps = betaEps;
      _xy = xy;
      double[] rhos = MemoryManager.malloc8d(xy.length);
      computeCholesky(gram, rhos, lmax * 1e-8,intercept);
      _addedL2 = rhos[0] != 0;
      _rho = _addedL2 ? rhos : null;
    }

    // solve non-penalized problem
    public void solve(double[] result) {
      System.arraycopy(_xy, 0, result, 0, _xy.length);
      _chol.solve(result);
      double gerr = Double.POSITIVE_INFINITY;
      if (_addedL2) { // had to add l2-pen to turn the gram to be SPD
        double[] oldRes = MemoryManager.arrayCopyOf(result, result.length);
        for (int i = 0; i < 1000; ++i) {
          solve(oldRes, result);
          double[] g = gradient(result)._gradient;
          gerr = Math.max(-ArrayUtils.minValue(g), ArrayUtils.maxValue(g));
          if (gerr < 1e-4) return;
          System.arraycopy(result, 0, oldRes, 0, result.length);
        }
        Log.warn("Gram solver did not converge, gerr = " + gerr);
      }
    }

    public GramSolver(Gram gram, double[] xy, boolean intercept, double l2pen, double l1pen, double[] beta_given, double[] proxPen, double[] lb, double[] ub) {
      if (ub != null && lb != null)
        for (int i = 0; i < ub.length; ++i) {
          assert ub[i] >= lb[i] : i + ": ub < lb, ub = " + Arrays.toString(ub) + ", lb = " + Arrays.toString(lb);
        }
      _lambda = l2pen;
      _gram = gram;
      // Try to pick optimal rho constant here used in ADMM solver.
      //
      // Rho defines the strength of proximal-penalty and also the strentg of L1 penalty aplpied in each step.
      // Picking good rho constant is tricky and greatly influences the speed of convergence and precision with which we are able to solve the problem.
      //
      // Intuitively, we want the proximal l2-penalty ~ l1 penalty (l1 pen = lambda/rho, where lambda is the l1 penalty applied to the problem)
      // Here we compute the rho for each coordinate by using equation for computing coefficient for single coordinate and then making the two penalties equal.
      int ii = intercept ? 1 : 0;
      int icptCol = gram.fullN()-1;
      double[] rhos = MemoryManager.malloc8d(xy.length);
      double min = Double.POSITIVE_INFINITY;
      for (int i = 0; i < xy.length - ii; ++i) {
        double d = xy[i];
        d = d >= 0 ? d : -d;
        if (d < min && d != 0) min = d;
      }
      double ybar = xy[icptCol];
      for (int i = 0; i < rhos.length - ii; ++i) {
        double y = xy[i];
        if (y == 0) y = min;
        double xbar = gram.get(icptCol, i);
        double x = ((y - ybar * xbar) / ((gram.get(i, i) - xbar * xbar) + l2pen));///gram.get(i,i);
        rhos[i] = ADMM.L1Solver.estimateRho(x, l1pen, lb == null ? Double.NEGATIVE_INFINITY : lb[i], ub == null ? Double.POSITIVE_INFINITY : ub[i]);
      }
      // do the intercept separate as l1pen does not apply to it
      if (intercept && (lb != null && !Double.isInfinite(lb[icptCol]) || ub != null && !Double.isInfinite(ub[icptCol]))) {
        int icpt = xy.length - 1;
        rhos[icpt] = 1;//(xy[icpt] >= 0 ? xy[icpt] : -xy[icpt]);
      }
      if (l2pen > 0)
        gram.addDiag(l2pen);
      if (proxPen != null && beta_given != null) {
        gram.addDiag(proxPen);
        xy = xy.clone();
        for (int i = 0; i < xy.length; ++i)
          xy[i] += proxPen[i] * beta_given[i];
      }
      _xy = xy;
      _rho = rhos;
      computeCholesky(gram, rhos, 1e-5,intercept);
    }

    private void computeCholesky(Gram gram, double[] rhos, double rhoAdd, boolean intercept) {
      gram.addDiag(rhos);
      if(!intercept) {
        gram.dropIntercept();
        rhos = Arrays.copyOf(rhos,rhos.length-1);
        _xy[_xy.length-1] = 0;
      }
      _chol = gram.cholesky(null, true, null);
      if (!_chol.isSPD()) { // make sure rho is big enough
        gram.addDiag(ArrayUtils.mult(rhos, -1));
        gram.addDiag(rhoAdd,!intercept);
        Log.info("Got NonSPD matrix with original rho, re-computing with rho = " + (_rho[0]+rhoAdd));
        _chol = gram.cholesky(null, true, null);
        int cnt = 0;
        double rhoAddSum = rhoAdd;
        while (!_chol.isSPD() && cnt++ < 5) {
          gram.addDiag(rhoAdd,!intercept);
          rhoAddSum += rhoAdd;
          Log.warn("Still NonSPD matrix, re-computing with rho = " + (rhos[0] + rhoAddSum));
          _chol = gram.cholesky(null, true, null);
        }
        if (!_chol.isSPD()) {
          throw new Gram.NonSPDMatrixException();
        }
      }
      gram.addDiag(ArrayUtils.mult(rhos, -1));
      ArrayUtils.mult(rhos, -1);
    }

    @Override
    public double[] rho() {
      return _rho;
    }

    @Override
    public boolean solve(double[] beta_given, double[] result) {
      if (beta_given != null)
        for (int i = 0; i < _xy.length; ++i)
          result[i] = _xy[i] + _rho[i] * beta_given[i];
      else
        System.arraycopy(_xy, 0, result, 0, _xy.length);
      _chol.solve(result);
      return true;
    }

    @Override
    public boolean hasGradient() {
      return false;
    }

    @Override
    public OptimizationUtils.GradientInfo gradient(double[] beta) {
      double[] grad = _gram.mul(beta);
      for (int i = 0; i < _xy.length; ++i)
        grad[i] -= _xy[i];
      return new OptimizationUtils.GradientInfo(Double.NaN,grad); // todo compute the objective
    }

    @Override
    public int iter() {
      return 0;
    }
  }
}

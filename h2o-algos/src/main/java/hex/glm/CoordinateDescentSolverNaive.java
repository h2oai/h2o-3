package hex.glm;

import hex.DataInfo;
import hex.optimization.ADMM;
import water.*;
import water.fvec.Chunk;
import water.util.*;

import java.util.Arrays;

/**
 * Created by tomas on 6/30/17.
 */
public class CoordinateDescentSolverNaive extends GLMSolver {
  static class DataChunk extends Iced  {
    public DataChunk(Chunk [] cs, DataChunk old){
      _chks = cs;
      int len = cs[0].len();
      w = old == null? MemoryManager.malloc8d(len):old.w;
      zTilda = old == null? MemoryManager.malloc8d(len):old.zTilda;
      d0 = old == null? MemoryManager.malloc8d(len):old.d0;
      d1 = old == null? MemoryManager.malloc8d(len):old.d1;
      c0 = old == null?MemoryManager.malloc4(len):old.c0;
      c1 = old == null?MemoryManager.malloc4(len):old.c1;
    }
    Chunk[] _chks ; // original dataset
    double [] w;
    double [] zTilda;
    double [] d0;
    double [] d1;
    int [] c0;
    int [] c1;
  }

  boolean _run_local;
  long _oldSz;

  int [] _oldActiveCols;
  VecUtils.RawVec<DataChunk> _data;

  @Override
  protected Futures cleanup(Futures fs){
    if(_data != null)_data.remove(fs);
    return fs;
  }

  @Override
  protected GLM.GLMState fit(GLM.GLMState state){
    double [] beta = state.beta();
    DataInfo activeData = state.activeData();
    double lambda = state.lambda();
    double alpha = state.alpha();
    double betaEpsilon = state.betaEpsilon()*state.betaEpsilon();
    long size_increment = activeData._adaptedFrame.byteSize()-_oldSz;
    boolean run_local = false;
    if(H2O.CLOUD.size() > 1){
      HeartBeat hb = H2O.SELF._heartbeat;
      if(hb.get_free_mem() > 2*size_increment) {
        run_local = true;
        _oldSz += size_increment;
      } else Log.warn("Running COORDINATE_DESCENT_NAIVE but don't have enough memory to run local => can be extremely slow!");
    }
    if(activeData._predictor_transform != DataInfo.TransformType.STANDARDIZE)
      throw H2O.unimpl("COD for non-standardized data is not implemented!");
    final double l1pen = lambda * alpha;
    final double l2pen = l1pen * (1-alpha);
    double wsumx,wsumux; // intercept denum
    double wsumInv, wsumuInv;
    double [] denums;
    double [] betaold = beta.clone();
    int iter2=0; // total cd iters
    if(_run_local && !run_local) {
      _data.remove(new Futures());
      _data = null;
      _oldActiveCols = null;
    }
    final VecUtils.RawVec<DataChunk> olddata = _data;
    final boolean has_new_cols = _oldActiveCols == null || _oldActiveCols != activeData._activeCols;
    if(has_new_cols){
      _data = VecUtils.RawVec.makeChunks(activeData._adaptedFrame.vecs(), run_local, new VecUtils.RawVec.ChunkedMaker<DataChunk>() {
        @Override
        public DataChunk make(Chunk[] cs) {
          DataChunk old = olddata == null?null:olddata.valForChunkIdx(cs[0].cidx());
          return new DataChunk(cs,old);
        }
      });
      if(olddata != null)olddata.remove(new Futures());
    }
    long startTimeTotalNaive = System.currentTimeMillis();
    double sparseRatio = FrameUtils.sparseRatio(activeData._adaptedFrame);
    boolean sparse =  sparseRatio <= .125;
    long t0 = System.currentTimeMillis();
    GenWeightsRes gt = _data.eval(new GenWeightsFun(sparse, activeData, state.glmWeightsFun(), beta));
    int iter1Sum = 0;
    int iter_x = 0;
    double gamma = beta[beta.length-1]; // scalar offset, can be intercept and/or sparse offset compensation for skipped centering
    if(sparse)
      gamma += GLM.sparseOffset(beta,activeData);
    double [] beta_old_outer = beta.clone();
    // generate new IRLS iteration
    while (iter2++ < 50) {
      denums = gt.denums;
      wsumx = gt.wsum;
      wsumux = gt.wsumu;
      wsumInv = 1.0/wsumx;
      wsumuInv = 1.0/wsumux;
      for(int i = 0; i < denums.length; ++i)
        denums[i] = 1.0/(denums[i]*wsumuInv + l2pen);
      int iter1 = 0;
      double RES = gt.res; // sum of weighted residual:   sum_i{w_i*(y_i-ytilda_i)}
      while (iter1++ < 1000) {
        if (activeData._cats > 0) {
          double [] bNew = null, bOld = null;
          for (int i = 0; i < activeData._cats; ++i) {
            int catStart = activeData._catOffsets[i];
            int catEnd = activeData._catOffsets[i+1];
            bOld = Arrays.copyOfRange(betaold, catStart, catEnd+1);
            bOld[bOld.length-1] = 0;
            double [] res = _data.eval(new CodCatFun((iter_x = 1-iter_x),i,gamma,bOld,bNew,activeData.catMap(i),activeData.catNAFill(i)));
            for(int j=0; j < res.length; ++j)
              beta[catStart+j] = bOld[j] = ADMM.shrinkage(res[j]*wsumuInv, l1pen) * denums[catStart+j];
            bNew = bOld;
          }
          RES = _data.eval(new CodCatFun((iter_x = 1-iter_x),-1,gamma,null,bNew,null,Integer.MAX_VALUE))[0];
        }
        if(activeData.numNums() > 0){
          if(sparse){
            for (int i = 0; i < activeData.numNums(); i++) {
              int currIdx = i + activeData.numStart();
              int prevIdx = currIdx - 1;
              double delta = activeData.normSub(i)*activeData.normMul(i);
              gamma += delta*betaold[currIdx];
              beta[currIdx] = 0;
              SparseResiduals tsk = _data.eval(new CodSparseNumFun((iter_x = 1 - iter_x),i + activeData._cats, gamma, betaold[currIdx], prevIdx >= activeData.numStart() ? beta[prevIdx]-betaold[prevIdx] : 0, activeData.normMul(i), 0));
              // sparse task calculates only residual updates across the non-zeros
              double RES_NEW = RES + tsk._residual + tsk._residual_new - delta*betaold[currIdx]*wsumx;
              // adjust for skipped centering
              // sum_i {w_i*(x_i-delta)*(r_i-gamma)}
              //   := sum_i {w_i*x_i*r_i} - gamma sum_i{w_i*x_i} - delta * sum_i{w_i*r_i} + gamma*delta sum_i {w_i}
              //   := tsk._residual - gamma*sum_i{w_i*x_i} - delta*RES
              double x = tsk._res - delta*RES_NEW;
              double bnew = ADMM.shrinkage(x * wsumuInv,l1pen) * denums[currIdx];
              RES += tsk._residual + delta*wsumx*(bnew-betaold[currIdx]);
              beta[currIdx] = bnew;
              gamma -= bnew*delta;
            }
            double bdiff = beta[activeData.numStart() + activeData.numNums() - 1] - betaold[activeData.numStart() + activeData.numNums() - 1];
            if(bdiff != 0)
              RES += _data.eval(new CodSparseNumFun((iter_x = 1 - iter_x),-1, gamma, 0, beta[activeData.numStart() + activeData.numNums() - 1] - betaold[activeData.numStart() + activeData.numNums() - 1], 0, 0))._residual;
          } else {
            for (int i = 0; i < activeData.numNums(); i++) {
              int currIdx = i + activeData.numStart();
              int prevIdx = currIdx - 1;
              double tsk_res = _data.eval(new GLMCoordinateDescentTaskSeqNaiveNum((iter_x = 1 - iter_x),i+activeData._cats, gamma, betaold[currIdx], prevIdx >= activeData.numStart() ? beta[prevIdx] : 0, activeData.normMul(i), activeData.normSub(i),activeData.normSub(i)))._val;
              beta[currIdx] = ADMM.shrinkage(tsk_res * wsumuInv, l1pen) * denums[currIdx];
            }
            RES = _data.eval(new CodIcptFun((iter_x = 1 - iter_x), gamma, beta[activeData.numStart() + activeData.numNums() - 1]))._val;
          }
        }
        // compute intercept
        if (/*glmJob._parms._family != GLMModel.GLMParameters.Family.gaussian && */!Double.isNaN(RES)) { // TODO handle no intercept case
          beta[beta.length - 1] = RES*wsumInv + betaold[beta.length-1];
          double icptdiff = beta[beta.length - 1] - betaold[beta.length-1];
          gamma += icptdiff;
          RES = 0;
        }
        double maxDiff = 0;
        for(int i = 0; i < beta.length-1; ++i){ // intercept does not count
          double diff = beta[i] - betaold[i];
          double d = diff*diff*gt.wxx[i]*wsumuInv;
          if (d > maxDiff) maxDiff = d;
        }
        System.arraycopy(beta,0,betaold,0,beta.length);
        if (maxDiff < betaEpsilon)
          break;
      }
      iter1Sum += iter1;
      gt = _data.eval(new GenWeightsFun(sparse, activeData, state.glmWeightsFun(), beta));
      if(!state.update(beta,gt._likelihood,1) || state.family() == GLM.Family.gaussian)
        break;
      double maxDiff = 0;
      for(int i = 0; i < beta.length-1; ++i){ // intercept does not count
        double diff = beta[i] - beta_old_outer[i];
        double d = diff*diff*gt.wxx[i]*wsumuInv;
        if (d > maxDiff) maxDiff = d;
      }
      System.arraycopy(beta,0,beta_old_outer,0,beta.length);
      if (maxDiff < betaEpsilon)
        break;
    }
    long endTimeTotalNaive = System.currentTimeMillis();
    Log.info(state.LogMsg("COD Naive took " + iter2 + ":" + iter1Sum + " iterations, " + (endTimeTotalNaive-startTimeTotalNaive)*0.001 + " seconds"));
    return state;
  }



  private static class GLMCoordinateDescentTaskSeqNaiveNum extends VecUtils.RawVec.RawVecFun<CoordinateDescentSolverNaive.DataChunk,IcedDouble> {
    final double _normMul;
    final double _normSub;
    final double _NA;
    final double intercept;
    final double _bOld; // current old value at j
    final double _bNew; // global beta @ j-1 that was just updated.
    final int _iter_cnt;
    final int _currId;


    public GLMCoordinateDescentTaskSeqNaiveNum(int iter_cnt, int currId, double intercept, double betaOld, double betaNew, double normMul, double normSub, double NA) { // pass it norm mul and norm sup - in the weights already done. norm
      _iter_cnt = iter_cnt;
      _currId = currId;
      this.intercept = intercept;
      _normMul = normMul;
      _normSub = normSub;
      _bOld = betaOld;
      _bNew = betaNew;
      _NA = NA;
    }
    public IcedDouble makeNew(){return new IcedDouble(0);}
    @Override
    public IcedDouble map(CoordinateDescentSolverNaive.DataChunk data, IcedDouble accum) {
      final double[] wChunk = data.w;
      final double[] ztildaChunk = data.zTilda;
      final double[] xPrev = _iter_cnt == 0?data.d1:data.d0;
      final double[] xCurr = _iter_cnt == 1?data.d1:data.d0;
      data._chks[_currId].getDoubles(xCurr, 0, xCurr.length, _NA);
      double res = 0;
      for (int i = 0; i < xCurr.length; ++i) { // going over all the rows in the chunk
        double w = wChunk[i];
        if (w == 0) continue;
        double x = (xCurr[i] - _normSub)*_normMul;
        xCurr[i] = x;
        double ztilda = ztildaChunk[i] + x * _bOld - xPrev[i] * _bNew;
        ztildaChunk[i] = ztilda;
        double diff = (ztilda - intercept);
        double wdiff = w * diff;
        res += wdiff * x;
      }
      accum._val += res;
      return accum;
    }

    @Override
    public IcedDouble reduce(IcedDouble x, IcedDouble y){
      x._val += y._val;
      return x;
    }
  }

  public static class CodIcptFun extends VecUtils.RawVec.RawVecFun<DataChunk,IcedDouble> {
    final double intercept;
    final double _bNew; // global beta @ j-1 that was just updated.
    final int _iter_cnt;

    public CodIcptFun(int iter_cnt, double intercept, double betaNew) { // pass it norm mul and norm sup - in the weights already done. norm
      _iter_cnt = iter_cnt;
      this.intercept = intercept;
      _bNew = betaNew;

    }
    @Override
    public IcedDouble map(DataChunk data, IcedDouble accum) {
      final double[] wChunk = data.w;
      final double[] ztildaChunk = data.zTilda;
      final double[] xPrev = _iter_cnt == 0?data.d1:data.d0;
      double res = 0;
      for (int i = 0; i < xPrev.length; ++i) { // going over all the rows in the chunk
        double w = wChunk[i];
        if (w == 0) continue;
        double ztilda = ztildaChunk[i] - xPrev[i] * _bNew;
        ztildaChunk[i] = ztilda;
        res += w*(ztilda - intercept);
      }
      accum._val += res;
      return accum;
    }
    @Override
    public IcedDouble reduce(IcedDouble x, IcedDouble y){
      x._val += y._val;return x;
    }
    @Override
    public IcedDouble makeNew() {return new IcedDouble(0);}
  }

  private static class SparseResiduals extends Iced{
    double _res;
    double _residual;
    double _residual_new;
  }

  private static final class CodSparseNumFun extends VecUtils.RawVec.RawVecFun<DataChunk,SparseResiduals> {
    final double _normMul;
    final double _NA;
    final double _bOld; // current old value at j
    final double _bDiff; // global beta @ j-1 that was just updated.
    final double _sparseOffset;
    final int _iter_cnt;
    final int _currId;

    public CodSparseNumFun(int iter_cnt, int currId, double sparseOffset, double betaOld, double betaDiff, double normMul, double NA) { // pass it norm mul and norm sup - in the weights already done. norm
      _iter_cnt = iter_cnt;
      _currId = currId;
      _normMul = normMul;
      _sparseOffset = sparseOffset;
      _bOld = betaOld;
      _bDiff = betaDiff;
      _NA = NA;
    }

    @Override
    public SparseResiduals map(DataChunk val,SparseResiduals accum) {
      final double[] wChunk = val.w;
      final double[] ztildaChunk = val.zTilda;
      final double[] xPrev = _iter_cnt == 0 ? val.d1 : val.d0;
      final double[] xCurr = _iter_cnt == 1 ? val.d1 : val.d0;
      final int[] idPrev = _iter_cnt == 0 ? val.c1 : val.c0;
      final int[] idCurr = _iter_cnt == 1 ? val.c1 : val.c0;
      int currlen = _currId == -1 ? 0 : val._chks[_currId].getSparseDoubles(xCurr, idCurr, _NA);
      double residual = 0;
      if (currlen < idCurr.length)
        idCurr[currlen] = -1;
      if (_bDiff != 0) {
        int i = 0, j;
        while (i < idPrev.length && (j = idPrev[i]) != -1) {
          double w = wChunk[j];
          if (w != 0) {
            double x = xPrev[i];
            double pred = x * _bDiff;
            ztildaChunk[j] -= pred;
            residual -= w * pred;
          }
          i++;
        }
      }
      accum._residual += residual;
      if (_currId != -1) {
        double res = 0;
        double residualNew = 0;
        int i = 0, j;
        if (_bOld != 0) {
          while (i < idCurr.length && (j = idCurr[i]) != -1) {
            double w = wChunk[j];
            if (w != 0) {
              double x = xCurr[i] * _normMul;
              xCurr[i] = x;
              double pred = x * _bOld;
              double diff = (ztildaChunk[j] + pred - _sparseOffset);
              res += w * x * diff;
              residualNew += w * pred;
            }
            i++;
          }
        } else {
          while (i < idCurr.length && (j = idCurr[i]) != -1) {
            double x = xCurr[i] * _normMul;
            res += x * wChunk[j] * (ztildaChunk[j] - _sparseOffset);
            xCurr[i] = x;
            i++;
          }
        }
        accum._res += res;
        accum._residual_new += residualNew;
      }
      return accum;
    }
    @Override
    public SparseResiduals reduce(SparseResiduals x,SparseResiduals y){
     x._res += y._res;
     x._residual += y._residual;
     x._residual_new += y._residual_new;
     return x;
    }

    @Override
    public SparseResiduals getResult(SparseResiduals x) {return x;}

    @Override
    public SparseResiduals makeNew() {return new SparseResiduals();}
  }

  private static class CodCatFun extends VecUtils.RawVec.RawVecFun<DataChunk,double[]> {
    final double[] _bOld; // current old value at j
    final double[] _bNew; // global beta @ j-1 that was just updated.
    final int[] _catMap;
    final int _iter_cnt;
    final int _NA;
    final double _intercept;
    final int _currId;

    public CodCatFun(int iter_cnt, int curr_id, double gamma, double[] betaOld, double[] betaNew, int[] catMap, int NA) {
      _iter_cnt = iter_cnt;
      _currId = curr_id;
      _intercept = gamma;
      _catMap = catMap;
      _bOld = betaOld;
      _bNew = betaNew;
      _NA = NA;
    }

    private void computeCatCat(double[] wChunk, double[] ztildaChunk, int[] xCurr, int[] xPrev, double[] res) {

      for (int i = 0; i < xCurr.length; ++i) { // going over all the rows in the chunk
        if (wChunk[i] == 0) continue;
        int cid = xCurr[i];
        if (_catMap != null) {  // some levels are ignored?
          cid = _catMap[cid];
          if (cid == -1) cid = res.length - 1;
          xCurr[i] = cid;
        }
        double ztilda = (ztildaChunk[i] = ztildaChunk[i] + _bOld[cid] - _bNew[xPrev[i]]);
        if (cid < res.length - 1)
          res[cid] += wChunk[i] * (ztilda - _intercept);
      }
    }

    private void computeCatStart(double[] wChunk, double[] ztildaChunk, int[] xCurr, double[] res) {
      for (int i = 0; i < xCurr.length; ++i) { // going over all the rows in the chunk
        if (wChunk[i] == 0) continue;
        int cid = xCurr[i];
        if (_catMap != null) {  // some levels are ignored?
          cid = _catMap[cid];
          if (cid == -1) cid = res.length - 1;
          xCurr[i] = cid;
        }
        if (cid < res.length - 1) {
          double ztilda = (ztildaChunk[i] = ztildaChunk[i] + _bOld[cid]);
          res[cid] += wChunk[i] * (ztilda - _intercept);
        }
      }
    }

    private void computeCatEnd(double[] wChunk, double[] ztildaChunk, int[] xPrev, double[] res) {
      double s = 0;
      for (int i = 0; i < xPrev.length; ++i) {
        double w = wChunk[i];
        if (w == 0) continue;
        double ztilda = ztildaChunk[i] -= _bNew[xPrev[i]];
        double diff = ztilda - _intercept;
        s += w * diff;
      }
      res[0] += s;
    }

    @Override
    public double[] reduce(double[] x, double[] y) {
      return ArrayUtils.add(x, y);
    }

    @Override
    public double[] getResult(double[] res) {
      return res;
    }

    @Override
    public double[] makeNew() {
      return _currId == -1 ? new double[1] : MemoryManager.malloc8d(_bOld.length);
    }

    @Override
    public double[] map(CoordinateDescentSolverNaive.DataChunk val, double[] res) {
      final double[] wChunk = val.w;
      final double[] ztildaChunk = val.zTilda;
      final int[] xPrev = _iter_cnt == 1 ? val.c0 : val.c1;
      final int[] xCurr = _iter_cnt == 0 ? val.c0 : val.c1;
      if (_currId != -1) val._chks[_currId].getIntegers(xCurr, 0, xCurr.length, _NA);
      if (_bNew == null) {
        computeCatStart(wChunk, ztildaChunk, xCurr, res);
      } else if (_currId == -1) {
        computeCatEnd(wChunk, ztildaChunk, xPrev, res);
      } else
        computeCatCat(wChunk, ztildaChunk, xCurr, xPrev, res);
      return res;
    }
  }

  private static class GenWeightsRes extends Iced {
    double [] denums;
    double [] wx;
    double [] wxx;
    double wr;
    double res;
    double mse;
    double wsum,wsumu;
    double _likelihood;
    transient volatile String _threadName;

    public GenWeightsRes reduce(GenWeightsRes z){
      denums = ArrayUtils.add(denums, z.denums);
      wx = ArrayUtils.add(wx, z.wx);
      wxx = ArrayUtils.add(wxx, z.wxx);
      wr += z.wr;
      res += z.res;
      mse += z.mse;
      wsum+=z.wsum;
      wsumu += z.wsumu;
      _likelihood += z._likelihood;
      return this;
    }
  }

  private static class GenWeightsFun extends VecUtils.RawVec.RawVecFun<DataChunk,GenWeightsRes> {
    final double[] _betaw;
    final DataInfo _dinfo;
    final boolean _sparse;
    final GLMModel.GLMWeightsFun _glmf;

    public GenWeightsFun(boolean sparse, DataInfo dinfo, GLMModel.GLMWeightsFun glmf, double[] betaw) {
      _glmf = glmf;
      _betaw = betaw;
      _dinfo = dinfo;
      _sparse = sparse;
    }

    @Override
    public GenWeightsRes makeNew() {
      GenWeightsRes res = new GenWeightsRes();
      res.denums = MemoryManager.malloc8d(_dinfo.fullN() + 1); // full N is expanded variables with categories
      res.wx = MemoryManager.malloc8d(_dinfo.fullN() + 1);
      res.wxx = MemoryManager.malloc8d(_dinfo.fullN() + 1);
      return res;
    }

    @Override
    public GenWeightsRes map(DataChunk data, final GenWeightsRes accum) {
      final String threadName = Thread.currentThread().getName();
      accum._threadName = threadName;
      final GLMModel.GLMWeights glmw = new GLMModel.GLMWeights();
      double[] wChunk = data.w;
      double[] zTilda = data.zTilda;
      DataInfo.Rows rows = _dinfo.rows(data._chks, _sparse);
      double sparseOffset = rows._sparse ? GLM.sparseOffset(_betaw, _dinfo) : 0;
      double res = 0, mse = 0, likelihood = 0, wr = 0, wsum = 0, wsumu = 0;
      for (int i = 0; i < rows._nrows; ++i) {
        DataInfo.Row r = rows.row(i);
        if (r.isBad() || r.weight == 0) {
          wChunk[i] = 0;
          continue;
        }
        final double y = r.response(0);
        final int numStart = _dinfo.numStart();
        final double eta = r.innerProduct(_betaw) + sparseOffset;
        _glmf.computeWeights(y, eta, 0, r.weight, glmw);
        likelihood += glmw.l;
        double residualSparse = (zTilda[i] = glmw.z - (eta - _betaw[_betaw.length - 1] - sparseOffset));
        double residual = glmw.z - eta;
        wr += glmw.w * residualSparse;
        res += glmw.w * residual;
        mse += glmw.w * residual * residual;
        assert glmw.w >= 0 || Double.isNaN(glmw.w) : "invalid weight " + glmw.w; // allow NaNs - can occur if line-search is needed!
        wChunk[i] = glmw.w;
        wsum += glmw.w;
        wsumu += r.weight; // just add the user observation weight for the scaling.
        for (int j = 0; j < r.nBins; ++j)  // go over cat variables
          accum.denums[r.binIds[j]] += glmw.w; // binIds skips the zeros.
        for (int j = 0; j < r.nNums; ++j) { // num vars
          int id = r.numIds == null ? (j + numStart) : r.numIds[j];
          double d = r.numVals[j];
          accum.denums[id] += glmw.w * d * d;
          accum.wx[id] += glmw.w * d;
          accum.wxx[id] += glmw.w * d * d;
        }
      }
      accum.res += res;
      accum._likelihood += likelihood;
      accum.mse += mse;
      accum.wr += wr;
      accum.wsum += wsum;
      accum.wsumu += wsumu;
      assert accum._threadName == threadName;
      accum._threadName = null;
      return accum;
    }

    @Override
    public GenWeightsRes reduce(GenWeightsRes x, GenWeightsRes y) {
      return x.reduce(y);
    }



    @Override
    public GenWeightsRes getResult(GenWeightsRes x) {
      if (_dinfo._cats > 0) {
        System.arraycopy(x.denums, 0, x.wx, 0, _dinfo.numStart());
        System.arraycopy(x.denums, 0, x.wxx, 0, _dinfo.numStart());
      }
      if (_sparse) { // adjust for skipped centering
        int numStart = _dinfo.numStart();
        for (int i = 0; i < _dinfo.numNums(); ++i) {
          int j = numStart + i;
          double delta = _dinfo.normSub(i) * _dinfo.normMul(i);
          x.wxx[j] = x.wxx[j] - 2 * delta * x.wx[j] + delta * delta * x.wsum;
          x.denums[j] = (x.denums[j] - 2 * delta * x.wx[j] + delta * delta * x.wsum);
        }
      }
      return x;
    }
  }

}

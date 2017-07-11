package hex.glm;

import hex.DataInfo;
import hex.optimization.ADMM;
import water.*;
import water.fvec.Chunk;
import water.util.FrameUtils;
import water.util.Log;
import water.util.VecUtils;

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
  protected void fit(GLM glmJob, ComputationState state){
    double [] beta = state.beta().clone();
    DataInfo activeData = state.activeData();
    double lambda = state.lambda();
    double alpha = state._alpha;
    double betaEpsilon = glmJob._parms._beta_epsilon*glmJob._parms._beta_epsilon;
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
    // public GLMGenerateWeightsTask(Key jobKey, DataInfo dinfo, GLMModel.GLMParameters glm, double[] betaw) {
    GLMTask.GLMGenerateWeightsTask2 gt = _data.eval(new GLMTask.GLMGenerateWeightsTask2(sparse, activeData, glmJob._parms, beta));
    long tdelta = System.currentTimeMillis() - t0;
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
        if(glmJob._job.stop_requested()) throw new Job.JobCancelledException();
        if (activeData._cats > 0) {
          double [] bNew = null, bOld = null;
          for (int i = 0; i < activeData._cats; ++i) {
            int catStart = activeData._catOffsets[i];
            int catEnd = activeData._catOffsets[i+1];
            bOld = Arrays.copyOfRange(betaold, catStart, catEnd+1);
            bOld[bOld.length-1] = 0;
            double [] res = _data.eval(new GLMTask.GLMCoordinateDescentTaskSeqNaiveCat2((iter_x = 1-iter_x),i,gamma,bOld,bNew,activeData.catMap(i),activeData.catNAFill(i)))._res;
//            if(i < 2 && iter1 < 2) System.out.println(i + ": " + "iter_x: " + iter_x + ": " + Arrays.toString(Arrays.copyOf(res,Math.min(10,res.length))));
            for(int j=0; j < res.length; ++j)
              beta[catStart+j] = bOld[j] = ADMM.shrinkage(res[j]*wsumuInv, l1pen) * denums[catStart+j];
            bNew = bOld;
          }

          RES = _data.eval(new GLMTask.GLMCoordinateDescentTaskSeqNaiveCat2((iter_x = 1-iter_x),-1,gamma,null,bNew,null,Integer.MAX_VALUE))._residual;
//          if(iter1 < 2) System.out.println("iter_x: " + iter_x + ": " + RES);
        }
        if(activeData.numNums() > 0){
          if(sparse){
            for (int i = 0; i < activeData.numNums(); i++) {
              int currIdx = i + activeData.numStart();
              int prevIdx = currIdx - 1;
              double delta = activeData.normSub(i)*activeData.normMul(i);
              gamma += delta*betaold[currIdx];
              beta[currIdx] = 0;
              GLMTask.GLMCoordinateDescentTaskSeqNaiveNumSparse2 tsk = _data.eval(new GLMTask.GLMCoordinateDescentTaskSeqNaiveNumSparse2((iter_x = 1 - iter_x),i + activeData._cats, gamma, betaold[currIdx], prevIdx >= activeData.numStart() ? beta[prevIdx]-betaold[prevIdx] : 0, activeData.normMul(i), 0));
              // sparse task calculates only residual updates across the non-zeros
              double RES_NEW = RES + tsk._residual + tsk._residualNew - delta*betaold[currIdx]*wsumx;
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
              RES += _data.eval(new GLMTask.GLMCoordinateDescentTaskSeqNaiveNumSparse2((iter_x = 1 - iter_x),-1, gamma, 0, beta[activeData.numStart() + activeData.numNums() - 1] - betaold[activeData.numStart() + activeData.numNums() - 1], 0, 0))._residual;
          } else {
            for (int i = 0; i < activeData.numNums(); i++) {
              int currIdx = i + activeData.numStart();
              int prevIdx = currIdx - 1;
              double tsk_res = _data.eval(new GLMTask.GLMCoordinateDescentTaskSeqNaiveNum2((iter_x = 1 - iter_x),i+activeData._cats, gamma, betaold[currIdx], prevIdx >= activeData.numStart() ? beta[prevIdx] : 0, activeData.normMul(i), activeData.normSub(i),activeData.normSub(i)));
              beta[currIdx] = ADMM.shrinkage(tsk_res * wsumuInv, l1pen) * denums[currIdx];
            }
            RES = _data.eval(new GLMTask.GLMCoordinateDescentTaskSeqNaiveNumIcpt2((iter_x = 1 - iter_x), gamma, beta[activeData.numStart() + activeData.numNums() - 1]));
          }
        }
        // compute intercept
        if (glmJob._parms._family != GLMModel.GLMParameters.Family.gaussian && !Double.isNaN(RES)) { // TODO handle no intercept case
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
      gt = _data.eval(new GLMTask.GLMGenerateWeightsTask2(sparse, activeData, glmJob._parms, beta));
      if(!glmJob.progress(beta.clone(),gt._likelihood) || glmJob._parms._family == GLMModel.GLMParameters.Family.gaussian)
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
    Log.info(glmJob.LogMsg("COD Naive took " + iter2 + ":" + iter1Sum + " iterations, " + (endTimeTotalNaive-startTimeTotalNaive)*0.001 + " seconds"));
  }
}

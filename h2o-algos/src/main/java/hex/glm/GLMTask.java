package hex.glm;

import hex.DataInfo;
import hex.DataInfo.Row;
import hex.FrameTask2;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMWeights;
import hex.glm.GLMModel.GLMWeightsFun;
import hex.gram.Gram;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.MathUtils;
import water.util.MathUtils.BasicStats;

import java.util.Arrays;

import static hex.glm.GLMTask.DataAddW2AugXZ.getCorrectChunk;

/**
 * All GLM related distributed tasks:
 *
 * YMUTask           - computes response means on actual datasets (if some rows are ignored - e.g ignoring rows with NA and/or doing cross-validation)
 * GLMGradientTask   - computes gradient at given Beta, used by L-BFGS, for KKT condition check
 * GLMLineSearchTask - computes residual deviance(s) at given beta(s), used by line search (both L-BFGS and IRLSM)
 * GLMIterationTask  - used by IRLSM to compute Gram matrix and response t(X) W X, t(X)Wz
 *
 * @author tomasnykodym
 */
public abstract class GLMTask  {
  final static double EPS=1e-10;
  final static double ZEROEQUAL = 1e-8;
  final static double ONEEQUAL = 1-1e-8;
  static class NullDevTask extends MRTask<NullDevTask> {
    double _nullDev;
    final double [] _ymu;
    final GLMWeightsFun _glmf;
    final boolean _hasWeights;
    final boolean _hasOffset;
    public NullDevTask(GLMWeightsFun glmf, double [] ymu, boolean hasWeights, boolean hasOffset) {
      _glmf = glmf;
      _ymu = ymu;
      _hasWeights = hasWeights;
      _hasOffset = hasOffset;
    }

    @Override public void map(Chunk [] chks) {
      int i = 0;
      int len = chks[0]._len;
      Chunk w = _hasWeights?chks[i++]:new C0DChunk(1.0,len);
      Chunk o = _hasOffset?chks[i++]:new C0DChunk(0.0,len);
      Chunk r = chks[i];
      if(_glmf._family != Family.multinomial) {
        double ymu = _glmf.link(_ymu[0]);
        for (int j = 0; j < len; ++j)
          _nullDev += w.atd(j)*_glmf.deviance(r.atd(j), _glmf.linkInv(ymu + o.atd(j)));
      } else {
        throw H2O.unimpl();
      }
    }
    @Override public void reduce(NullDevTask ndt) {_nullDev += ndt._nullDev;}
  }

  static class GLMResDevTask extends FrameTask2<GLMResDevTask> {
    final GLMWeightsFun _glmf;
    final double [] _beta;
    double _resDev = 0;
    long _nobs;
    double _likelihood;

    public GLMResDevTask(Key jobKey, DataInfo dinfo,GLMParameters parms, double [] beta) {
      super(null,dinfo, jobKey);
      _glmf = new GLMWeightsFun(parms);
      _beta = beta;
      _sparseOffset = _sparse?GLM.sparseOffset(_beta,_dinfo):0;
    }
    private transient GLMWeights _glmw;
    private final double _sparseOffset;

    @Override public boolean handlesSparseData(){return true;}
    @Override
    public void chunkInit() {
      _glmw = new GLMWeights();
    }
    @Override
    protected void processRow(Row r) {
      _glmf.computeWeights(r.response(0), r.innerProduct(_beta) + _sparseOffset, r.offset, r.weight, _glmw);
      _resDev += _glmw.dev;
      _likelihood += _glmw.l;
      ++_nobs;
    }
    @Override public void reduce(GLMResDevTask gt) {_nobs += gt._nobs; _resDev += gt._resDev; _likelihood += gt._likelihood;}
    public double avgDev(){return _resDev/_nobs;}
    public double dev(){return _resDev;}

  }

  static class GLMResDevTaskOrdinal extends FrameTask2<GLMResDevTaskOrdinal> {
    final double [][] _beta;
    double _likelihood;
    final int _nclasses;
    final int _lastClass;
    final int _secondToLast;
    long _nobs;

    public GLMResDevTaskOrdinal(Key jobKey, DataInfo dinfo, double [] beta, int nclasses) {
      super(null,dinfo, jobKey);
      _beta = ArrayUtils.convertTo2DMatrix(beta,beta.length/nclasses);
      _nclasses = nclasses;
      _lastClass = nclasses-1;
      _secondToLast = _lastClass - 1;

    }

    @Override public boolean handlesSparseData(){return true;}
    private transient double [] _sparseOffsets;

    @Override
    public void chunkInit() {
      _sparseOffsets = MemoryManager.malloc8d(_nclasses);
      if(_sparse)
        for(int c = 0; c < _nclasses; ++c)
          _sparseOffsets[c] = GLM.sparseOffset(_beta[c],_dinfo);
    }
    @Override
    protected void processRow(Row r) {
      _nobs++;
      int c = (int)r.response(0); // true response category
      if (c==0) { // for category 0
        double eta = r.innerProduct(_beta[0])+ _sparseOffsets[c];
        _likelihood -= r.weight * (eta-Math.log(1+Math.exp(eta)));
      } else if (c==_lastClass) { // for class nclass-1
        _likelihood += r.weight * Math.log(1+Math.exp(r.innerProduct(_beta[_secondToLast])+ _sparseOffsets[c]));
      } else { // for category from 1 to nclass-2
        double eta = Math.exp(r.innerProduct(_beta[c])+_sparseOffsets[c]);
        double etaM1 = Math.exp(r.innerProduct(_beta[c])+_sparseOffsets[c-1]);
        _likelihood -= r.weight * Math.log(eta/(1+eta)-etaM1/(1+etaM1));
      }
    }
    @Override public void reduce(GLMResDevTaskOrdinal gt) {_nobs += gt._nobs; _likelihood += gt._likelihood;}

    public double avgDev(){return _likelihood*2/_nobs;}
    public double dev(){return _likelihood*2;}
  }

  static class GLMResDevTaskMultinomial extends FrameTask2<GLMResDevTaskMultinomial> {
    final double [][] _beta;
    double _likelihood;
    final int _nclasses;
    long _nobs;

    public GLMResDevTaskMultinomial(Key jobKey, DataInfo dinfo, double [] beta, int nclasses) {
      super(null,dinfo, jobKey);
      _beta = ArrayUtils.convertTo2DMatrix(beta,beta.length/nclasses);
      _nclasses = nclasses;
    }

    @Override public boolean handlesSparseData(){return true;}
    private transient double [] _sparseOffsets;

    @Override
    public void chunkInit() {
      _sparseOffsets = MemoryManager.malloc8d(_nclasses);
      if(_sparse)
        for(int c = 0; c < _nclasses; ++c)
          _sparseOffsets[c] = GLM.sparseOffset(_beta[c],_dinfo);
    }
    @Override
    protected void processRow(Row r) {
      _nobs++;
      double sumExp = 0;
      for(int c = 0; c < _nclasses; ++c)
        sumExp += Math.exp(r.innerProduct(_beta[c]) + _sparseOffsets[c]);
      int c = (int)r.response(0);
      _likelihood -= r.weight * ((r.innerProduct(_beta[c]) + _sparseOffsets[c]) - Math.log(sumExp));
    }
    @Override public void reduce(GLMResDevTaskMultinomial gt) {_nobs += gt._nobs; _likelihood += gt._likelihood;}

    public double avgDev(){return _likelihood*2/_nobs;}
    public double dev(){return _likelihood*2;}
  }

 static class WeightedSDTask extends MRTask<WeightedSDTask> {
   final int _weightId;
   final double [] _mean;
   public double [] _varSum;
   public WeightedSDTask(int wId, double [] mean){
     _weightId = wId;
     _mean = mean;
   }
   @Override public void map(Chunk [] chks){
     double [] weights = null;
     if(_weightId != - 1){
       weights = MemoryManager.malloc8d(chks[_weightId]._len);
       chks[_weightId].getDoubles(weights,0,weights.length);
       chks = ArrayUtils.remove(chks,_weightId);
     }
     _varSum = MemoryManager.malloc8d(_mean.length);
     double [] vals = MemoryManager.malloc8d(chks[0]._len);
     int [] ids = MemoryManager.malloc4(chks[0]._len);
     for(int c = 0; c < _mean.length; ++c){
       double mu = _mean[c];
       int n = chks[c].getSparseDoubles(vals,ids);
       double s = 0;
       for(int i = 0; i < n; ++i) {
         double d = vals[i];
         if(Double.isNaN(d)) // NAs are either skipped or replaced with mean (i.e. can also be skipped)
           continue;
         d = d - mu;
         if(_weightId != -1)
           s += weights[ids[i]]*d*d;
         else
           s += d*d;
       }
       _varSum[c] = s;
     }
   }
   public void reduce(WeightedSDTask t){
     ArrayUtils.add(_varSum,t._varSum);
   }
 }
 static public class YMUTask extends MRTask<YMUTask> {
   double _yMin = Double.POSITIVE_INFINITY, _yMax = Double.NEGATIVE_INFINITY;
   final int _responseId;
   final int _weightId;
   final int _offsetId;
   final int _nums; // number of numeric columns
   final int _numOff;
   final boolean _skipNAs;
   final boolean _computeWeightedMeanSigmaResponse;

   private BasicStats _basicStats;
   private BasicStats _basicStatsResponse;
   double [] _yMu;
   final int _nClasses;


   private double [] _predictorSDs;
   private final boolean _expandedResponse; // true iff family == multinomial and response has been maually expanded into binary columns

   public double [] predictorMeans(){return _basicStats.mean();}
   public double [] predictorSDs(){
     if(_predictorSDs != null) return _predictorSDs;
     return (_predictorSDs = _basicStats.sigma());
   }

   public double [] responseMeans(){return _basicStatsResponse.mean();}
   public double [] responseSDs(){
     return _basicStatsResponse.sigma();
   }

   public YMUTask(DataInfo dinfo, int nclasses, boolean computeWeightedMeanSigmaResponse, boolean skipNAs, boolean haveResponse, boolean expandedResponse) {
     _nums = dinfo._nums;
     _numOff = dinfo._cats;
     _responseId = haveResponse ? dinfo.responseChunkId(0) : -1;
     _weightId = dinfo._weights?dinfo.weightChunkId():-1;
     _offsetId = dinfo._offset?dinfo.offsetChunkId():-1;
     _nClasses = nclasses;
     _computeWeightedMeanSigmaResponse = computeWeightedMeanSigmaResponse;
     _skipNAs = skipNAs;
     _expandedResponse = _nClasses == 1 || expandedResponse;
   }

   @Override public void setupLocal(){}

   @Override public void map(Chunk [] chunks) {
     _yMu = new double[_nClasses];
     double [] ws = MemoryManager.malloc8d(chunks[0].len());
     if(_weightId != -1)
       chunks[_weightId].getDoubles(ws,0,ws.length);
     else
      Arrays.fill(ws,1);
     boolean changedWeights = false;
     if(_skipNAs) { // first find the rows to skip, need to go over all chunks including categoricals
       double [] vals = MemoryManager.malloc8d(chunks[0]._len);
       int [] ids = MemoryManager.malloc4(vals.length);
       for (int i = 0; i < chunks.length; ++i) {
         int n = vals.length;
         if(chunks[i].isSparseZero())
           n = chunks[i].getSparseDoubles(vals,ids);
         else
          chunks[i].getDoubles(vals,0,n);
         for (int r = 0; r < n; ++r) {
           if (ws[r] != 0 && Double.isNaN(vals[r])) {
             ws[r] = 0;
             changedWeights = true;
           }
         }
       }
       if(changedWeights && _weightId != -1)
         chunks[_weightId].set(ws);
     }
     Chunk response = _responseId < 0 ? null : chunks[_responseId];
     double [] numsResponse = null;
     _basicStats = new BasicStats(_nums);
     if(_computeWeightedMeanSigmaResponse) {
       _basicStatsResponse = new BasicStats(_nClasses);
       numsResponse = MemoryManager.malloc8d(_nClasses);
     }
     // compute basic stats for numeric predictors
     for(int i = 0; i < _nums; ++i) {
       Chunk c = chunks[i + _numOff];
       double w;
       for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
         if ((w = ws[r]) == 0) continue;
         double d = c.atd(r);
         _basicStats.add(d, w, i);
       }
     }
     if (response == null) return;
     long nobs = 0;
     double wsum = 0;
     for(double w:ws) {
       if(w != 0)++nobs;
       wsum += w;
     }
     _basicStats.setNobs(nobs,wsum);
     // compute the mean for the response
     // autoexpand categoricals into binary vecs
     for(int r = 0; r < response._len; ++r) {
       double w;
       if((w = ws[r]) == 0)
         continue;
       if(_computeWeightedMeanSigmaResponse) {
         //FIXME: Add support for subtracting offset from response
         if(_expandedResponse) {
           for (int i = 0; i < _nClasses; ++i)
             numsResponse[i] = chunks[chunks.length - _nClasses + i].atd(r);
         } else {
           Arrays.fill(numsResponse,0);
           double d = response.atd(r);
           if(Double.isNaN(d))
             Arrays.fill(numsResponse,Double.NaN);
           else
             numsResponse[(int)d] = 1;
         }
         _basicStatsResponse.add(numsResponse,w);
       }
       double d = response.atd(r);
       if(!Double.isNaN(d)) {
         if (_nClasses > 2)
           _yMu[(int) d] += w;
         else
           _yMu[0] += w*d;
         if (d < _yMin)
           _yMin = d;
         if (d > _yMax)
           _yMax = d;
       }
     }
     if(_basicStatsResponse != null)_basicStatsResponse.setNobs(nobs,wsum);
     for(int i = 0; i < _nums; ++i) {
       if(chunks[i+_numOff].isSparseZero())
         _basicStats.fillSparseZeros(i);
       else if(chunks[i+_numOff].isSparseNA())
         _basicStats.fillSparseNAs(i);
     }
   }
   @Override public void postGlobal() {
     ArrayUtils.mult(_yMu,1.0/_basicStats._wsum);
   }

   @Override public void reduce(YMUTask ymt) {
     if(ymt._basicStats.nobs() > 0 && ymt._basicStats.nobs() > 0) {
       ArrayUtils.add(_yMu,ymt._yMu);
       if(_yMin > ymt._yMin)
         _yMin = ymt._yMin;
       if(_yMax < ymt._yMax)
         _yMax = ymt._yMax;
       _basicStats.reduce(ymt._basicStats);
       if(_computeWeightedMeanSigmaResponse)
         _basicStatsResponse.reduce(ymt._basicStatsResponse);
     } else if (_basicStats.nobs() == 0) {
       _yMu = ymt._yMu;
       _yMin = ymt._yMin;
       _yMax = ymt._yMax;
       _basicStats = ymt._basicStats;
       _basicStatsResponse = ymt._basicStatsResponse;
     }
   }
   public double wsum() {return _basicStats._wsum;}

   public long nobs() {return _basicStats.nobs();}
 }

  static double  computeMultinomialEtas(double [] etas, double [] exps) {
    double maxRow = ArrayUtils.maxValue(etas);
    double sumExp = 0;
    int K = etas.length;
    for(int c = 0; c < K; ++c) {
      double x = Math.exp(etas[c] - maxRow);
      sumExp += x;
      exps[c+1] = x;
    }
    double reg = 1.0/(sumExp);
    for(int c = 0; c < etas.length; ++c)
      exps[c+1] *= reg;
    exps[0] = 0;
    exps[0] = ArrayUtils.maxIndex(exps)-1;
    return Math.log(sumExp) + maxRow;
  }
  
  static abstract class GLMGradientTask extends MRTask<GLMGradientTask> {
    final double [] _beta;
    public double [] _gradient;
    public double _likelihood;
    final transient  double _currentLambda;
    final transient double _reg;
    protected final DataInfo _dinfo;


    protected GLMGradientTask(Key jobKey, DataInfo dinfo, double reg, double lambda, double[] beta){
      _dinfo = dinfo;
      _beta = beta.clone();
      _reg = reg;
      _currentLambda = lambda;

    }
    protected abstract void computeGradientMultipliers(double [] es, double [] ys, double [] ws);

    private final void computeCategoricalEtas(Chunk [] chks, double [] etas, double [] vals, int [] ids) {
      // categoricals
      for(int cid = 0; cid < _dinfo._cats; ++cid){
        Chunk c = chks[cid];
        if(c.isSparseZero()) {
          int nvals = c.getSparseDoubles(vals,ids,-1);
          for(int i = 0; i < nvals; ++i){
            int id = _dinfo.getCategoricalId(cid,(int)vals[i]);
            if(id >=0) etas[ids[i]] += _beta[id];
          }
        } else {
          c.getIntegers(ids, 0, c._len,-1);
          for(int i = 0; i < ids.length; ++i){
            int id = _dinfo.getCategoricalId(cid,ids[i]);
            if(id >=0) etas[i] += _beta[id];
          }
        }
      }
    }

    private final void computeCategoricalGrads(Chunk [] chks, double [] etas, double [] vals, int [] ids) {
      // categoricals
      for(int cid = 0; cid < _dinfo._cats; ++cid){
        Chunk c = chks[cid];
        if(c.isSparseZero()) {
          int nvals = c.getSparseDoubles(vals,ids,-1);
          for(int i = 0; i < nvals; ++i){
            int id = _dinfo.getCategoricalId(cid,(int)vals[i]);
            if(id >=0) _gradient[id] += etas[ids[i]];
          }
        } else {
          c.getIntegers(ids, 0, c._len,-1);
          for(int i = 0; i < ids.length; ++i){
            int id = _dinfo.getCategoricalId(cid,ids[i]);
            if(id >=0) _gradient[id] += etas[i];
          }
        }
      }
    }

    private final void computeNumericEtas(Chunk [] chks, double [] etas, double [] vals, int [] ids) {
      int numOff = _dinfo.numStart();
      for(int cid = 0; cid < _dinfo._nums; ++cid){
        double scale = _dinfo._normMul != null?_dinfo._normMul[cid]:1;
        double off = _dinfo._normSub != null?_dinfo._normSub[cid]:0;
        double NA = _dinfo._numNAFill[cid];
        Chunk c = chks[cid+_dinfo._cats];
        double b = scale*_beta[numOff+cid];
        if(c.isSparseZero()){
          int nvals = c.getSparseDoubles(vals,ids,NA);
          for(int i = 0; i < nvals; ++i)
            etas[ids[i]] += vals[i] * b;
        } else if(c.isSparseNA()){
          int nvals = c.getSparseDoubles(vals,ids,NA);
          for(int i = 0; i < nvals; ++i)
            etas[ids[i]] += (vals[i] - off) * b;
        } else {
          c.getDoubles(vals,0,vals.length,NA);
          for(int i = 0; i < vals.length; ++i)
            etas[i] += (vals[i] - off) * b;
        }
      }
    }

    private final void computeNumericGrads(Chunk [] chks, double [] etas, double [] vals, int [] ids) {
      int numOff = _dinfo.numStart();
      for(int cid = 0; cid < _dinfo._nums; ++cid){
        double NA = _dinfo._numNAFill[cid];
        Chunk c = chks[cid+_dinfo._cats];
        double scale = _dinfo._normMul == null?1:_dinfo._normMul[cid];
        double offset = _dinfo._normSub == null?0:_dinfo._normSub[cid];
        if(c.isSparseZero()){
          double g = 0;
          int nVals = c.getSparseDoubles(vals,ids,NA);
          for(int i = 0; i < nVals; ++i)
            g += (vals[i]-offset)*scale*etas[ids[i]];
          _gradient[numOff+cid] = g;
        } else if(c.isSparseNA()){
          double off = _dinfo._normSub == null?0:_dinfo._normSub[cid];
          double g = 0;
          int nVals = c.getSparseDoubles(vals,ids,NA);
          for(int i = 0; i < nVals; ++i)
            g += (vals[i]-off)*scale*etas[ids[i]];
          _gradient[numOff+cid] = g;
        } else {
          double off = _dinfo._normSub == null?0:_dinfo._normSub[cid];
          c.getDoubles(vals,0,vals.length,NA);
          double g = 0;
          for(int i = 0; i < vals.length; ++i)
            g += (vals[i]-off)*scale*etas[i];
          _gradient[numOff+cid] = g;
        }
      }
    }

    public void map(Chunk [] chks) {
      _gradient = MemoryManager.malloc8d(_beta.length);
      Chunk response = chks[chks.length-_dinfo._responses];
      Chunk weights = _dinfo._weights?chks[_dinfo.weightChunkId()]:new C0DChunk(1,response._len);
      double [] ws = weights.getDoubles(MemoryManager.malloc8d(weights._len),0,weights._len);
      double [] ys = response.getDoubles(MemoryManager.malloc8d(weights._len),0,response._len);
      double [] etas = MemoryManager.malloc8d(response._len);
      if(_dinfo._offset)
        chks[_dinfo.offsetChunkId()].getDoubles(etas,0,etas.length);
      double sparseOffset = 0;
      int numStart = _dinfo.numStart();
      if(_dinfo._normSub != null)
        for(int i = 0; i < _dinfo._nums; ++i)
          if(chks[_dinfo._cats + i].isSparseZero())
            sparseOffset -= _beta[numStart + i]*_dinfo._normSub[i]*_dinfo._normMul[i];
      ArrayUtils.add(etas,sparseOffset + _beta[_beta.length-1]);
      double [] vals = MemoryManager.malloc8d(response._len);
      int [] ids = MemoryManager.malloc4(response._len);
      computeCategoricalEtas(chks,etas,vals,ids);
      computeNumericEtas(chks,etas,vals,ids);
      computeGradientMultipliers(etas,ys,ws);
      // walk the chunks again, add to the gradient
      computeCategoricalGrads(chks,etas,vals,ids);
      computeNumericGrads(chks,etas,vals,ids);
      // add intercept
      _gradient[_gradient.length-1] = ArrayUtils.sum(etas);
      if(_dinfo._normSub != null) {
        double icpt = _gradient[_gradient.length-1];
        for(int i = 0; i < _dinfo._nums; ++i) {
          if(chks[_dinfo._cats+i].isSparseZero()) {
            double d = _dinfo._normSub[i] * _dinfo._normMul[i];
            _gradient[numStart + i] -= d * icpt;
          }
        }
      }
    }

    @Override
    public final void reduce(GLMGradientTask gmgt){
      ArrayUtils.add(_gradient,gmgt._gradient);
      _likelihood += gmgt._likelihood;
    }
    @Override public final void postGlobal(){
      ArrayUtils.mult(_gradient,_reg);
      for(int j = 0; j < _beta.length - 1; ++j)
        _gradient[j] += _currentLambda * _beta[j];
    }
  }

  static class GLMGenericGradientTask extends GLMGradientTask {
    private final GLMWeightsFun _glmf;
    public GLMGenericGradientTask(Key jobKey, DataInfo dinfo, GLMParameters parms, double lambda, double[] beta) {
      super(jobKey, dinfo, parms._obj_reg, lambda, beta);
      _glmf = new GLMWeightsFun(parms);
    }

    @Override protected void computeGradientMultipliers(double [] es, double [] ys, double [] ws){
      double l = 0;
      for(int i = 0; i < es.length; ++i) {
        if (Double.isNaN(ys[i]) || ws[i] == 0) {
          es[i] = 0;
        } else {
          double mu = _glmf.linkInv(es[i]);
          mu = mu==0?hex.glm.GLMModel._EPS:mu;
          l += ws[i] * _glmf.likelihood(ys[i], mu);
          double var = _glmf.variance(mu);
          if (var < hex.glm.GLMModel._EPS) var = hex.glm.GLMModel._EPS; // es is the gradient without the predictor term
          if (_glmf._family.equals(Family.tweedie)) {
            _glmf._oneOeta = 1.0/(es[i]==0?hex.glm.GLMModel._EPS:es[i]);
            _glmf._oneOetaSquare = _glmf._oneOeta*_glmf._oneOeta;
            es[i] = ws[i]*_glmf.linkInvDeriv(mu)*(_glmf._var_power==1?(1-ys[i]/mu):
                    (_glmf._var_power==2?(1/mu-ys[i]*Math.pow(mu, -_glmf._var_power)):
                            (Math.pow(mu, _glmf._oneMinusVarPower)-ys[i]*Math.pow(mu, -_glmf._var_power))));
          } else {
            es[i] = ws[i] * (mu - ys[i]) / (var * _glmf.linkDeriv(mu));
          }
        }
      }
      _likelihood = l;
    }
  }

  static class GLMPoissonGradientTask extends GLMGradientTask {
    private final GLMWeightsFun _glmf;
    public GLMPoissonGradientTask(Key jobKey, DataInfo dinfo, GLMParameters parms, double lambda, double[] beta) {
      super(jobKey, dinfo, parms._obj_reg, lambda, beta);
      _glmf = new GLMWeightsFun(parms);
    }
    @Override protected void computeGradientMultipliers(double [] es, double [] ys, double [] ws){
      double l = 0;
      for(int i = 0; i < es.length; ++i) {
        if (Double.isNaN(ys[i]) || ws[i] == 0) {
          es[i] = 0;
        } else {
          double eta = es[i];
          double mu = Math.exp(eta);
          double yr = ys[i];
          double diff = mu - yr;
          l += ws[i] * (yr == 0?mu:yr*Math.log(yr/mu) + diff);  // todo: looks wrong to me... double check
          es[i] = ws[i]*diff;
        }
      }
      _likelihood = 2*l;
    }
  }

  static class GLMNegativeBinomialGradientTask extends GLMGradientTask {
    private final GLMWeightsFun _glmf;
    public GLMNegativeBinomialGradientTask(Key jobKey, DataInfo dinfo, GLMParameters parms, double lambda, double[] beta) {
      super(jobKey, dinfo, parms._obj_reg, lambda, beta);
      _glmf = new GLMWeightsFun(parms);
    }
    @Override protected void computeGradientMultipliers(double [] es, double [] ys, double [] ws) {
      double l = 0;
      for(int i = 0; i < es.length; ++i) {
        if (Double.isNaN(ys[i]) || ws[i] == 0) {
          es[i] = 0;
        } else {
          double eta = es[i];
          double mu = _glmf.linkInv(eta);
          double yr = ys[i];
          if ((mu > 0) && (yr > 0)) {  // response and predictions are all nonzeros
            double invSum =1.0/(1.0+mu*_glmf._theta);
            double muDeriv = _glmf.linkInvDeriv(mu);
            es[i] = ws[i] * (invSum-yr/mu+_glmf._theta*yr*invSum) * muDeriv; // gradient of -llh.  CHECKED-log/CHECKED-identity
            l -= ws[i] * (sumOper(yr, _glmf._invTheta, 0)-(yr+_glmf._invTheta)*Math.log(1+_glmf._theta*mu)+
                  yr*Math.log(mu)+yr*Math.log(_glmf._theta)); // store the -llh, with everything.  CHECKED-Log/CHECKED-identity
          } else if ((mu > 0) && (yr==0)) {
            es[i] = ws[i]*(_glmf.linkInvDeriv(mu)/(1.0+_glmf._theta*mu)); // CHECKED-log/CHECKED-identity
            l += _glmf._invTheta*Math.log(1+_glmf._theta*mu);  //CHECKED-log/CHECKED-identity
          } // no update otherwise
        }
      }
      _likelihood = l;
    }
  }

  static double sumOper(double y, double multiplier, int opVal) {
    double summation = 0.0;
      for (int val = 0; val < y; val++) {
        double temp = opVal==0?Math.log((val+multiplier)/(val+1)):1.0/(val*multiplier*multiplier+multiplier);
        summation += opVal==0?temp:(opVal==1?temp:(opVal==2?temp*temp*(2*val*multiplier+1):Math.log(multiplier+val)));
      }
    return summation;
  }
  
  static class GLMQuasiBinomialGradientTask extends GLMGradientTask {
    private final GLMWeightsFun _glmf;
    public GLMQuasiBinomialGradientTask(Key jobKey, DataInfo dinfo, GLMParameters parms, double lambda, double[] beta) {
      super(jobKey, dinfo, parms._obj_reg, lambda, beta);
      _glmf = new GLMWeightsFun(parms);
    }
    @Override protected void computeGradientMultipliers(double [] es, double [] ys, double [] ws){
      double l = 0;
      for(int i = 0; i < es.length; ++i){
        double p = _glmf.linkInv(es[i]);
        if(p == 0) p = 1e-15;
        if(p == 1) p = 1 - 1e-15;
        es[i] = -ws[i]*(ys[i]-p);
        l += ys[i]*Math.log(p) + (1-ys[i])*Math.log(1-p);
      }
      _likelihood = -l;
    }
  }


  static class GLMBinomialGradientTask extends GLMGradientTask {
    public GLMBinomialGradientTask(Key jobKey, DataInfo dinfo, GLMParameters parms, double lambda, double [] beta) {
      super(jobKey,dinfo,parms._obj_reg,lambda,beta);
      assert (parms._family == Family.binomial && parms._link == Link.logit) || (parms._family == Family.fractionalbinomial && parms._link == Link.logit);
    }

    @Override
    protected void computeGradientMultipliers(double[] es, double[] ys, double[] ws) {
      for(int i = 0; i < es.length; ++i) {
        if(Double.isNaN(ys[i]) || ws[i] == 0){es[i] = 0; continue;}
        double e = es[i], w = ws[i];
        double yr = ys[i];
        double ym = 1.0 / (Math.exp(-e) + 1.0);
        if(ym != yr) _likelihood += w*((MathUtils.y_log_y(yr, ym)) + MathUtils.y_log_y(1 - yr, 1 - ym));
        es[i] = ws[i] * (ym - yr);
      }
    }
  }

  static class GLMGaussianGradientTask extends GLMGradientTask {
    public GLMGaussianGradientTask(Key jobKey, DataInfo dinfo, GLMParameters parms, double lambda, double [] beta) {
      super(jobKey,dinfo,parms._obj_reg,lambda,beta);
      assert parms._family == Family.gaussian && parms._link == Link.identity;
    }

    @Override
    protected void computeGradientMultipliers(double[] es, double[] ys, double[] ws) {
      for(int i = 0; i < es.length; ++i) {
        double w = ws[i];
        if(w == 0 || Double.isNaN(ys[i])){
          es[i] = 0;
          continue;
        }
        double e = es[i], y = ys[i];
        double d = (e-y);
        double wd = w*d;
        _likelihood += wd*d;
        es[i] = wd;
      }
    }
  }

  static class GLMMultinomialLikelihoodTask extends GLMMultinomialGradientBaseTask {
    public GLMMultinomialLikelihoodTask(Job job, DataInfo dinfo, double lambda, double[][] beta, double reg) {
      super(job, dinfo, lambda, beta, reg);
    }

    public GLMMultinomialLikelihoodTask(Job job, DataInfo dinfo, double lambda, double[][] beta, GLMParameters glmp) {
      super(job, dinfo, lambda, beta, glmp);
    }

    @Override
    public void calMultipliersNGradients(double[][] etas, double[][] etasOffset, double[] ws, double[] vals,
                                         int[] ids, Chunk response, Chunk[] chks, int M, int P, int numStart) {
      computeGradientMultipliers(etas, response.getDoubles(vals, 0, M), ws);
    }
  }

  // share between multinomial and ordinal regression
  static abstract class GLMMultinomialGradientBaseTask extends MRTask<GLMMultinomialGradientBaseTask> {
    final double[][] _beta;
    final transient double _currentLambda;
    final transient double _reg;
    public double[][] _gradient;
    double _likelihood;
    Job _job;
    final boolean _sparse;
    final DataInfo _dinfo;
    // parameters used by ordinal regression
    Link _link;           // link function, e.g. ologit, ologlog, oprobit
    GLMParameters _glmp;  // parameter used to access linkinv and linkinvderiv functions
    int _secondToLast;    // denote class label nclass-2
    int _theLast;         // denote class label nclass-1
    int _interceptId;     // index of offset/intercept in double[][] _beta

    /**
     * @param job
     * @param dinfo
     * @param lambda
     * @param beta   coefficients as 2D array [P][K]
     * @param reg
     */
    public GLMMultinomialGradientBaseTask(Job job, DataInfo dinfo, double lambda, double[][] beta, double reg) {
      _currentLambda = lambda;
      _reg = reg;
      // need to flip the beta
      _beta = new double[beta[0].length][beta.length];
      for (int i = 0; i < _beta.length; ++i)
        for (int j = 0; j < _beta[i].length; ++j)
          _beta[i][j] = beta[j][i];
      _job = job;
      _sparse = FrameUtils.sparseRatio(dinfo._adaptedFrame) < .125;
      _dinfo = dinfo;
      if (_dinfo._offset) throw H2O.unimpl();
    }

    public GLMMultinomialGradientBaseTask(Job job, DataInfo dinfo, double lambda, double[][] beta, GLMParameters glmp) {
      this(job, dinfo, lambda, beta, glmp._obj_reg);
      _theLast = beta.length - 1;       // initialize ordinal regression parameters
      _secondToLast = _theLast - 1;
      _interceptId = _beta.length - 1;
      _link = glmp._link;
      _glmp = glmp;
    }

    // common between multinomial and ordinal
    public final void computeCategoricalEtas(Chunk[] chks, double[][] etas, double[] vals, int[] ids) {
      // categoricals
      for (int cid = 0; cid < _dinfo._cats; ++cid) {
        Chunk c = chks[cid];
        if (c.isSparseZero()) {
          int nvals = c.getSparseDoubles(vals, ids, -1);
          for (int i = 0; i < nvals; ++i) {
            int id = _dinfo.getCategoricalId(cid, (int) vals[i]);
            if (id >= 0) ArrayUtils.add(etas[ids[i]], _beta[id]);
          }
        } else {
          c.getIntegers(ids, 0, c._len, -1);
          for (int i = 0; i < ids.length; ++i) {
            int id = _dinfo.getCategoricalId(cid, ids[i]);
            if (id >= 0) ArrayUtils.add(etas[i], _beta[id]);
          }
        }
      }
    }

    public final void computeCategoricalGrads(Chunk[] chks, double[][] etas, double[] vals, int[] ids) {
      // categoricals
      for (int cid = 0; cid < _dinfo._cats; ++cid) {
        Chunk c = chks[cid];
        if (c.isSparseZero()) {
          int nvals = c.getSparseDoubles(vals, ids, -1);
          for (int i = 0; i < nvals; ++i) {
            int id = _dinfo.getCategoricalId(cid, (int) vals[i]);
            if (id >= 0) ArrayUtils.add(_gradient[id], etas[ids[i]]);
          }
        } else {
          c.getIntegers(ids, 0, c._len, -1);
          for (int i = 0; i < ids.length; ++i) {
            int id = _dinfo.getCategoricalId(cid, ids[i]);
            if (id >= 0) ArrayUtils.add(_gradient[id], etas[i]);
          }
        }
      }
    }

    public final void computeNumericEtas(Chunk[] chks, double[][] etas, double[] vals, int[] ids) {
      int numOff = _dinfo.numStart();
      for (int cid = 0; cid < _dinfo._nums; ++cid) {
        double[] b = _beta[numOff + cid];
        double scale = _dinfo._normMul != null ? _dinfo._normMul[cid] : 1;
        double NA = _dinfo._numNAFill[cid];
        Chunk c = chks[cid + _dinfo._cats];
        if (c.isSparseZero() || c.isSparseNA()) {
          int nvals = c.getSparseDoubles(vals, ids, NA);
          for (int i = 0; i < nvals; ++i) {
            double d = vals[i] * scale;
            ArrayUtils.wadd(etas[ids[i]], b, d);
          }
        } else {
          c.getDoubles(vals, 0, vals.length, NA);
          double off = _dinfo._normSub != null ? _dinfo._normSub[cid] : 0;
          for (int i = 0; i < vals.length; ++i) {
            double d = (vals[i] - off) * scale;
            ArrayUtils.wadd(etas[i], b, d);
          }
        }
      }
    }

    public final void computeNumericGrads(Chunk[] chks, double[][] etas, double[] vals, int[] ids) {
      int numOff = _dinfo.numStart();
      for (int cid = 0; cid < _dinfo._nums; ++cid) {
        double[] g = _gradient[numOff + cid];
        double NA = _dinfo._numNAFill[cid];
        Chunk c = chks[cid + _dinfo._cats];
        double scale = _dinfo._normMul == null ? 1 : _dinfo._normMul[cid];
        if (c.isSparseZero() || c.isSparseNA()) {
          int nVals = c.getSparseDoubles(vals, ids, NA);
          for (int i = 0; i < nVals; ++i)
            ArrayUtils.wadd(g, etas[ids[i]], vals[i] * scale);
        } else {
          double off = _dinfo._normSub == null ? 0 : _dinfo._normSub[cid];
          c.getDoubles(vals, 0, vals.length, NA);
          for (int i = 0; i < vals.length; ++i) {
            ArrayUtils.wadd(g, etas[i], (vals[i] - off) * scale);
          }
        }
      }
    }


    // This method will compute the multipliers for gradient calculation of the betas in etas and for
    // the intercepts in etas_offsets for each row of data
    final void computeGradientMultipliersLH(double [][] etas, double [][] etasOffset, double [] ys, double [] ws) {
      int K = _beta[0].length;    // number of class
      double[] tempEtas = new double[K];  // store original etas
      int y;  // get and store response class category
      double yJ, yJm1;
      for (int row = 0; row < etas.length; row++) { // calculate the multiplier from each row
        double w = ws[row];
        if (w==0) {
          Arrays.fill(etas[row], 0);    // zero out etas for current row
          continue;
        }
        // note that, offset is different for all class, beta is shared for all classes
        System.arraycopy(etas[row], 0, tempEtas, 0, K); // copy data over to tempEtas
        Arrays.fill(etas[row], 0);    // zero out etas for current row
        y = (int) ys[row];  // get response class category
        if (y==0) { // response is in 0th category
          etasOffset[row][0] = _glmp.linkInv(tempEtas[0])-1;
          etas[row][0] = etasOffset[row][0];
          _likelihood -= w*tempEtas[y]-Math.log(1+Math.exp(tempEtas[y]));
        } else if (y==_theLast) { // response is in last category
          etasOffset[row][_secondToLast] = _glmp.linkInv(tempEtas[_secondToLast]);
          etas[row][0] = etasOffset[row][_secondToLast];
          _likelihood += w*Math.log(1+Math.exp(tempEtas[_secondToLast]));
        } else {  // perform update for response between 1 to K-2, y can affect class y and y-1
          int lastC = y-1;  // previous class
          yJ = _glmp.linkInv(tempEtas[y]);
          yJm1 = _glmp.linkInv(tempEtas[lastC]);
          double den = yJ-yJm1;
          den = den==0.0?1e-10:den;
          _likelihood -= w*Math.log(den);
          etas[row][0] = yJ+yJm1-1.0; // for non-intercepts
          double oneMcdfPC = 1-yJm1;
          oneMcdfPC = oneMcdfPC==0.0?1e-10:oneMcdfPC;
          double oneOthreshold = 1-Math.exp(_beta[_interceptId][lastC]-_beta[_interceptId][y]);
          oneOthreshold = oneOthreshold==0.0?1e-10:oneOthreshold;
          double oneOverThreshold = 1.0/oneOthreshold;
          etasOffset[row][y] = (yJ-1)*oneOverThreshold/oneMcdfPC;
          yJ = yJ==0?1e-10:yJ;
          etasOffset[row][lastC] = yJm1*oneOverThreshold/yJ;
        }
        for (int c=1; c<K; c++)  // set beta of all classes to be the same
          etas[row][c]=etas[row][0];
      }
    }

    // This method will compute the multipliers for gradient calculation of the betas in etas and for
    // the intercepts in etas_offsets for each row of data
    final void computeGradientMultipliersSQERR(double [][] etas, double [][] etasOffset, double [] ys, double [] ws) {
      int K = _beta[0].length;    // number of class
      double[] tempEtas = new double[K];  // store original etas
      int y;  // get and store response class category
      double yJ, yJm1;
      for (int row = 0; row < etas.length; row++) { // calculate the multiplier from each row
        double w = ws[row];
        if (w==0) {
          Arrays.fill(etas[row], 0);    // zero out etas for current row
          continue;
        }
        // note that, offset is different for all class, beta is shared for all classes
        System.arraycopy(etas[row], 0, tempEtas, 0, K); // copy data over to tempEtas
        Arrays.fill(etas[row], 0);    // zero out etas for current row
        y = (int) ys[row];  // get response class category
        for (int c = 0; c < y; c++) { // classes < yresp, should be negative
          if (tempEtas[c] > 0) {
            etasOffset[row][c] = tempEtas[c];
            etas[row][0] += tempEtas[c];
            _likelihood += w*0.5*tempEtas[c]*tempEtas[c];
          }
        }
        for (int c = y; c < _theLast; c++) {  // class >= yresp, should be positive
          if (tempEtas[c] <= 0) {
            etasOffset[row][c] = tempEtas[c];
            etas[row][0] += tempEtas[c];
            _likelihood += w*0.5*tempEtas[c]*tempEtas[c];
          }
        }
        for (int c=1; c<K; c++)  // set beta of all classes to be the same
          etas[row][c]=etas[row][0];
      }
    }

    final void computeGradientMultipliers(double [][] etas, double [] ys, double [] ws){
      int K = _beta[0].length;
      double [] exps = new double[K+1];
      for(int i = 0; i < etas.length; ++i) {
        double w = ws[i];
        if(w == 0){
          Arrays.fill(etas[i],0);
          continue;
        }
        int y = (int) ys[i];
        double logSumExp = computeMultinomialEtas(etas[i], exps);
        _likelihood -= w * (etas[i][y] - logSumExp);
        for (int c = 0; c < K; ++c)
          etas[i][c] = w * (exps[c + 1] - (y == c ? 1 : 0));
      }
    }

    @Override public void map(Chunk[] chks) {
      if(_job != null && _job.stop_requested()) throw new Job.JobCancelledException();
      int numStart = _dinfo.numStart();
      int K = _beta[0].length;// number of classes
      int P = _beta.length;   // number of predictors (+ intercept)
      int M = chks[0]._len;   // number of rows in this chunk of data
      _gradient = new double[P][K];
      double [][] etas = new double[M][K];  // store multiplier for non-intercept parameters
      double [][] etasOffset = new double[M][K];  // store multiplier for intercept parameters
      double[] offsets = new double[K];
      for(int k = 0; k < K; ++k)
        offsets[k] = _beta[P-1][k]; // intercept
      // sparse offset + intercept
      if(_dinfo._normSub != null) {
        for(int i = 0; i < _dinfo._nums; ++i)
          if(chks[_dinfo._cats + i].isSparseZero())
            ArrayUtils.wadd(offsets,_beta[numStart + i], -_dinfo._normSub[i]*_dinfo._normMul[i]);
      }
      for (int i = 0; i < chks[0]._len; ++i)
        System.arraycopy(offsets, 0, etas[i], 0, K);
      Chunk response = chks[_dinfo.responseChunkId(0)];
      double [] ws = MemoryManager.malloc8d(M);
      if(_dinfo._weights) ws = chks[_dinfo.weightChunkId()].getDoubles(ws,0,M);
      else Arrays.fill(ws,1);
      chks = Arrays.copyOf(chks,chks.length-1-(_dinfo._weights?1:0));
      double [] vals = MemoryManager.malloc8d(M);
      int [] ids = MemoryManager.malloc4(M);
      computeCategoricalEtas(chks,etas,vals,ids);
      computeNumericEtas(chks,etas,vals,ids);

      calMultipliersNGradients(etas, etasOffset, ws, vals, ids, response, chks, M, P, numStart);

    }
    public abstract void calMultipliersNGradients(double[][] etas, double[][] etasOffset, double[] ws, double[] vals,
                                                  int[] ids, Chunk response, Chunk[] chks, int M, int P, int numStart);

    @Override
    public void reduce(GLMMultinomialGradientBaseTask gmgt){
      if(_gradient != gmgt._gradient)
        ArrayUtils.add(_gradient,gmgt._gradient);
      _likelihood += gmgt._likelihood;
    }

    @Override public void postGlobal(){
      ArrayUtils.mult(_gradient, _reg);
      int P = _beta.length;
      // add l2 penalty
      if (_currentLambda > 0) {
        for (int c = 0; c < P - 1; ++c)
          for (int j = 0; j < _beta[0].length; ++j)
            _gradient[c][j] += _currentLambda * _beta[c][j];
      }
    }

    public double [] gradient(){
      double [] res = MemoryManager.malloc8d(_gradient.length*_gradient[0].length);
      int P = _gradient.length;
      for(int k = 0; k < _gradient[0].length; ++k)
        for(int i = 0; i < _gradient.length; ++i)
          res[k*P + i] = _gradient[i][k];
      return res;
    }
  }

  // share between multinomial and ordinal regression
  static class GLMMultinomialGradientTask extends GLMMultinomialGradientBaseTask {
    public GLMMultinomialGradientTask(Job job, DataInfo dinfo, double lambda, double[][] beta, double reg) {
      super(job, dinfo, lambda, beta, reg);
    }

    public GLMMultinomialGradientTask(Job job, DataInfo dinfo, double lambda, double[][] beta, GLMParameters glmp) {
      super(job, dinfo, lambda, beta, glmp);
    }
    @Override
    public void calMultipliersNGradients(double[][] etas, double[][] etasOffset, double[] ws, double[] vals,
                                         int[] ids, Chunk response, Chunk[] chks, int M, int P, int numStart) {
      if (_glmp != null && _link == Link.ologit && (_glmp._solver.equals(GLMParameters.Solver.AUTO) ||
              _glmp._solver.equals((GLMParameters.Solver.GRADIENT_DESCENT_LH))))  // gradient is stored in etas
        computeGradientMultipliersLH(etas, etasOffset, response.getDoubles(vals, 0, M), ws);
      else if (_glmp != null && _link == Link.ologit && _glmp._solver.equals(GLMParameters.Solver.GRADIENT_DESCENT_SQERR))
        computeGradientMultipliersSQERR(etas, etasOffset, response.getDoubles(vals, 0, M), ws);
      else
        computeGradientMultipliers(etas, response.getDoubles(vals, 0, M), ws);

      computeCategoricalGrads(chks, etas, vals, ids);
      computeNumericGrads(chks, etas, vals, ids);

      double [] g = _gradient[P-1]; // get the intercept gradient.
      // sum up the gradient over the data rows in this chk[]
      if (_link == Link.ologit) {
        for (int i = 0; i < etasOffset.length; ++i)
          ArrayUtils.add(g, etasOffset[i]);
      } else {
        for (int i = 0; i < etas.length; ++i)
          ArrayUtils.add(g, etas[i]);
      }
      if(_dinfo._normSub != null) {
        double [] icpt = _gradient[P-1];
        for(int i = 0; i < _dinfo._normSub.length; ++i) {
          if(chks[_dinfo._cats+i].isSparseZero())
            ArrayUtils.wadd(_gradient[numStart+i],icpt,-_dinfo._normSub[i]*_dinfo._normMul[i]);
        }
      }
    }
  }

//  public static class GLMCoordinateDescentTask extends MRTask<GLMCoordinateDescentTask> {
//    final double [] _betaUpdate;
//    final double [] _beta;
//    final double _xOldSub;
//    final double _xOldMul;
//    final double _xNewSub;
//    final double _xNewMul;
//
//    double [] _xy;
//
//    public GLMCoordinateDescentTask(double [] betaUpdate, double [] beta, double xOldSub, double xOldMul, double xNewSub, double xNewMul) {
//      _betaUpdate = betaUpdate;
//      _beta = beta;
//      _xOldSub = xOldSub;
//      _xOldMul = xOldMul;
//      _xNewSub = xNewSub;
//      _xNewMul = xNewMul;
//    }
//
//    public void map(Chunk [] chks) {
//      Chunk xOld = chks[0];
//      Chunk xNew = chks[1];
//      if(xNew.vec().isCategorical()){
//        _xy = MemoryManager.malloc8d(xNew.vec().domain().length);
//      } else
//      _xy = new double[1];
//      Chunk eta = chks[2];
//      Chunk weights = chks[3];
//      Chunk res = chks[4];
//      for(int i = 0; i < eta._len; ++i) {
//        double w = weights.atd(i);
//        double e = eta.atd(i);
//        if(_betaUpdate != null) {
//          if (xOld.vec().isCategorical()) {
//            int cid = (int) xOld.at8(i);
//            e = +_betaUpdate[cid];
//          } else
//            e += _betaUpdate[0] * (xOld.atd(i) - _xOldSub) * _xOldMul;
//          eta.set(i, e);
//        }
//        int cid = 0;
//        double x = w;
//        if(xNew.vec().isCategorical()) {
//          cid = (int) xNew.at8(i);
//          e -= _beta[cid];
//        } else {
//          x = (xNew.atd(i) - _xNewSub) * _xNewMul;
//          e -= _beta[0] * x;
//          x *= w;
//        }
//        _xy[cid] += x * (res.atd(i) - e);
//      }
//    }
//    @Override public void reduce(GLMCoordinateDescentTask t) {
//      ArrayUtils.add(_xy, t._xy);
//    }
//  }


//  /**
//   * Compute initial solution for multinomial problem (Simple weighted LR with all weights = 1/4)
//   */
//  public static final class GLMMultinomialInitTsk extends MRTask<GLMMultinomialInitTsk>  {
//    double [] _mu;
//    DataInfo _dinfo;
//    Gram _gram;
//    double [][] _xy;
//
//    @Override public void map(Chunk [] chks) {
//      Rows rows = _dinfo.rows(chks);
//      _gram = new Gram(_dinfo);
//      _xy = new double[_mu.length][_dinfo.fullN()+1];
//      int numStart = _dinfo.numStart();
//      double [] ds = new double[_mu.length];
//      for(int i = 0; i < ds.length; ++i)
//        ds[i] = 1.0/(_mu[i] * (1-_mu[i]));
//      for(int i = 0; i < rows._nrows; ++i) {
//        Row r = rows.row(i);
//        double y = r.response(0);
//        _gram.addRow(r,.25);
//        for(int c = 0; c < _mu.length; ++c) {
//          double iY = y == c?1:0;
//          double z = (y-_mu[c]) * ds[i];
//          for(int j = 0; j < r.nBins; ++j)
//            _xy[c][r.binIds[j]] += z;
//          for(int j = 0; j < r.nNums; ++j){
//            int id = r.numIds == null?(j + numStart):r.numIds[j];
//            double val = r.numVals[j];
//            _xy[c][id] += z*val;
//          }
//        }
//      }
//    }
//    @Override public void reduce(){
//
//    }
//  }

  /**
   * Task to compute t(X) %*% W %*%  X and t(X) %*% W %*% y
   */
  public static class LSTask extends FrameTask2<LSTask> {
    public double[] _xy;
    public Gram _gram;
    final int numStart;

    public LSTask(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey) {
      super(cmp, dinfo, jobKey);
      numStart = _dinfo.numStart();
    }

    @Override
    public void chunkInit() {
      _gram = new Gram(_dinfo.fullN(), _dinfo.largestCat(), _dinfo.numNums(), _dinfo._cats, true);
      _xy = MemoryManager.malloc8d(_dinfo.fullN() + 1);

    }

    @Override
    protected void processRow(Row r) {
      double wz = r.weight * (r.response(0) - r.offset);
      for (int i = 0; i < r.nBins; ++i) {
        _xy[r.binIds[i]] += wz;
      }
      for (int i = 0; i < r.nNums; ++i) {
        int id = r.numIds == null ? (i + numStart) : r.numIds[i];
        double val = r.numVals[i];
        _xy[id] += wz * val;
      }
      if (_dinfo._intercept)
        _xy[_xy.length - 1] += wz;
      _gram.addRow(r, r.weight);
    }

    @Override
    public void reduce(LSTask lst) {
      ArrayUtils.add(_xy, lst._xy);
      _gram.add(lst._gram);
    }

    @Override
    public void postGlobal() {
      if (_sparse && _dinfo._normSub != null) { // need to adjust gram for missing centering!
        int ns = _dinfo.numStart();
        int interceptIdx = _xy.length - 1;
        double[] interceptRow = _gram._xx[interceptIdx - _gram._diagN];
        double nobs = interceptRow[interceptRow.length - 1]; // weighted _nobs
        for (int i = ns; i < _dinfo.fullN(); ++i) {
          double iMean = _dinfo._normSub[i - ns] * _dinfo._normMul[i - ns];
          for (int j = 0; j < ns; ++j)
            _gram._xx[i - _gram._diagN][j] -= interceptRow[j] * iMean;
          for (int j = ns; j <= i; ++j) {
            double jMean = _dinfo._normSub[j - ns] * _dinfo._normMul[j - ns];
            _gram._xx[i - _gram._diagN][j] -= interceptRow[i] * jMean + interceptRow[j] * iMean - nobs * iMean * jMean;
          }
        }
        if (_dinfo._intercept) { // do the intercept row
          for (int j = ns; j < _dinfo.fullN(); ++j)
            interceptRow[j] -= nobs * _dinfo._normSub[j - ns] * _dinfo._normMul[j - ns];
        }
        // and the xy vec as well
        for (int i = ns; i < _dinfo.fullN(); ++i) {
          _xy[i] -= _xy[_xy.length - 1] * _dinfo._normSub[i - ns] * _dinfo._normMul[i - ns];
        }
      }
    }
  }

  public static class GLMWLSTask extends LSTask {
    final GLMWeightsFun _glmw;
    final double [] _beta;
    double _sparseOffset;

    public GLMWLSTask(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey, GLMWeightsFun glmw, double [] beta) {
      super(cmp, dinfo, jobKey);
      _glmw = glmw;
      _beta = beta;
    }

    private transient GLMWeights _ws;

    @Override public void chunkInit(){
      super.chunkInit();
      _ws = new GLMWeights();
    }

    @Override
    public void processRow(Row r) {
      // update weights
      double eta = r.innerProduct(_beta) + _sparseOffset;
      _glmw.computeWeights(r.response(0),eta,r.weight,r.offset,_ws);
      r.weight = _ws.w;
      r.offset = 0; // handled offset here
      r.setResponse(0,_ws.z);
      super.processRow(r);
    }
  }

  public static class GLMMultinomialWLSTask extends LSTask {
    final GLMWeightsFun _glmw;
    final double [] _beta;
    double _sparseOffset;

    public GLMMultinomialWLSTask(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey, GLMWeightsFun glmw, double [] beta) {
      super(cmp, dinfo, jobKey);
      _glmw = glmw;
      _beta = beta;
    }

    private transient GLMWeights _ws;

    @Override public void chunkInit(){
      super.chunkInit();
      _ws = new GLMWeights();
    }

    @Override
    public void processRow(Row r) {

      // update weights
      double eta = r.innerProduct(_beta) + _sparseOffset;
      _glmw.computeWeights(r.response(0),eta,r.weight,r.offset,_ws);
      r.weight = _ws.w;
      r.offset = 0; // handled offset here
      r.setResponse(0,_ws.z);
      super.processRow(r);
    }
  }


  public static class GLMIterationTaskMultinomial extends FrameTask2<GLMIterationTaskMultinomial> {
    final int _c;
    final double [] _beta; // current beta to compute update of predictors for the current class

    double [] _xy;
    Gram _gram;
    transient double _sparseOffset;

    public GLMIterationTaskMultinomial(DataInfo dinfo, Key jobKey, double [] beta, int c) {
      super(null, dinfo, jobKey);
      _beta = beta;
      _c = c;
    }

    @Override public void chunkInit(){
      // initialize
      _gram = new Gram(_dinfo.fullN(), _dinfo.largestCat(), _dinfo.numNums(), _dinfo._cats,true);
      _xy = MemoryManager.malloc8d(_dinfo.fullN()+1); // + 1 is for intercept
      if(_sparse)
        _sparseOffset = GLM.sparseOffset(_beta,_dinfo);
    }
    @Override
    protected void processRow(Row r) {
      double y = r.response(0);
      double sumExp = r.response(1);
      double maxRow = r.response(2);
      int numStart = _dinfo.numStart();
      y = (y == _c)?1:0;
      double eta = r.innerProduct(_beta) + _sparseOffset;
      if(eta > maxRow) maxRow = eta;
      double etaExp = Math.exp(eta-maxRow);
      sumExp += etaExp;
      double mu = (etaExp == Double.POSITIVE_INFINITY?1:(etaExp / sumExp));
      if(mu < 1e-16)
        mu = 1e-16;//
      double d = mu*(1-mu);
      double wz = r.weight * (eta * d + (y-mu));
      double w  = r.weight * d;
      for(int i = 0; i < r.nBins; ++i) {
        _xy[r.binIds[i]] += wz;
      }
      for(int i = 0; i < r.nNums; ++i){
        int id = r.numIds == null?(i + numStart):r.numIds[i];
        double val = r.numVals[i];
        _xy[id] += wz*val;
      }
      if(_dinfo._intercept)
        _xy[_xy.length-1] += wz;
      _gram.addRow(r, w);
    }

    @Override
    public void reduce(GLMIterationTaskMultinomial glmt) {
      ArrayUtils.add(_xy,glmt._xy);
      _gram.add(glmt._gram);
    }
  }

  public static class GLMMultinomialUpdate extends FrameTask2<GLMMultinomialUpdate> {
    private final double [][] _beta; // updated  value of beta
    private final int _c;
    private transient double [] _sparseOffsets;
    private transient double [] _etas;

    public GLMMultinomialUpdate(DataInfo dinfo, Key jobKey, double [] beta, int c) {
      super(null, dinfo, jobKey);
      _beta = ArrayUtils.convertTo2DMatrix(beta,dinfo.fullN()+1);
      _c = c;
    }

    @Override public void chunkInit(){
      // initialize
      _sparseOffsets = MemoryManager.malloc8d(_beta.length);
      _etas = MemoryManager.malloc8d(_beta.length);
      if(_sparse) {
        for(int i = 0; i < _beta.length; ++i)
          _sparseOffsets[i] = GLM.sparseOffset(_beta[i],_dinfo);
      }
    }

    private transient Chunk _sumExpChunk;
    private transient Chunk _maxRowChunk;

    @Override public void map(Chunk [] chks) {
      _sumExpChunk = chks[chks.length-2];
      _maxRowChunk = chks[chks.length-1];
      super.map(chks);
    }

    @Override
    protected void processRow(Row r) {
      double maxrow = 0;
      for(int i = 0; i < _beta.length; ++i) {
        _etas[i] = r.innerProduct(_beta[i]) + _sparseOffsets[i];
        if(_etas[i] > maxrow)
          maxrow = _etas[i];
      }
      double sumExp = 0;
      for(int i = 0; i < _beta.length; ++i)
//        if(i != _c)
          sumExp += Math.exp(_etas[i]-maxrow);
      _maxRowChunk.set(r.cid,_etas[_c]);
      _sumExpChunk.set(r.cid,Math.exp(_etas[_c]-maxrow)/sumExp);
    }
  }
  
  /**
   * One iteration of glm, computes weighted gram matrix and t(x)*y vector and t(y)*y scalar.
   *
   * @author tomasnykodym
   */
  public static class GLMIterationTask extends FrameTask2<GLMIterationTask> {
    final GLMWeightsFun _glmf;
    double [][]_beta_multinomial;
    double []_beta;
    protected Gram  _gram; // wx%*%x
    double [] _xy; // wx^t%*%z,
    double _yy;

    final double [] _ymu;

    long _nobs;
    public double _likelihood;
    private transient GLMWeights _w;
    private transient GLMWeightsFun _glmfTweedie; // only needed for Tweedie
    //    final double _lambda;
    double wsum, wsumu;
    double _sumsqe;
    int _c = -1;

    public  GLMIterationTask(Key jobKey, DataInfo dinfo, GLMWeightsFun glmw,double [] beta) {
      super(null,dinfo,jobKey);
      _beta = beta;
      _ymu = null;
      _glmf = glmw;
    }

    public  GLMIterationTask(Key jobKey, DataInfo dinfo, GLMWeightsFun glmw, double [] beta, int c) {
      super(null,dinfo,jobKey);
      _beta = beta;
      _ymu = null;
      _glmf = glmw;
      _c = c;
    }

    @Override public boolean handlesSparseData(){return true;}

    transient private double _sparseOffset;

    @Override
    public void chunkInit() {
      // initialize
      _gram = new Gram(_dinfo.fullN(), _dinfo.largestCat(), _dinfo.numNums(), _dinfo._cats,true);
      _xy = MemoryManager.malloc8d(_dinfo.fullN()+1); // + 1 is for intercept
      if(_sparse)
        _sparseOffset = GLM.sparseOffset(_beta,_dinfo);
      _w = new GLMWeights();
      if (_glmf._family.equals(Family.tweedie)) {
        _glmfTweedie = new GLMModel.GLMWeightsFun(_glmf._family, _glmf._link, _glmf._var_power, _glmf._link_power,
                _glmf._theta);
      }
    }
    
    public Gram getGram() {
      return _gram;
    }

    @Override
    protected void processRow(Row r) { // called for every row in the chunk
      if(r.isBad() || r.weight == 0) return;
      ++_nobs;
      double y = r.response(0);
      _yy += y*y;
      final int numStart = _dinfo.numStart();
      double wz,w;
      if(_glmf._family == Family.multinomial) {
        y = (y == _c)?1:0;
        double mu = r.response(1);
        double eta = r.response(2);
        double d = mu*(1-mu);
        if(d == 0) d = 1e-10;
        wz = r.weight * (eta * d + (y-mu));
        w  = r.weight * d;
      } else if(_beta != null) {
        if (_glmf._family.equals(Family.tweedie))
          _glmfTweedie.computeWeights(y, r.innerProduct(_beta) + _sparseOffset, r.offset, r.weight, _w);
        else
          _glmf.computeWeights(y, r.innerProduct(_beta) + _sparseOffset, r.offset, r.weight, _w);
        w = _w.w; // hessian without the xij xik part
        if (_glmf._family.equals(Family.tweedie))  // already multiplied with w for w.z
          wz = _w.z;
        else
          wz = w*_w.z;
        _likelihood += _w.l;
      } else {
        w = r.weight;
        wz = w*(y - r.offset);
      }
      wsum+=w;
      wsumu+=r.weight; // just add the user observation weight for the scaling.
      for(int i = 0; i < r.nBins; ++i)
        _xy[r.binIds[i]] += wz;
      for(int i = 0; i < r.nNums; ++i){
        int id = r.numIds == null?(i + numStart):r.numIds[i];
        double val = r.numVals[i];
        _xy[id] += wz*val;
      }
      if(_dinfo._intercept)
        _xy[_xy.length-1] += wz;
      _gram.addRow(r,w);
    }

    @Override
    public void chunkDone(){adjustForSparseStandardizedZeros();}

    @Override
    public void reduce(GLMIterationTask git){
      ArrayUtils.add(_xy, git._xy);
      _gram.add(git._gram);
      _nobs += git._nobs;
      wsum += git.wsum;
      wsumu += git.wsumu;
      _likelihood += git._likelihood;
      _sumsqe += git._sumsqe;
      _yy += git._yy;
      super.reduce(git);
    }

    private void adjustForSparseStandardizedZeros(){
      if(_sparse && _dinfo._normSub != null) { // need to adjust gram for missing centering!
        int ns = _dinfo.numStart();
        int interceptIdx = _xy.length - 1;
        double[] interceptRow = _gram._xx[interceptIdx - _gram._diagN];
        double nobs = interceptRow[interceptRow.length - 1]; // weighted _nobs
        for (int i = ns; i < _dinfo.fullN(); ++i) {
          double iMean = _dinfo._normSub[i - ns] * _dinfo._normMul[i - ns];
          for (int j = 0; j < ns; ++j)
            _gram._xx[i - _gram._diagN][j] -= interceptRow[j] * iMean;
          for (int j = ns; j <= i; ++j) {
            double jMean = _dinfo._normSub[j - ns] * _dinfo._normMul[j - ns];
            _gram._xx[i - _gram._diagN][j] -= interceptRow[i] * jMean + interceptRow[j] * iMean - nobs * iMean * jMean;
          }
        }
        if (_dinfo._intercept) { // do the intercept row
          for (int j = ns; j < _dinfo.fullN(); ++j)
            interceptRow[j] -= nobs * _dinfo._normSub[j - ns] * _dinfo._normMul[j - ns];
        }
        // and the xy vec as well
        for (int i = ns; i < _dinfo.fullN(); ++i) {
          _xy[i] -= _xy[_xy.length - 1] * _dinfo._normSub[i - ns] * _dinfo._normMul[i - ns];
        }
      }
    }

    public boolean hasNaNsOrInf() {
      return ArrayUtils.hasNaNsOrInfs(_xy) || _gram.hasNaNsOrInfs();
    }
  }
  

 /* public static class GLMCoordinateDescentTask extends FrameTask2<GLMCoordinateDescentTask> {
    final GLMParameters _params;
    final double [] _betaw;
    final double [] _betacd;
    public double [] _temp;
    public double [] _varsum;
    public double _ws=0;
    long _nobs;
    public double _likelihoods;
    public  GLMCoordinateDescentTask(Key jobKey, DataInfo dinfo, double lambda, GLMModel.GLMParameters glm, boolean validate, double [] betaw,
                                     double [] betacd, double ymu, Vec rowFilter, H2OCountedCompleter cmp) {
      super(cmp,dinfo,jobKey,rowFilter);
      _params = glm;
      _betaw = betaw;
      _betacd = betacd;
    }


    @Override public boolean handlesSparseData(){return false;}


    @Override
    public void chunkInit() {
      _temp=MemoryManager.malloc8d(_dinfo.fullN()+1); // using h2o memory manager
      _varsum=MemoryManager.malloc8d(_dinfo.fullN());
    }

    @Override
    protected void processRow(Row r) {
      if(r.bad || r.weight == 0) return;
      ++_nobs;
      final double y = r.response(0);
      assert ((_params._family != Family.gamma) || y > 0) : "illegal response column, y must be > 0  for family=Gamma.";
      assert ((_params._family != Family.binomial) || (0 <= y && y <= 1)) : "illegal response column, y must be <0,1>  for family=Binomial. got " + y;
      final double w, eta, mu, var, z;
      final int numStart = _dinfo.numStart();
      double d = 1;
      if( _params._family == Family.gaussian && _params._link == Link.identity){
        w = r.weight;
        z = y - r.offset;
        mu = 0;
        eta = mu;
      } else {
        eta = r.innerProduct(_betaw);
        mu = _params.linkInv(eta + r.offset);
        var = Math.max(1e-6, _params.variance(mu)); // avoid numerical problems with 0 variance
        d = _params.linkDeriv(mu);
        z = eta + (y-mu)*d;
        w = r.weight/(var*d*d);
      }
      _likelihoods += r.weight*_params.likelihood(y,mu);
      assert w >= 0|| Double.isNaN(w) : "invalid weight " + w; // allow NaNs - can occur if line-search is needed!

      _ws+=w;
      double xb = r.innerProduct(_betacd);
      for(int i = 0; i < r.nBins; ++i)  { // go over cat variables
        _temp[r.binIds[i]] += (z - xb + _betacd[r.binIds[i]])  *w;
        _varsum[r.binIds[i]] += w ;
      }
      for(int i = 0; i < r.nNums; ++i){ // num vars
        int id = r.numIds == null?(i + numStart):r.numIds[i];
        _temp[id] += (z- xb + r.get(id)*_betacd[id] )*(r.get(id)*w);
        _varsum[id] += w*r.get(id)*r.get(id);
      }
        _temp[_temp.length-1] += w*(z-r.innerProduct(_betacd)+_betacd[_betacd.length-1]);
    }

    @Override
    public void reduce(GLMCoordinateDescentTask git){ // adding contribution of all the chunks
      ArrayUtils.add(_temp, git._temp);
      ArrayUtils.add(_varsum, git._varsum);
      _ws+= git._ws;
      _nobs += git._nobs;
      _likelihoods += git._likelihoods;
      super.reduce(git);
    }

  }
*/

  public static class RandColAddW2AugXZ extends MRTask<RandColAddW2AugXZ> {
    public int[] _cumRandCatLevels; // cumulative sum of random column categorical levels
    public int _randNumColStart;
    public long _randRowStart;  // index into absolute row number start in Frame _AugXZ
    Job _job;
    Frame _prior_weights_psi; // first column is prior weight, second column is wpsi, third is zmi
    public int _totAugxzColNumber;
    public int[] _weightID;

    public RandColAddW2AugXZ(Job job, int[] randCatLevels, Frame prior_weights_psi, int wpsiID, long randRowStart, 
                             int randNumColStart, int augXZColNum) {
      _job = job;
      _prior_weights_psi = prior_weights_psi;
      _weightID = new int[]{wpsiID};
      _cumRandCatLevels = ArrayUtils.cumsum(randCatLevels);
      _randRowStart = randRowStart;
      _randNumColStart = randNumColStart;
      _totAugxzColNumber = augXZColNum;
    }

    /***
     * Given colIndex to the expanded random columns, this method will calculate which random column that colIndex
     * belongs to.
     * @param cumrandCatLevels
     * @param colIndex
     * @return
     */
    public static int findRandColIndex(int[] cumrandCatLevels, long colIndex) {
      int len = cumrandCatLevels.length;
      for (int index = 0; index < len; index++) {
        if (colIndex < cumrandCatLevels[index])
          return index;
      }
      return (len-1);
    }

    @Override
    public void map(Chunk[] chunks) {       // chunks will be AugXZ
      long chkStartIdx = chunks[0].start(); // absolute row number of AugXZ chunk
      // Note here, we are working on the lower rows of augXZ related to 0 | Iq.
      if ((chkStartIdx+chunks[0].len()) >= _randRowStart) { // only start working if we are looking at correct chunk
        // need to figure out which chunk of priorWeightsWpsi to take and where the row start should be as well
        Chunk[] priorWeightsWpsi = new Chunk[1];
        int chkRowStart = (int) (_randRowStart-chkStartIdx); // relative start in AugXZ
        chkRowStart  = chkRowStart  > 0?chkRowStart:0;  // whole chunk of AugXZ used to calculate lower part of AugXZ
        long chkWeightRowStart = chkRowStart+chkStartIdx-_randRowStart; // first row of absolute row index of weight
        int[] weightChunkInfo = getCorrectChunk(_prior_weights_psi, 0, chkWeightRowStart, priorWeightsWpsi,
                _weightID, null);  // start from 0 to total randColExpanded
        int chkWeightRelRow = weightChunkInfo[2];
        int psiColumnIndex = (int)priorWeightsWpsi[0].start()+_randNumColStart+chkWeightRelRow;// start of column for random Columns
        for (int index = chkRowStart; index < chunks[0]._len; index++) {  // go throw each row of AugXZ
          if (chkWeightRelRow >= weightChunkInfo[1]) {  // need to grab a new weight chunk
            weightChunkInfo = getCorrectChunk(_prior_weights_psi, weightChunkInfo[0]+1,
                    chkWeightRelRow+priorWeightsWpsi[0].start(), priorWeightsWpsi, _weightID, weightChunkInfo);
            chkWeightRelRow = weightChunkInfo[2];
          }
          double wpsi = priorWeightsWpsi[0].atd(chkWeightRelRow);
          for (int colIndex=0; colIndex < psiColumnIndex; colIndex++) {
            chunks[colIndex].set(index, 0.0); // zero out columns to left of psiColumnIndex
          }
          chunks[psiColumnIndex].set(index, wpsi);        // update weight to AugXZ
          psiColumnIndex++;
          for (int colIndex=psiColumnIndex; colIndex < _totAugxzColNumber; colIndex++)
            chunks[colIndex].set(index, 0.0); // zero out columns to right of psiColumnIndex
          chkWeightRelRow++;
        }
      }
    }
  }

  /***
   *
   * This class calculates wpsi, zmi for frame _prior_weights_psi.  
   */
  public static class CalculateW4Rand extends MRTask<CalculateW4Rand> {
    GLMParameters _parms;
    public int[] _cumRandCatLevels; // cumulative sum of random column categorical levels
    public double[] _psi;
    public double[] _phi;
    public int _numRandCol; // number of random columns specified by user
    Job _job;
    double[] _vi; // store random column coefficients

    public CalculateW4Rand(Job job, GLMParameters params, int[] randCatLevels, double[] psi, double[] phi, 
                           double[] vi) {
      _job = job;
      _parms = params;
      _numRandCol = _parms._random_columns.length;  // number of random columns specified by user
      _cumRandCatLevels = ArrayUtils.cumsum(randCatLevels);
      _psi = psi;
      _phi = phi;
      _vi = vi;
    }

    /***
     * Given colIndex to the expanded random columns, this method will calculate which random column that colIndex
     * belongs to.
     * @param cumrandCatLevels
     * @param colIndex
     * @return
     */
    public static int findRandColIndex(int[] cumrandCatLevels, long colIndex) {
      int len = cumrandCatLevels.length;
      for (int index = 0; index < len; index++) {
        if (colIndex < cumrandCatLevels[index])
          return index;
      }
      return (len-1);
    }

    @Override
    public void map(Chunk[] chunks) {       // chunks will be wpsi_frame
        GLMWeightsFun[] glmfunRand = ReturnGLMMMERunInfo.getRandGLMFuns(null, _numRandCol, _parms);
        double temp, ui, zmi, wpsi;
        for (int index = 0; index < chunks[0]._len; index++) {  // go throw each row of AugXZ
          int randIndex = findRandColIndex(_cumRandCatLevels, index);
          temp = glmfunRand[randIndex].linkInvDeriv(_phi[index]); // du_dv
          ui = glmfunRand[randIndex].linkInv(_vi[index]);
          zmi = _vi[index]+(_psi[index]-ui)/temp;
          chunks[2].set(index, zmi);
          wpsi = chunks[0].atd(index) * temp * temp / (glmfunRand[randIndex].variance(_psi[index]) * _phi[index]);
          chunks[1].set(index, Math.sqrt(wpsi));  // update weight frame with new weight
        }
    }
    
  }
  
  public static class GenerateResid extends MRTask<GenerateResid> {
    public Job _job;
    double _oneOverSqrtSumDevONMP;
    int _hvColIdx;
    int _residColIdx;
    long _numDataRows;
    
    public GenerateResid(Job job, double oneOverSqrtSumDevONMP, int hvColIdx, int residColIdx, long numDataRows) {
      _job = job;
      _oneOverSqrtSumDevONMP = oneOverSqrtSumDevONMP;
      _hvColIdx = hvColIdx;
      _residColIdx = residColIdx;
      _numDataRows = numDataRows;
    }

    @Override
    public void map(Chunk[] chunks) { // chunk contains infos from GLMMME run
      long chkStartRowIdx = chunks[0].start();
      int chkRowNumber = chunks[0].len();
      for (int rowIndex=0; rowIndex<chkRowNumber; rowIndex++) {
        long absRowIdx = rowIndex+chkStartRowIdx;
        if (absRowIdx < _numDataRows) { // only need to generate resid for data rows
          double tempVal = chunks[_residColIdx].atd(rowIndex)*_oneOverSqrtSumDevONMP;
          chunks[_residColIdx].set(rowIndex, tempVal/Math.sqrt(1-chunks[_hvColIdx].atd(rowIndex)));
        } else
          break;
      }
    }
  }
  
  public static class CalculateEtaInfo extends MRTask<CalculateEtaInfo> {
    public double _sumEtaDiffSq;
    public double _sumEtaSq;
    public int[] _etaOetaN;
    
    public CalculateEtaInfo(int[] etaOldetaNew) {
      _sumEtaDiffSq = 0;
      _sumEtaSq = 0;
      _etaOetaN = etaOldetaNew;
    }

    @Override
    public void map(Chunk[] chunks) {
      _sumEtaDiffSq = 0;
      _sumEtaSq = 0;
      int chkLen = chunks[0].len();
      for (int rowIndex=0; rowIndex < chkLen; rowIndex++) {
        double tempetaN = chunks[_etaOetaN[1]].atd(rowIndex); // grab etaNew value
        double tempetaDiff = chunks[_etaOetaN[0]].atd(rowIndex)-tempetaN;
        _sumEtaSq += tempetaN*tempetaN;
        _sumEtaDiffSq += tempetaDiff*tempetaDiff;
      }
    }

    @Override
    public void reduce(CalculateEtaInfo other){
      this._sumEtaDiffSq += other._sumEtaDiffSq;
      this._sumEtaSq += other._sumEtaSq;
    }
  }
  
  
  public static class ExtractFrameFromSourceWithProcess extends MRTask<ExtractFrameFromSourceWithProcess> {
    public Frame _sourceFrame;
    int[] _devhvColIdx;
    long _startRowIndex;  // matches 0 row of dest chunk
    long _lengthToCopy;
    
    public ExtractFrameFromSourceWithProcess(Frame sourceFrame, int[] devHvColIdx, long startRowIndex, long lengthCopy) {
      _sourceFrame = sourceFrame;
      _devhvColIdx = devHvColIdx;
      _startRowIndex = startRowIndex;
      _lengthToCopy = lengthCopy;
    }

    @Override
    public void map(Chunk[] chunks) {
      long startChkIdx = chunks[0].start(); // absolute row index of chunks to copy to.
      int chkLen = chunks[0].len();
      long sourceChkIdx = _startRowIndex+startChkIdx; // absolute source chunk row index
      Chunk[] sourceChunks = new Chunk[_devhvColIdx.length];
      int[] fetchedChunkInfo = getCorrectChunk(_sourceFrame, 0, sourceChkIdx, sourceChunks, _devhvColIdx, 
              null);
      int fetchedRelRowIndex = fetchedChunkInfo[2];
      for (int rowIndex=0; rowIndex < chkLen; rowIndex++) {
        if (rowIndex+startChkIdx >= _lengthToCopy)
          break;
        if (fetchedRelRowIndex >= fetchedChunkInfo[1]) {
          fetchedChunkInfo = getCorrectChunk(_sourceFrame, fetchedChunkInfo[0]+1, 
                  fetchedRelRowIndex+sourceChunks[0].start(), sourceChunks, _devhvColIdx, fetchedChunkInfo);
          fetchedRelRowIndex = fetchedChunkInfo[2];
        }
        double temp = 1.0-sourceChunks[1].atd(fetchedRelRowIndex);
        chunks[0].set(rowIndex, sourceChunks[0].atd(fetchedRelRowIndex)/temp);  // set response
        chunks[2].set(rowIndex, temp/2);  // set weight
        fetchedRelRowIndex++;
      }
    }
  }

  /***
   * This class will copy columns from a source frame to columns in the destination frame
   * 
   */
  public static class CopyPartsOfFrame extends MRTask<CopyPartsOfFrame> {
    public Frame _sourceFrame;
    public int[] _destColIndices;
    public int[] _sourceColIndices;
    public long _nrowsToCopy;

    public CopyPartsOfFrame(Frame fr, int[] destFrameColID, int[] sourceFrameColID, long numRows) {
      _sourceFrame = fr;
      if (sourceFrameColID==null) {
        int numCols = fr.numCols();
        _sourceColIndices = new int[numCols];
        for (int index=0; index < numCols; index++)
          _sourceColIndices[index] = index;
      } else
        _sourceColIndices = sourceFrameColID;
      
      if (destFrameColID == null) {
        int numCols = _sourceColIndices.length;
        _destColIndices = new int[numCols];
        for (int index=0; index < numCols; index++)
          _destColIndices[index]=index;
      } else
        _destColIndices = destFrameColID;
      
      assert _destColIndices.length==_sourceColIndices.length;
      _nrowsToCopy = numRows;
    }

    @Override
    public void map(Chunk[] chunks) { // chunk contains infos from GLMMME run
      int colLen = _sourceColIndices.length;
      long chkStartIdx = chunks[0].start(); // first row of destination frame
      Chunk[] sourceChunks = new Chunk[colLen]; // just fetch the needed columns from the source
      long lastRowIndex = chkStartIdx + chunks[0].len();
      if (chkStartIdx < _nrowsToCopy) { // only copy chunk when there are enough source chunks
        int rowLen = lastRowIndex > _nrowsToCopy ? ((int) (_nrowsToCopy - chkStartIdx)) : chunks[0].len();
        int[] fetchedChkInfo = getCorrectChunk(_sourceFrame, 0, chkStartIdx, sourceChunks, _sourceColIndices, null);
        int fetchedChkRelRow = fetchedChkInfo[2];
        for (int rowIndex = 0; rowIndex < rowLen; rowIndex++) {
          if (fetchedChkRelRow >= fetchedChkInfo[1]) {  // need new chunk
            fetchedChkInfo = getCorrectChunk(_sourceFrame, fetchedChkInfo[0] + 1,
                    fetchedChkRelRow + sourceChunks[0].start(), sourceChunks, _sourceColIndices, fetchedChkInfo);
            fetchedChkRelRow = fetchedChkInfo[2];
          }
          for (int colIndex = 0; colIndex < colLen; colIndex++) {
            chunks[_destColIndices[colIndex]].set(rowIndex, sourceChunks[colIndex].atd(fetchedChkRelRow));
          }
          fetchedChkRelRow++;
        }
      }
    }
  }

  public static class ReturnGLMMMERunInfo extends MRTask<ReturnGLMMMERunInfo> {
    public DataInfo _dinfo;
    public Frame _w_prior_wpsi;
    public Frame _qMatrix;
    Job _job;
    double _sumDev;
    double _sumEtaDiffSq;
    double _sumEtaSq;
    public int _totalaugXZCol;
    public int[] _dinfoWCol;  // columns to load from dinfo to calculate z and dev
    public int[] _wpriorwpsiCol;  // columns to load from _w_prior_wpsi to calculate z and dev
    public long _numDataRow;
    public int _maxdinfoCol;  // number of columns to load from dinfo._adaptedFrame
    GLMParameters _parms;
    public double[] _psi;
    public double[] _ubeta;
    public int[] _cumRandCatLevels; // cumulative sum of random column categorical levels
    public int _numRandCol;

    public ReturnGLMMMERunInfo(Job job, DataInfo datainfo, Frame wpriorwpsi, Frame qMatrix, int[] dinfoWCol, int[] wCol,
                               GLMParameters params, double[] psi, double[] ubeta, int[] cumRandCatLevels) {
      _job = job;
      _dinfo = datainfo;
      _w_prior_wpsi = wpriorwpsi;
      _qMatrix= qMatrix;
      _sumDev = 0;
      _sumEtaDiffSq = 0;
      _sumEtaSq = 0;
      _totalaugXZCol = qMatrix.numCols();
      _dinfoWCol = dinfoWCol;
      _wpriorwpsiCol = wCol;
      _numDataRow = _dinfo._adaptedFrame.numRows();
      _maxdinfoCol = _dinfo._weights?4:3;
      _parms = params;
      _psi = psi;
      _ubeta = ubeta;
      _cumRandCatLevels = cumRandCatLevels;
      _numRandCol = cumRandCatLevels.length;
    }

    public static GLMWeightsFun[] getRandGLMFuns(GLMWeightsFun[] randGLMs, int numRandFuncs, GLMParameters params) {
      if (randGLMs == null)
        randGLMs = new GLMWeightsFun[numRandFuncs];
      for (int index=0; index < numRandFuncs; index++) {
        Link randlink;
        if (params._rand_link==null)
          randlink = params._rand_family[index].defaultLink;
        else
          randlink = params._rand_link[index];
        randGLMs[index] = new GLMWeightsFun(params._rand_family[index], randlink,
                params._tweedie_variance_power, params._tweedie_link_power, 0);
      }
      return randGLMs;
    }

    @Override
    public void reduce(ReturnGLMMMERunInfo other){
      this._sumEtaDiffSq += other._sumEtaDiffSq;
      this._sumEtaSq += other._sumEtaSq;
    }

    @Override
    public void map(Chunk[] chunks) { // chunk contains infos from GLMMME run
      GLMWeightsFun glmfun=null;
      GLMWeightsFun[] glmfunRand = null;
      long chkStartRowIdx = chunks[0].start(); // first row number of chunk
      int chkRowNumber = chunks[0].len();
      Chunk[] chunksqMatrix = new Chunk[_totalaugXZCol]; // fetch chunk from AugXZ in order to calculate hv, Augz
      int[] qMatrixInfo = getCorrectChunk(_qMatrix, 0, chkStartRowIdx, chunksqMatrix, null, null);
      Chunk[] chunks4ZDev = new Chunk[4]; // potentially load response, zi, etai, weightID
      int[] zdevChunkInfo = new int[3];
      boolean usingWpsi;
      long rowOffset = chkStartRowIdx-_numDataRow;
      if (chkStartRowIdx >= _numDataRow) {  // load _w_prior_wpsi chunks, get wprior, zmi
        usingWpsi = true;
        zdevChunkInfo = getCorrectChunk(_w_prior_wpsi, 0, rowOffset, chunks4ZDev,
                _wpriorwpsiCol, zdevChunkInfo);
        glmfunRand = getRandGLMFuns(glmfunRand, _numRandCol, _parms);
      } else {  // load from dinfo: response, zi, etai and maybe weightID for prior_weight
        usingWpsi = false;
        glmfun = new GLMWeightsFun(_parms._family, _parms._link, _parms._tweedie_variance_power,
                _parms._tweedie_link_power, 0);
        zdevChunkInfo = getCorrectChunk(_dinfo._adaptedFrame, 0, chkStartRowIdx, chunks4ZDev, _dinfoWCol,
                zdevChunkInfo);
      }
      for (int rowIndex=0; rowIndex < chkRowNumber; rowIndex++) { // correct chunks are loaded for now
        int zdevAbsRelRowNumber = usingWpsi?(int)(rowIndex+rowOffset):rowIndex+zdevChunkInfo[2];  // offset into zdevChunks
        if (!usingWpsi && (zdevAbsRelRowNumber >= zdevChunkInfo[1])) {  // running out of rows with dinfo
          long rowAbsIndex = rowIndex+chkStartRowIdx;
          if (rowAbsIndex>=_numDataRow) { // load from wprior_wpsi
            usingWpsi=true;
            zdevChunkInfo = getCorrectChunk(_w_prior_wpsi, 0, rowAbsIndex-_numDataRow,
                    chunks4ZDev, _wpriorwpsiCol, zdevChunkInfo);
            if (glmfunRand==null) { // generate glmfunRand[] for the first time only
              glmfunRand = getRandGLMFuns(glmfunRand, _numRandCol, _parms);
            }
          } else {  // still load from dinfo
            zdevChunkInfo = getCorrectChunk(_dinfo._adaptedFrame, 0,
                    rowAbsIndex, chunks4ZDev, _dinfoWCol, zdevChunkInfo);
            if (glmfun==null)
              glmfun = new GLMWeightsFun(_parms._family, _parms._link, _parms._tweedie_variance_power,
                      _parms._tweedie_link_power, 0);
          }
          zdevAbsRelRowNumber = usingWpsi?(int)(rowIndex+rowOffset):rowIndex+zdevChunkInfo[2];
        }  else if (usingWpsi && (zdevAbsRelRowNumber-zdevChunkInfo[0]) >= zdevChunkInfo[1]) {  // load from wprior_wpsi
          zdevChunkInfo = getCorrectChunk(_w_prior_wpsi, 0, zdevAbsRelRowNumber, chunks4ZDev, _wpriorwpsiCol,
                  zdevChunkInfo);
          if (glmfunRand==null) { // generate glmfunRand[] for the first time only
            glmfunRand = getRandGLMFuns(glmfunRand, _numRandCol, _parms);
          }
          zdevAbsRelRowNumber = (int)(rowIndex+rowOffset);
        }
        _sumDev += calDev(usingWpsi, _cumRandCatLevels, zdevAbsRelRowNumber, chunks4ZDev, chunks, rowIndex, glmfun, glmfunRand, _psi, _ubeta);
        setHv(chunksqMatrix, chunks[1], rowIndex, qMatrixInfo[2]++);  // get hv from augXZ only
        if (qMatrixInfo[2] > qMatrixInfo[1]) {  // need to load in new chunk
          qMatrixInfo = getCorrectChunk(_qMatrix, 1+qMatrixInfo[0], chkStartRowIdx, chunksqMatrix, null, qMatrixInfo);
        }
      }
    }

    public static void setHv(Chunk[] qmat, Chunk hv, int relRowIndex, int qmatRelRowIndex) {
      int numCol = qmat.length;
      double rowSum = 0;
      for (int colIndex=0; colIndex < numCol; colIndex++) {
        double temp = qmat[colIndex].atd(qmatRelRowIndex);
        rowSum += temp*temp;
      }
      hv.set(relRowIndex, rowSum > ONEEQUAL?ONEEQUAL:rowSum);
    }

    public  double calDev(boolean usingWpsi, int[] _cumRandCatLevels, int zdevAbsRelRowNumber, Chunk[] chunks4ZDev,
                          Chunk[] chunks, int rowIndex, GLMWeightsFun glmfun,
                          GLMWeightsFun[] glmfunRand, double[] psi, double[] ubeta) {
      if (usingWpsi) {
        int randIndex = RandColAddW2AugXZ.findRandColIndex(_cumRandCatLevels, zdevAbsRelRowNumber);
        return setZDevEta(chunks4ZDev, chunks, rowIndex, (int) (zdevAbsRelRowNumber-chunks[0].start()),
                (int) zdevAbsRelRowNumber, glmfunRand[randIndex], psi, ubeta);
      } else {          // get z, dev, eta from dinfo
        return setZDevEta(chunks4ZDev, chunks, rowIndex, zdevAbsRelRowNumber, glmfun);
      }
    }

    public static double setZDevEta(Chunk[] wpsiChunks, Chunk[] destChunk, int relRowIndex, int wpsiRowIndex,
                                    int abswpsiRowIndex, GLMWeightsFun glmfuns, double[] psi, double[] ubeta) {
      destChunk[0].set(relRowIndex, wpsiChunks[1].atd(wpsiRowIndex)); // set Z value
      double temp = psi[abswpsiRowIndex]-ubeta[abswpsiRowIndex];
      double devVal=wpsiChunks[0].atd(wpsiRowIndex)*temp*temp;
      destChunk[2].set(relRowIndex, devVal < ZEROEQUAL?ZEROEQUAL:devVal);
      return devVal;
    }

    public  double setZDevEta(Chunk[] dinfoChunks, Chunk[] destChunk, int relRowIndex, int dinfoRowIndex,
                              GLMWeightsFun glmfun) {
      destChunk[0].set(relRowIndex, dinfoChunks[1].atd(dinfoRowIndex)); // set AugZ value
      double eta = dinfoChunks[2].atd(dinfoRowIndex);
      destChunk[3].set(relRowIndex, eta); // set new eta value
      double temp2 = eta-destChunk[5].atd(relRowIndex);
      _sumEtaDiffSq += temp2*temp2;
      _sumEtaSq += eta*eta;
      double temp = dinfoChunks[0].atd(dinfoRowIndex)-glmfun.linkInv(eta);
      destChunk[4].set(relRowIndex, temp);  // set resid = (y-mu.i)
      double prior_weight = dinfoChunks[3]==null?1:dinfoChunks[3].atd(dinfoRowIndex);
      double devVal = prior_weight*temp*temp;
      destChunk[2].set(relRowIndex, devVal < ZEROEQUAL?ZEROEQUAL:devVal);
      return devVal;
    }
  }
  
  public static class ReturnGLMMMERunInfoRandCols extends MRTask<ReturnGLMMMERunInfoRandCols> {
    public DataInfo _dinfo;
    public Frame _w_prior_wpsi;
    public Frame _qMatrix;
    Job _job;
    double _sumDev;
    public int _totalqMatrixCols;
    public int[] _wpriorwpsiCol;  // columns to load from _w_prior_wpsi to calculate z and dev
    public long _numDataRow;
    GLMParameters _parms;
    public double[] _psi;
    public double[] _ubeta;
    public int[] _cumRandCatLevels; // cumulative sum of random column categorical levels
    public int _numRandCol;

    public ReturnGLMMMERunInfoRandCols(Job job, DataInfo datainfo, Frame wpriorwpsi, Frame qmatrix, int[] wCol,
                               GLMParameters params, double[] psi, double[] ubeta, int[] cumRandCatLevels) {
      _job = job;
      _w_prior_wpsi = wpriorwpsi;
      _qMatrix = qmatrix;
      _sumDev = 0;
      _totalqMatrixCols = qmatrix.numCols();
      _wpriorwpsiCol = wCol;
      _numDataRow = datainfo._adaptedFrame.numRows();
      _parms = params;
      _psi = psi;
      _ubeta = ubeta;
      _cumRandCatLevels = cumRandCatLevels;
      _numRandCol = cumRandCatLevels.length;
    }
    
    @Override
    public void map(Chunk[] chunks) { // chunk contains infos from GLMMME run
      GLMWeightsFun[] glmfunRand = null;
      long chkStartRowIdx = chunks[0].start(); // first row number of chunk
      long maxChkRowIdx = chunks[0].start() + chunks[0].len();
      if (chkStartRowIdx >= _numDataRow || _numDataRow < maxChkRowIdx) { // only update random column part
        int chkRowNumber = chunks[0].len();
        chkStartRowIdx = chkStartRowIdx >= _numDataRow?chkStartRowIdx:_numDataRow;  // absolute row start index
        Chunk[] chunksqMatrix = new Chunk[_totalqMatrixCols]; // fetch chunk from qMatrix in order to calculate hv, Augz
        int[] qMatrixInfo = getCorrectChunk(_qMatrix, 0, chkStartRowIdx, chunksqMatrix, null, null);
        Chunk[] chunks4ZDev = new Chunk[4]; // potentially load response, zi, etai, weightID
        int[] zdevChunkInfo = new int[3];

        long rowOffset = chkStartRowIdx - _numDataRow;
        zdevChunkInfo = getCorrectChunk(_w_prior_wpsi, 0, rowOffset, chunks4ZDev,
                  _wpriorwpsiCol, zdevChunkInfo);
        int zdevRelRowNumber = zdevChunkInfo[2];
        int qMatrixRelRow = qMatrixInfo[2];
        glmfunRand = getRandGLMFuns(glmfunRand, _numRandCol, _parms);
        int chunkRowStart = (int)(chkStartRowIdx-chunks[0].start());
        for (int rowIndex = chunkRowStart; rowIndex < chkRowNumber; rowIndex++) { // correct chunks are loaded for now
          if (zdevRelRowNumber >= zdevChunkInfo[1]) {
            zdevChunkInfo = getCorrectChunk(_w_prior_wpsi, zdevChunkInfo[0]+1, 
                    zdevRelRowNumber+chunks4ZDev[0].start(), chunks4ZDev, _wpriorwpsiCol,
                    zdevChunkInfo);
            zdevRelRowNumber = zdevChunkInfo[2];
            if (glmfunRand == null) { // generate glmfunRand[] for the first time only
              glmfunRand = getRandGLMFuns(glmfunRand, _numRandCol, _parms);
            }
          }
          if (qMatrixRelRow >= qMatrixInfo[1]) {
            qMatrixInfo = getCorrectChunk(_qMatrix, qMatrixInfo[0]+1, qMatrixRelRow+chunksqMatrix[0].start(),
                    chunksqMatrix, null, qMatrixInfo);
            qMatrixRelRow=qMatrixInfo[2];
          }
          int randIndex = RandColAddW2AugXZ.findRandColIndex(_cumRandCatLevels, zdevRelRowNumber);
          _sumDev += setZDevEta(chunks4ZDev, chunks, rowIndex, zdevRelRowNumber, 
                  (int) (zdevRelRowNumber+chunks4ZDev[0].start()), _psi, _ubeta);
          setHv(chunksqMatrix, chunks[1], rowIndex, qMatrixRelRow);  // get hv from augXZ only
          qMatrixRelRow++;
          zdevRelRowNumber++;
        }
      }
    }


    public static GLMWeightsFun[] getRandGLMFuns(GLMWeightsFun[] randGLMs, int numRandFuncs, GLMParameters params) {
      if (randGLMs == null)
        randGLMs = new GLMWeightsFun[numRandFuncs];
      for (int index=0; index < numRandFuncs; index++) {
        Link randlink;
        if (params._rand_link==null)
          randlink = params._rand_family[index].defaultLink;
        else
          randlink = params._rand_link[index];
        randGLMs[index] = new GLMWeightsFun(params._rand_family[index], randlink,
                params._tweedie_variance_power, params._tweedie_link_power, 0);
      }
      return randGLMs;
    }
    
    @Override
    public void reduce(ReturnGLMMMERunInfoRandCols other){
      this._sumDev += other._sumDev;
    }


    public static void setHv(Chunk[] qmat, Chunk hv, int relRowIndex, int qmatRelRowIndex) {
      int numCol = qmat.length;
      double rowSum = 0;
      for (int colIndex=0; colIndex < numCol; colIndex++) {
        double temp = qmat[colIndex].atd(qmatRelRowIndex);
        rowSum += temp*temp;
      }
      hv.set(relRowIndex, rowSum > ONEEQUAL?ONEEQUAL:rowSum);
    }
    
    public static double setZDevEta(Chunk[] wpsiChunks, Chunk[] destChunk, int relRowIndex, int wpsiRowIndex, 
                                    int abswpsiRowIndex,  double[] psi, double[] ubeta) {
      destChunk[0].set(relRowIndex, wpsiChunks[1].atd(wpsiRowIndex)); // set Z value
      double temp = psi[abswpsiRowIndex]-ubeta[abswpsiRowIndex];
      double devVal=wpsiChunks[0].atd(wpsiRowIndex)*temp*temp;
      destChunk[2].set(relRowIndex, devVal < ZEROEQUAL?ZEROEQUAL:devVal);
      return devVal;
    }
  }

  /***
   * fill in the returnFrame from the data portion only
   */
  public static class ReturnGLMMMERunInfoData extends MRTask<ReturnGLMMMERunInfoData> {
    public DataInfo _dinfo;
    public Frame _qMatrix;
    Job _job;
    double _sumDev;
    double _sumEtaDiffSq;
    double _sumEtaSq;
    public int _totalaugXZCol;
    public int[] _dinfoWCol;  // columns to load from dinfo to calculate z and dev
    public long _numDataRow;
    GLMParameters _parms;

    public ReturnGLMMMERunInfoData(Job job, DataInfo datainfo, Frame qMatrix, int[] dinfoWCol,
                               GLMParameters params) {
      _job = job;
      _dinfo = datainfo;
      _qMatrix = qMatrix;
      _sumDev = 0;
      _sumEtaDiffSq = 0;
      _sumEtaSq = 0;
      _totalaugXZCol = qMatrix.numCols();
      _dinfoWCol = dinfoWCol;
      _numDataRow = _dinfo._adaptedFrame.numRows();
      _parms = params;
    }
    
    @Override
    public void map(Chunk[] chunks) { // chunk contains infos from GLMMME run
      long chkStartRowIdx = chunks[0].start(); // first row number of chunk
      if (chkStartRowIdx < _numDataRow) { // only look at chunks corresponding to the data rows
        GLMWeightsFun glmfun = null;

        long maxRowIndex = chkStartRowIdx+chunks[0].len();
        int chkRowNumber = maxRowIndex>=_numDataRow?chunks[0].len()-(int)(maxRowIndex-_numDataRow):chunks[0].len(); // number of row to consider
        Chunk[] chunksqMatrix = new Chunk[_totalaugXZCol]; // fetch chunk from qMatrix in order to calculate hv, Augz
        int[] qMatrixInfo = getCorrectChunk(_qMatrix, 0, chkStartRowIdx, chunksqMatrix, null, null);
        int qMatrixRelRow = qMatrixInfo[2];
        Chunk[] chunks4ZDev = new Chunk[4]; // potentially load response, zi, etai, weightID
        int[] zdevChunkInfo = new int[3];

        glmfun = new GLMWeightsFun(_parms._family, _parms._link, _parms._tweedie_variance_power,
                _parms._tweedie_link_power, 0);
        zdevChunkInfo = getCorrectChunk(_dinfo._adaptedFrame, 0, chkStartRowIdx, chunks4ZDev, _dinfoWCol,
                zdevChunkInfo);
        int zdevAbsRelRowNumber = zdevChunkInfo[2];
        for (int rowIndex = 0; rowIndex < chkRowNumber; rowIndex++) { // correct chunks are loaded for now
          if (zdevAbsRelRowNumber >= zdevChunkInfo[1]) { // exceeds chunk limit, grab next one
            zdevChunkInfo = getCorrectChunk(_dinfo._adaptedFrame, zdevChunkInfo[0] + 1,
                    zdevAbsRelRowNumber + chunks4ZDev[0].start(), chunks4ZDev, _dinfoWCol, zdevChunkInfo);
            zdevAbsRelRowNumber = zdevChunkInfo[2];
          }
          if (qMatrixRelRow  > qMatrixInfo[1]) {  // need to load in new chunk
            qMatrixInfo = getCorrectChunk(_qMatrix, 1 + qMatrixInfo[0], qMatrixRelRow+chunksqMatrix[0].start(), chunksqMatrix, null, qMatrixInfo);
            qMatrixRelRow = qMatrixInfo[2];
          }
            if (glmfun == null)
              glmfun = new GLMWeightsFun(_parms._family, _parms._link, _parms._tweedie_variance_power,
                      _parms._tweedie_link_power, 0);
          _sumDev += setZDevEta(chunks4ZDev, chunks, rowIndex,  zdevAbsRelRowNumber, glmfun);
          setHv(chunksqMatrix, chunks[1], rowIndex, qMatrixRelRow);  // get hv from qmatrix only
          qMatrixRelRow++;
          zdevAbsRelRowNumber++;
        }
      }
    }

    @Override
    public void reduce(ReturnGLMMMERunInfoData other){
      this._sumEtaDiffSq += other._sumEtaDiffSq;
      this._sumEtaSq += other._sumEtaSq;
      this._sumDev += other._sumDev;
    }

    public static void setHv(Chunk[] qmat, Chunk hv, int relRowIndex, int qmatRelRowIndex) {
      int numCol = qmat.length;
      double rowSum = 0;
      for (int colIndex=0; colIndex < numCol; colIndex++) {
        double temp = qmat[colIndex].atd(qmatRelRowIndex);
        rowSum += temp*temp;
      }
      hv.set(relRowIndex, rowSum > ONEEQUAL?ONEEQUAL:rowSum);
    }
    
    public double setZDevEta(Chunk[] dinfoChunks, Chunk[] destChunk, int relRowIndex, int dinfoRowIndex,
                             GLMWeightsFun glmfun) {
      destChunk[0].set(relRowIndex, dinfoChunks[1].atd(dinfoRowIndex)); // set AugZ value
      double eta = dinfoChunks[2].atd(dinfoRowIndex);
      destChunk[3].set(relRowIndex, eta); // set new eta value
      double temp2 = eta-destChunk[5].atd(relRowIndex);
      _sumEtaDiffSq += temp2*temp2;
      _sumEtaSq += eta*eta;
      double temp = dinfoChunks[0].atd(dinfoRowIndex)-glmfun.linkInv(eta);
      destChunk[4].set(relRowIndex, temp);  // set resid = (y-mu.i)
      double prior_weight = dinfoChunks[3]==null?1:dinfoChunks[3].atd(dinfoRowIndex);
      double devVal = prior_weight*temp*temp;
      destChunk[2].set(relRowIndex, devVal < ZEROEQUAL?ZEROEQUAL:devVal);
      return devVal;
    }
  }
  public static class ExpandRandomColumns extends MRTask<ExpandRandomColumns> {
    Job _job;
    int[] _randomColIndices;
    int[] _randomColLevels;
    int _numRandCols;
    int _startRandomExpandedColumn;
    public ExpandRandomColumns(Job job, int[] randomColIndices, int[] randomColLevels, int startExpandedCol) {
      _job = job;
      _randomColIndices = randomColIndices;
      _randomColLevels = randomColLevels;
      _startRandomExpandedColumn = startExpandedCol;
      _numRandCols = randomColIndices.length;
    }

    @Override
    public void map(Chunk[] chunks) {
      int chunkRowLen = chunks[0].len();
      int columnOffset = _startRandomExpandedColumn;
      for (int colIndex = 0; colIndex < _numRandCols; colIndex++) { // expand each random column for each row
        for (int rowIndex = 0; rowIndex < chunkRowLen; rowIndex++) {
          int randColVal = ((int) chunks[_randomColIndices[colIndex]].atd(rowIndex)) + columnOffset;
          chunks[randColVal].set(rowIndex, 1);
        }
        columnOffset += _randomColLevels[colIndex];
      }
    }
  }

  // generate AugZ*W as a double array 
  public static class CalculateAugZWData extends MRTask<CalculateAugZWData> {
    public DataInfo _dinfo; // contains X and Z in response
    public long _numDataRows;
    Job _job;
    public int[] _dinfoWCol;

    public  CalculateAugZWData(Job job, DataInfo dInfo, int dinfoRespColStart) { // pass it norm mul and norm sup - in the weights already done. norm
      _job = job;
      _dinfo = dInfo;
      _numDataRows = _dinfo._adaptedFrame.numRows();  // number of data rows
      _dinfoWCol = new int[]{dinfoRespColStart, dinfoRespColStart+1};
    }

    @Override
    public void map(Chunk[] chunks) { // chunks from AugZ
      long chkStartIdx = chunks[0].start();// first row number of chunks
      if (chkStartIdx < _numDataRows) { // only deal with data portion
        long lastChkIdx = chkStartIdx+chunks[0]._len;
        int chunkLen = lastChkIdx < _numDataRows?chunks[0]._len:(int)(_numDataRows-chkStartIdx);
        Chunk[] augzwChunks = new Chunk[2]; // loaded from _dinfo or _prior_weight_psi
        int[] extraChkInfo = new int[3];
        extraChkInfo = getCorrectChunk(_dinfo._adaptedFrame, 0, chkStartIdx, augzwChunks,
                _dinfoWCol, extraChkInfo);

        int extraRelRow = extraChkInfo[2];
        for (int rowIndex = 0; rowIndex < chunkLen; rowIndex++) {
          if (extraRelRow >= extraChkInfo[1]) { // need to load new chunk
            long chkAbsRowNumber = rowIndex + chkStartIdx;
            extraChkInfo = getCorrectChunk(_dinfo._adaptedFrame, extraChkInfo[0], chkAbsRowNumber, augzwChunks,
                    _dinfoWCol, extraChkInfo);

            extraRelRow = extraChkInfo[2];
          }
          chunks[0].set(rowIndex, augzwChunks[0].atd(extraRelRow) * augzwChunks[1].atd(extraRelRow));
          extraRelRow++;
        }
      }
    }
  }

  // generate AugZ*W as a double array 
  public static class CalculateAugZWRandCols extends MRTask<CalculateAugZWRandCols> {
    public long _numDataRows;
    Job _job;
    Frame _prior_weight_psi;  // contains prior_weight, wpsi, zmi for random effects/columns
    public int[] _weightWCol;

    public  CalculateAugZWRandCols(Job job, Frame prior_weight_psi, int weightColStart, 
                                   long numDataRows) { // pass it norm mul and norm sup - in the weights already done. norm
      _job = job;
      _prior_weight_psi = prior_weight_psi;
      _numDataRows = numDataRows;  // number of data rows
      _weightWCol = new int[]{weightColStart, weightColStart+1};
    }

    @Override
    public void map(Chunk[] chunks) { // chunks from AugZW
      long chkStartIdx = chunks[0].start();// first row number of chunks
      long chkEndIdx = chkStartIdx+chunks[0]._len;
      if (chkStartIdx > _numDataRows || chkEndIdx > _numDataRows) {
        int chunkLen = chunks[0]._len;
        int chunkStartRow = chkStartIdx > _numDataRows?0:(int)(_numDataRows-chkStartIdx);
        Chunk[] augzwChunks = new Chunk[2]; // loaded from _dinfo or _prior_weight_psi
        int[] extraChkInfo = new int[3];
        extraChkInfo = getCorrectChunk(_prior_weight_psi, 0, 
                chunkStartRow+chkStartIdx - _numDataRows, augzwChunks, _weightWCol, extraChkInfo);
        int extraRelRow = extraChkInfo[2];
        for (int rowIndex = chunkStartRow; rowIndex < chunkLen; rowIndex++) {
          if (extraRelRow >= extraChkInfo[1]) { // need to load new chunk
            extraChkInfo = getCorrectChunk(_prior_weight_psi, extraChkInfo[0]+1, 
                    extraRelRow+augzwChunks[0].start(), augzwChunks, _weightWCol, extraChkInfo);
            extraRelRow = extraChkInfo[2];
          }
          chunks[0].set(rowIndex, augzwChunks[0].atd(extraRelRow) * augzwChunks[1].atd(extraRelRow));
          extraRelRow++;
        }
      }
    }
  }
  
  // generate AugZ*W as a double array 
  public static class CalculateAugZW extends MRTask<CalculateAugZW> {
    GLMParameters _parms;
    public DataInfo _dinfo; // contains X and Z in response
    public int[] _random_columnsID;
    public int _augZID;
    public int _dataColNumber;
    public int _randColNumber;
    public int _numColStart;
    public int _numRandCol;
    public long _numDataRows;
    Job _job;
    Frame _prior_weight_psi;  // contains prior_weight, wpsi, zmi for random effects/columns
    public int[] _dinfoWCol;
    public int[] _weightWCol;

    public  CalculateAugZW(Job job, DataInfo dInfo, GLMParameters params, Frame prior_weight_psi, int randCatLevels,
                           int dinfoRespColStart, int weightColStart) { // pass it norm mul and norm sup - in the weights already done. norm
      _job = job;
      _dinfo = dInfo;
      _parms = params;
      _prior_weight_psi = prior_weight_psi;
      _augZID = _dinfo.responseChunkId(2);  // 0: response, 1: wdata, 2: zi
      _dataColNumber = _dinfo.fullN()+1; // add 1 for intercept at the beginning
      _numColStart = _dinfo._cats==0?0:_dinfo._catOffsets[_dinfo._cats];
      _numRandCol = _parms._random_columns.length;
      _random_columnsID = new int[_numRandCol];
      System.arraycopy(_parms._random_columns, 0, _random_columnsID, 0, _numRandCol);
      _randColNumber = randCatLevels; // total number of random columns expanded
      _numDataRows = _dinfo._adaptedFrame.numRows();  // number of data rows
      _dinfoWCol = new int[]{dinfoRespColStart, dinfoRespColStart+1};
      _weightWCol = new int[]{weightColStart, weightColStart+1};
    }

    @Override
    public void map(Chunk[] chunks) { // chunks from AugZ
      long chkStartIdx = chunks[0].start();// first row number of chunks
      int chunkLen = chunks[0]._len;
      Chunk[] augzwChunks = new Chunk[2]; // loaded from _dinfo or _prior_weight_psi
      int[] extraChkInfo = new int[3];
      if (chkStartIdx < _numDataRows) { // grab wdata and zi from _dinfo._adaptedFrame.
        extraChkInfo = getCorrectChunk(_dinfo._adaptedFrame, 0, chkStartIdx, augzwChunks,
                _dinfoWCol, extraChkInfo);
      } else {  // grab weight from _prior_weight_psi
        extraChkInfo = getCorrectChunk(_prior_weight_psi, 0, chkStartIdx-_numDataRows, augzwChunks,
                _weightWCol, extraChkInfo);
      }
      int extraRelRow = extraChkInfo[2];
      for (int rowIndex=0; rowIndex < chunkLen; rowIndex++) {
        if (extraRelRow >= extraChkInfo[1]) { // need to load new chunk
          long chkAbsRowNumber = rowIndex+chkStartIdx;
          if (chkAbsRowNumber < _numDataRows) { // need to load from dinfo
            extraChkInfo = getCorrectChunk(_dinfo._adaptedFrame, extraChkInfo[0], chkAbsRowNumber, augzwChunks,
                    _dinfoWCol, extraChkInfo);
          } else { // need to load from w_prior_psi
            extraChkInfo = getCorrectChunk(_prior_weight_psi, extraChkInfo[0], chkAbsRowNumber-_numDataRows,
                    augzwChunks, _weightWCol, extraChkInfo);
          }
          extraRelRow = extraChkInfo[2];
        }
        chunks[0].set(rowIndex, augzwChunks[0].atd(extraRelRow)*augzwChunks[1].atd(extraRelRow));
        extraRelRow++;
      }
    }
  }
  
  public static class HelpercAIC extends MRTask<HelpercAIC> {
    final double TWOPI = 2*Math.PI; // constant to be used for calculation
    final double _logOneO2pisd = -Math.log(Math.sqrt(TWOPI));
    public double _p; // stores sum(hv)
    public double _devOphi; // store sum(dev/glm.phi
    public double _constT;  // store *sum(log(2*pi*glm.phi);
    boolean _weightPresent;  // indicate if we have prior-weight
    final double _varFix;
    
    public HelpercAIC(boolean weightP, double varFix) {
      _weightPresent = weightP;
      _varFix = varFix;
    }

    @Override
    public void map(Chunk[] chunks) {
      _p = 0;
      _devOphi = 0;
      _constT = 0;
      int chunkLen = chunks[0].len();
      
      for (int rowIndex=0; rowIndex < chunkLen; rowIndex++) {
        double weight = _weightPresent?chunks[2].atd(rowIndex):1;
        double glm_phi = _varFix/weight;
        _constT += Math.log(TWOPI*glm_phi);
        _p += chunks[0].atd(rowIndex);
        _devOphi += chunks[1].atd(rowIndex)/glm_phi;
      }
    }
    
    @Override public void reduce(HelpercAIC other) {
      _p += other._p;
      _constT += other._constT;
      _devOphi += other._devOphi;
    }
  }

  /***
   * This class given the weights generated and stored in _dinfo and wpsi, will multiply the weights to AugXZ and
   * store them in AugXZ.
   */
  public static class CalculateW4Data extends MRTask<CalculateW4Data> {
    GLMParameters _parms;
    public DataInfo _dinfo;
    public int _prior_weightID; // column ID of prior-weights for data rows
    public int _wdataID;        // column ID to store weight info for data rows
    public int _offsetID;       // column ID for offets
    public int[] _random_columnsID; // column ID where random column values are stored
    public int[] _randCatLevels;  // categorical levels for random columns
    public int _augZID;           // column ID where zi is stored
    public int _etaOldID;         // column ID where old eta.i value is stored
    public int _dataColNumber;  // fixed column number
    public int _numColStart;    // numerical fixed column index start
    public double[] _beta;    // store fixed coefficients
    public double[] _ubeta;   // store random coefficients
    public double[] _psi;
    public double[] _phi;
    public double _tau;
    public int _numRandCol; // number of random effects/columns
    Job _job;
    public double _sumEtaDiffSq;  // store sum of (eta.i-eta.old)^2
    public double _sumEtaSq;      // store sum(eta.i^2)
    public double _HL_correction; // correction to Hierarchy likelihood, determined by distribution used.

    public CalculateW4Data(Job job, DataInfo dInfo, GLMParameters params, int[] randCatLevels,
                           double[] beta, double[] ubeta, double[] psi, double[] phi, double tau, double hlCorrection) { // pass it norm mul and norm sup - in the weights already done. norm
      _job = job;
      _dinfo = dInfo;
      _parms = params;
      _prior_weightID = _dinfo._weights?_dinfo.weightChunkId():-1;
      _augZID = _dinfo.responseChunkId(2);  // 0: response, 1: wdata, 2: zi, 3: etaOld
      _wdataID = _dinfo.responseChunkId(1);
      _etaOldID = _dinfo.responseChunkId(3);
      _offsetID = _dinfo._offset?_dinfo.offsetChunkId():-1;
      _dataColNumber = _dinfo.fullN()+1; // add 1 for intercept at the beginning
      _numColStart = _dinfo.numCats()==0?0:_dinfo._catOffsets[_dinfo._cats];
      _numRandCol = _parms._random_columns.length;
      _random_columnsID = _parms._random_columns;
      _randCatLevels = randCatLevels;
      _beta = beta;
      _ubeta = ubeta;
      _psi = psi;
      _phi = phi;
      _tau = tau;
      _HL_correction=hlCorrection;
      _sumEtaDiffSq=0;
      _sumEtaSq=0;
    }

    @Override
    public void map(Chunk[] chunks) { // chunks from _dinfo._adaptedFrame
      GLMWeightsFun glmfun = new GLMWeightsFun(_parms._family, _parms._link, _parms._tweedie_variance_power,
              _parms._tweedie_link_power, 0);
      Row row = _dinfo.newDenseRow(); // one row of fixed effects/columns
      double eta, mu, temp, zi, wdata;
      for (int i = 0; i < chunks[0]._len; ++i) { // going over all the rows in the chunk of _dinfo._adaptedFrame
        _dinfo.extractDenseRow(chunks, i, row);
        if (!row.isBad() && row.weight != 0) {
          eta = row.innerProduct(_beta) + row.offset;
          for (int index=0; index < _numRandCol; index++) {
            eta += _ubeta[(int)row.response(4+index)];
          }
          if (Double.isNaN(eta))
            throw H2O.fail("GLM.MME diverged! Try different starting values.");
          double etaDiff = eta - row.response(3);
          chunks[_etaOldID].set(i, eta);  // save current eta as etaOld for next round
          _sumEtaDiffSq += etaDiff * etaDiff;
          _sumEtaSq += eta * eta;
          mu = glmfun.linkInv(eta);
         temp = glmfun.linkInvDeriv(mu);
          zi = eta - row.offset + (row.response(0) - mu) / temp - _HL_correction;
          chunks[_augZID].set(i, zi);
          wdata = row.weight * temp * temp / (glmfun.variance(mu) * _tau);
          chunks[_wdataID].set(i, Math.sqrt(wdata)); // set the new weight back to _dinfo.
        }
      }
    }

    @Override
    public void reduce(CalculateW4Data other){
      this._sumEtaDiffSq += other._sumEtaDiffSq;
      this._sumEtaSq += other._sumEtaSq;
    }

    /***
     * This method will calculate wdata and store it in dinfo response columns.  In addition, it will calculate
     * sum(eta.i-eta.o)^2, sum(eta.i^2).  It will return sqrt(wdata).  We use the same method from R to calculate
     * wdata.
     *
     * @param glmfun
     * @param beta
     * @param ubeta
     * @param tau
     * @param row
     * @param chunks: chunks from dinfo
     * @param rowIndex
     * @return
     */
/*    public double getWeights(GLMWeightsFun glmfun, double[] beta, double[] ubeta, double tau, Row row, Chunk[] chunks,
                             int rowIndex) {
      double eta = row.innerProduct(beta) + row.offset;
      for (int index=0; index < _numRandCol; index++) {
        eta += ubeta[(int)row.response(4+index)];
      }
      if (Double.isNaN(eta))
        throw H2O.fail("GLM.MME diverged! Try different starting values.");
      double etaDiff = eta - row.response(3);
      chunks[_etaOldID].set(rowIndex, eta);  // save current eta as etaOld for next round
      _sumEtaDiffSq += etaDiff * etaDiff;
      _sumEtaSq += eta * eta;
      double mu = glmfun.linkInv(eta);
      double temp = glmfun.linkInvDeriv(mu);
      double zi = eta - row.offset + (row.response(0) - mu) / temp - _HL_correction;
      chunks[_augZID].set(rowIndex, zi);
      double wdata = row.weight * temp * temp / (glmfun.variance(mu) * tau);
      return Math.sqrt(wdata);
    }*/
  }

  /***
   * This class will update the frame AugXZ which contains Ta*sqrt(W inverse) from documentation: 
   *
   * - multiply the generated weight value to Ta and store in AugXZ;
   */
    public static class DataAddW2AugXZ extends MRTask<DataAddW2AugXZ> {
    public DataInfo _dinfo;
    public int _wdataID;        // column ID to store weight info for data rows
    public int[] _randCatLevels;  // categorical levels for random columns
    public int _dataColNumber;  // fixed column number
    public int _numColStart;    // numerical fixed column index start
    public int _numRandCol; // number of random effects/columns
    Job _job;
    public long _dataRows;

    public DataAddW2AugXZ(Job job, DataInfo dInfo, int[] randCatLevels) { // pass it norm mul and norm sup - in the weights already done. norm
      _job = job;
      _dinfo = dInfo;
      _wdataID = 1;
      _dataColNumber = _dinfo.fullN()+1; // add 1 for intercept at the beginning
      _numColStart = _dinfo._cats==0?0:_dinfo._catOffsets[_dinfo._cats];
      _numRandCol = randCatLevels.length;
      _randCatLevels = randCatLevels;
      _dataRows = _dinfo._adaptedFrame.numRows();
    }

    @Override
    public void map(Chunk[] chunks) { // chunks from augXZ but only takes care of data part
      long chkStartIdx = chunks[0].start(); // first row number of chunks of augXZ
      if (chkStartIdx < _dataRows) {  // only process data of augXZ upto dataRow
        int numColAugXZ = chunks.length;
        Chunk[] dinfoChunks = new Chunk[_dinfo._adaptedFrame.numCols()];
        double[] processedRow = new double[chunks.length]; // store one row of AugXZ: wdata*(intercept, x, z (expanded random columns))
        int[] dinfoChunkInfo = getCorrectChunk(_dinfo._adaptedFrame, 0, chkStartIdx, dinfoChunks, null, null);
        int dinfoChunkRelRow = dinfoChunkInfo[2];
        int chunkLen = chkStartIdx + chunks[0]._len >= _dataRows ? (int) (_dataRows - chkStartIdx) : chunks[0]._len;
        Row row = _dinfo.newDenseRow(); // one row of fixed effects/columns
        double wdata;
        for (int index = 0; index < chunkLen; index++) {
          if (dinfoChunkRelRow >= dinfoChunkInfo[1]) {
            dinfoChunkInfo = getCorrectChunk(_dinfo._adaptedFrame, dinfoChunkInfo[0]+1,
                    dinfoChunkRelRow+dinfoChunks[0].start(), dinfoChunks, null, null);
            dinfoChunkRelRow = dinfoChunkInfo[2];
          }
          _dinfo.extractDenseRow(dinfoChunks, dinfoChunkRelRow, row);
          Arrays.fill(processedRow, 0.0);
          wdata = row.response[_wdataID];
          row.scalarProduct(wdata, processedRow, _numColStart); // generate wdata*X
          int offset = _dataColNumber;
          for (int randColIndex = 0; randColIndex < _numRandCol; randColIndex++) { // generate x*Z
            int processRowIdx = offset + (int) row.response[4 + randColIndex];  // 0: response, 1: weight, 2: zi, 3: etai, 4 or more: z
            processedRow[processRowIdx] = wdata;  // save wdata as
            offset += _randCatLevels[randColIndex]; // write to next random column value
          }
          for (int colIndex = 0; colIndex < numColAugXZ; colIndex++) {      // assign the rows to the AugXZ
            chunks[colIndex].set(index, processedRow[colIndex]); // set w*X for intercept, data, random columns
          }
          dinfoChunkRelRow++;

        }
      }
    }
    
    /***
     * Given the chkIdx, this method will fetch the chunks with columns specified in vecIdx
     * @param augXZ: Frame frome which chunks are fetched
     * @param chkIdx: chunk index to fetch
     * @param chks: store fetched chunks
     * @param vecIdx: null, fetch all columns, else, contains columns of interest to fetch
     */
    public static void getAllChunks(Frame augXZ, int chkIdx, Chunk[] chks, int[] vecIdx) {
      if (vecIdx==null) { // copy all vectors of the chunk
        int chkLen = chks.length;
        for (int chkIndex =1 ; chkIndex < chkLen; chkIndex++)
          chks[chkIndex] = augXZ.vec(chkIndex).chunkForChunkIdx(chkIdx);
      } else {
        int veclen = vecIdx.length;
        for (int index=1; index < veclen; index++)
          chks[index] = augXZ.vec(vecIdx[index]).chunkForChunkIdx(chkIdx);
      }
    }

    /***
     * Given the absolute row index of interest, this method will find the chunk index of augXZ that contains the
     * absolute row index
     *
     * @param augXZ: Frame where chunks will be fetched
     * @param chkIdx: chunk index to check if it contains the absolute row index of interest
     * @param currentRowAbs: absolute row index of interest
     * @param chks: chunks to stored fetched chunk
     * @param vecIdx: column indices to fetch.  If null, fetch all columns
     * @return
     */
    public static int getOneSingleChunk(Frame augXZ, int chkIdx, long currentRowAbs, Chunk[] chks, int[] vecIdx) {
      chkIdx = chkIdx>=augXZ.vec(0).nChunks()?0:chkIdx;
      if (vecIdx==null) { // copy all vectors of the chunk
        // fetch one vector and check if it contains the correct rows
        chks[0] = augXZ.vec(0).chunkForChunkIdx(chkIdx);
      } else {
        chks[0] = augXZ.vec(vecIdx[0]).chunkForChunkIdx(chkIdx);
      }
      // find correct row offset into chunk.
      long strow = chks[0].start();
      long endrow = chks[0].len()+strow;
      if ((currentRowAbs >= strow) && (currentRowAbs< endrow))
        return -1;
      else if (currentRowAbs < strow)
        return (chkIdx-1);
      else
        return (chkIdx+1);
    }
    
    /**
     * This method, given the absolute row index of interest, will grab the correct chunk from augXZ containing the
     * same absolute row index of interest.  The chunks of augXZ will be stored in chks.  In addition, an integer
     * array will be returned that contain the following information about the fetched chunk of augXZ:
     * - index 0: chunk index;
     * - index 1: number of rows of fetched chunk;
     * - index 2: relative starting index of fetched chunk that will correspond to the absolute row index of interest
     *            passed to this method.
     *
     * @param augXZ: Frame from which chunks will be grabbed
     * @param chkIdx: starting chunk index to looking at
     * @param currentRowAbs: absolute row index of first row of interest
     * @param chks: stored fetched chunk
     * @param vecIdx: null if all columns should be fetched.  Else, contains the columns to be fetched
     * @param returnInfo: information about fetched chunk
     * @return
     */
    public static int[] getCorrectChunk(Frame augXZ, int chkIdx, long currentRowAbs, Chunk[] chks, int[] vecIdx,
                                        int[] returnInfo) {
      assert currentRowAbs < augXZ.numRows();
      int currentIdx = chkIdx >= augXZ.vec(0).nChunks()?0:chkIdx;
      while (currentIdx >= 0) { // currentIdx will be -1 if found the correct chunk
        currentIdx = getOneSingleChunk(augXZ, currentIdx, currentRowAbs, chks, vecIdx); // find chunk that contains currentRowAbs
      }
      getAllChunks(augXZ, chks[0].cidx(),chks, vecIdx); // fetched the chunks of augXZ to chks
      if (returnInfo == null) {
        returnInfo = new int[3];
      }
      returnInfo[0] = chks[0].cidx(); // chunk index of fetched chunks
      returnInfo[1] = chks[0].len();  // number of rows in fetched chunks
      returnInfo[2] = (int) (currentRowAbs-chks[0].start());  // relative row start of first row of fetched chunk
      return returnInfo;
    }
  }
  
  public static class GLMCoordinateDescentTaskSeqNaive extends MRTask<GLMCoordinateDescentTaskSeqNaive> {
    public double [] _normMulold;
    public double [] _normSubold;
    public double [] _normMulnew;
    public double [] _normSubnew;
    final double [] _betaold; // current old value at j
    final double [] _betanew; // global beta @ j-1 that was just updated.
    final int [] _catLvls_new; // sorted list of indices of active levels only for one categorical variable
    final int [] _catLvls_old;
    public double [] _temp;
    boolean _skipFirst;
    long _nobs;
    int _cat_num; // 1: c and p categorical, 2:c numeric and p categorical, 3:c and p numeric , 4: c categorical and previous num.
    boolean _interceptnew;
    boolean _interceptold;

    public  GLMCoordinateDescentTaskSeqNaive(boolean interceptold, boolean interceptnew, int cat_num ,
                                        double [] betaold, double [] betanew, int [] catLvlsold, int [] catLvlsnew,
                                        double [] normMulold, double [] normSubold, double [] normMulnew, double [] normSubnew,
                                             boolean skipFirst ) { // pass it norm mul and norm sup - in the weights already done. norm
      //mul and mean will be null without standardization.
      _normMulold = normMulold;
      _normSubold = normSubold;
      _normMulnew = normMulnew;
      _normSubnew = normSubnew;
      _cat_num = cat_num;
      _betaold = betaold;
      _betanew = betanew;
      _interceptold = interceptold; // if updating beta_1, then the intercept is the previous column
      _interceptnew = interceptnew; // if currently updating the intercept value
      _catLvls_old = catLvlsold;
      _catLvls_new = catLvlsnew;
      _skipFirst = skipFirst;
    }

    @Override
    public void map(Chunk [] chunks) {
      int cnt = 0;
      Chunk wChunk = chunks[cnt++];
      Chunk zChunk = chunks[cnt++];
      Chunk ztildaChunk = chunks[cnt++];
      Chunk xpChunk=null, xChunk=null;

      _temp = new double[_betaold.length];
      if (_interceptnew) {
        xChunk = new C0DChunk(1,chunks[0]._len);
        xpChunk = chunks[cnt++];
      } else {
        if (_interceptold) {
          xChunk = chunks[cnt++];
          xpChunk = new C0DChunk(1,chunks[0]._len);
        }
        else {
          xChunk = chunks[cnt++];
          xpChunk = chunks[cnt++];
        }
      }

      // For each observation, add corresponding term to temp - or if categorical variable only add the term corresponding to its active level and the active level
      // of the most recently updated variable before it (if also cat). If for an obs the active level corresponds to an inactive column, we just dont want to include
      // it - same if inactive level in most recently updated var. so set these to zero ( Wont be updating a betaj which is inactive) .
      for (int i = 0; i < chunks[0]._len; ++i) { // going over all the rows in the chunk
        double betanew = 0; // most recently updated prev variable
        double betaold = 0; // old value of current variable being updated
        double w = wChunk.atd(i);
        if(w == 0) continue;
        ++_nobs;
        int observation_level = 0, observation_level_p = 0;
        double val = 1, valp = 1;
        if(_cat_num == 1) {
          observation_level = (int) xChunk.at8(i); // only need to change one temp value per observation.
          if (_catLvls_old != null)
            observation_level = Arrays.binarySearch(_catLvls_old, observation_level);

          observation_level_p = (int) xpChunk.at8(i); // both cat
          if (_catLvls_new != null)
            observation_level_p = Arrays.binarySearch(_catLvls_new, observation_level_p);

          if(_skipFirst){
            observation_level--;
            observation_level_p--;
          }
        }
        else if(_cat_num == 2){
          val = xChunk.atd(i); // current num and previous cat
          if (_normMulold != null && _normSubold != null)
            val = (val - _normSubold[0]) * _normMulold[0];

          observation_level_p = (int) xpChunk.at8(i);
          if (_catLvls_new != null)
            observation_level_p = Arrays.binarySearch(_catLvls_new, observation_level_p);

          if(_skipFirst){
            observation_level_p--;
          }
        }
        else if(_cat_num == 3){
          val = xChunk.atd(i); // both num
          if (_normMulold != null && _normSubold != null)
            val = (val - _normSubold[0]) * _normMulold[0];
          valp = xpChunk.atd(i);
          if (_normMulnew != null && _normSubnew != null)
            valp = (valp - _normSubnew[0]) * _normMulnew[0];
        }
        else if(_cat_num == 4){
          observation_level = (int) xChunk.at8(i); // current cat
          if (_catLvls_old != null)
            observation_level = Arrays.binarySearch(_catLvls_old, observation_level); // search to see if this level is active.
          if(_skipFirst){
            observation_level--;
          }

          valp = xpChunk.atd(i); //prev numeric
          if (_normMulnew != null && _normSubnew != null)
            valp = (valp - _normSubnew[0]) * _normMulnew[0];
        }

        if(observation_level >= 0)
         betaold = _betaold[observation_level];
        if(observation_level_p >= 0)
         betanew = _betanew[observation_level_p];

        if (_interceptnew) {
            ztildaChunk.set(i, ztildaChunk.atd(i) - betaold + valp * betanew); //
            _temp[0] += w * (zChunk.atd(i) - ztildaChunk.atd(i));
          } else {
            ztildaChunk.set(i, ztildaChunk.atd(i) - val * betaold + valp * betanew);
            if(observation_level >=0 ) // if the active level for that observation is an "inactive column" don't want to add contribution to temp for that observation
            _temp[observation_level] += w * val * (zChunk.atd(i) - ztildaChunk.atd(i));
         }

       }

    }

    @Override
    public void reduce(GLMCoordinateDescentTaskSeqNaive git){
      ArrayUtils.add(_temp, git._temp);
      _nobs += git._nobs;
      super.reduce(git);
    }
  }


  public static class GLMCoordinateDescentTaskSeqIntercept extends MRTask<GLMCoordinateDescentTaskSeqIntercept> {
    final double [] _betaold;
    public double _temp;
    DataInfo _dinfo;

    public  GLMCoordinateDescentTaskSeqIntercept( double [] betaold, DataInfo dinfo) {
      _betaold = betaold;
      _dinfo = dinfo;
    }

    @Override
    public void map(Chunk [] chunks) {
      int cnt = 0;
      Chunk wChunk = chunks[cnt++];
      Chunk zChunk = chunks[cnt++];
      Chunk filterChunk = chunks[cnt++];
      Row r = _dinfo.newDenseRow();
      for(int i = 0; i < chunks[0]._len; ++i) {
        if(filterChunk.atd(i)==1) continue;
        _dinfo.extractDenseRow(chunks,i,r);
        _temp = wChunk.at8(i)* (zChunk.atd(i)- r.innerProduct(_betaold) );
      }

    }

    @Override
    public void reduce(GLMCoordinateDescentTaskSeqIntercept git){
      _temp+= git._temp;
      super.reduce(git);
    }

  }


  public static class GLMGenerateWeightsTask extends MRTask<GLMGenerateWeightsTask> {
    final GLMParameters _params;
    final double [] _betaw;
    double [] denums;
    double wsum,wsumu;
    DataInfo _dinfo;
    double _likelihood;

    public GLMGenerateWeightsTask(Key jobKey, DataInfo dinfo, GLMModel.GLMParameters glm, double[] betaw) {
      _params = glm;
      _betaw = betaw;
      _dinfo = dinfo;
    }

    @Override
    public void map(Chunk [] chunks) {
      Chunk wChunk = chunks[chunks.length-3];
      Chunk zChunk = chunks[chunks.length-2];
      Chunk zTilda = chunks[chunks.length-1];
      chunks = Arrays.copyOf(chunks,chunks.length-3);
      denums = new double[_dinfo.fullN()+1]; // full N is expanded variables with categories

      Row r = _dinfo.newDenseRow();
      for(int i = 0; i < chunks[0]._len; ++i) {
        _dinfo.extractDenseRow(chunks,i,r);
        if (r.isBad() || r.weight == 0) {
          wChunk.set(i,0);
          zChunk.set(i,0);
          zTilda.set(i,0);
          continue;
        }
        final double y = r.response(0);
        assert ((_params._family != Family.gamma) || y > 0) : "illegal response column, y must be > 0  for family=Gamma.";
        assert ((_params._family != Family.binomial) || (0 <= y && y <= 1)) : "illegal response column, y must be <0,1>  for family=Binomial. got " + y;
        final double w, eta, mu, var, z;
        final int numStart = _dinfo.numStart();
        double d = 1;
        eta = r.innerProduct(_betaw);
        if (_params._family == Family.gaussian && _params._link == Link.identity) {
          w = r.weight;
          z = y - r.offset;
          mu = 0;
        } else {
          mu = _params.linkInv(eta + r.offset);
          var = Math.max(1e-6, _params.variance(mu)); // avoid numerical problems with 0 variance
          d = _params.linkDeriv(mu);
          z = eta + (y - mu) * d;
          w = r.weight / (var * d * d);
        }
        _likelihood += _params.likelihood(y,mu);
        zTilda.set(i,eta-_betaw[_betaw.length-1]);
        assert w >= 0 || Double.isNaN(w) : "invalid weight " + w; // allow NaNs - can occur if line-search is needed!
        wChunk.set(i,w);
        zChunk.set(i,z);

        wsum+=w;
        wsumu+=r.weight; // just add the user observation weight for the scaling.

        for(int j = 0; j < r.nBins; ++j)  { // go over cat variables
          denums[r.binIds[j]] +=  w; // binIds skips the zeros.
        }
        for(int j = 0; j < r.nNums; ++j){ // num vars
          int id = r.numIds == null?(j + numStart):r.numIds[j];
          denums[id]+= w*r.get(id)*r.get(id);
        }

      }
    }

    @Override
    public void reduce(GLMGenerateWeightsTask git){ // adding contribution of all the chunks
      ArrayUtils.add(denums, git.denums);
      wsum+=git.wsum;
      wsumu += git.wsumu;
      _likelihood += git._likelihood;
      super.reduce(git);
    }


  }


  public static class ComputeSETsk extends FrameTask2<ComputeSETsk> {
//    final double [] _betaOld;
    final double [] _betaNew;
    double _sumsqe;
    double _wsum;

    public ComputeSETsk(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey, /*, double [] betaOld,*/ double [] betaNew, GLMParameters parms) {
      super(cmp, dinfo, jobKey);
//      _betaOld = betaOld;
      _glmf = new GLMWeightsFun(parms);
      _betaNew = betaNew;
    }

    transient double _sparseOffsetOld = 0;
    transient double _sparseOffsetNew = 0;
    final GLMWeightsFun _glmf;
    transient GLMWeights _glmw;
    @Override public void chunkInit(){
      if(_sparse) {
//        _sparseOffsetOld = GLM.sparseOffset(_betaNew, _dinfo);
        _sparseOffsetNew = GLM.sparseOffset(_betaNew, _dinfo);
      }
      _glmw = new GLMWeights();
    }

    @Override
    protected void processRow(Row r) {
      double z = r.response(0) - r.offset;
      double w = r.weight;
      if(_glmf._family != Family.gaussian) {
//        double etaOld = r.innerProduct(_betaOld) + _sparseOffsetOld;
        double etaOld = r.innerProduct(_betaNew) + _sparseOffsetNew;
        _glmf.computeWeights(r.response(0),etaOld,r.offset,r.weight,_glmw);
        z = _glmw.z;
        w = _glmw.w;
      }
      double eta = _glmf._family.equals(Family.tweedie)?r.innerProduct(_betaNew) + _sparseOffsetNew+r.offset:r.innerProduct(_betaNew) + _sparseOffsetNew;
      double xmu = _glmf._family.equals(Family.tweedie)?_glmf.linkInv(eta):0;
      _sumsqe += _glmf._family.equals(Family.tweedie)?
              ((r.response(0)-xmu)*(r.response(0)-xmu))*r.weight/Math.pow(xmu, _glmf._var_power):
              w*(eta - z)*(eta - z);

      _wsum += Math.sqrt(w);
    }
    @Override
    public void reduce(ComputeSETsk c){_sumsqe += c._sumsqe; _wsum += c._wsum;}
  }

  static class GLMIncrementalGramTask extends MRTask<GLMIncrementalGramTask> {
    final int[] _newCols;
    final DataInfo _dinfo;
    double[][] _gram;
    double [] _xy;
    final double [] _beta;
    final GLMWeightsFun _glmf;

    public GLMIncrementalGramTask(int [] newCols, DataInfo dinfo, GLMWeightsFun glmf, double [] beta){
      this._newCols = newCols;
      _glmf = glmf;
      _dinfo = dinfo;
      _beta = beta;
    }
    public void map(Chunk[] chks) {
      GLMWeights glmw = new GLMWeights();
      double [] wsum = new double[_dinfo.fullN()+1];
      double ywsum = 0;
      DataInfo.Rows rows = _dinfo.rows(chks);
      double [][] gram = new double[_newCols.length][_dinfo.fullN() + 1];
      double [] xy = new double[_newCols.length];
      final int ns = _dinfo.numStart();
      double sparseOffset = rows._sparse?GLM.sparseOffset(_beta,_dinfo):0;
      for (int rid = 0; rid < rows._nrows; ++rid) {
        int j = 0;
        Row r = rows.row(rid);
        if(r.weight == 0) continue;
        if(_beta != null) {
          _glmf.computeWeights(r.response(0), r.innerProduct(_beta) + sparseOffset, r.offset, r.weight, glmw);
        } else {
          glmw.w = r.weight;
          glmw.z = r.response(0);
        }
        r.addToArray(glmw.w,wsum);
        ywsum += glmw.z*glmw.w;
        // first cats
        for (int i = 0; i < r.nBins; i++) {
          while (j < _newCols.length && _newCols[j] < r.binIds[i])
            j++;
          if (j == _newCols.length || _newCols[j] >= ns)
            break;
          if (r.binIds[i] == _newCols[j]) {
            r.addToArray(glmw.w, gram[j]);
            xy[j] += glmw.w*glmw.z;
            j++;
          }
        }
        while (j < _newCols.length && _newCols[j] < ns)
          j++;
        // nums
        if (r.numIds != null) { // sparse
          for (int i = 0; i < r.nNums; i++) {
            while (j < _newCols.length && _newCols[j] < r.numIds[i])
              j++;
            if (j == _newCols.length) break;
            if (r.numIds[i] == _newCols[j]) {
              double wx = glmw.w * r.numVals[i];
              r.addToArray(wx, gram[j]);
              xy[j] += wx*glmw.z;
              j++;
            }
          }
        } else { // dense
          for (; j < _newCols.length; j++) {
            int id = _newCols[j];
            double x = r.numVals[id - _dinfo.numStart()];
            if(x == 0) continue;
            double wx = glmw.w * x;
            r.addToArray(wx, gram[j]);
            xy[j] += wx*glmw.z;
          }
          assert j == _newCols.length;
        }
      }
      if(rows._sparse && _dinfo._normSub != null){ // adjust for sparse zeros (skipped centering)
        int numstart = Arrays.binarySearch(_newCols,ns);
        if(numstart < 0) numstart = -numstart-1;
        for(int k = 0; k < numstart; ++k){
          int i = _newCols[k];
          double [] row = gram[k];
          for(int j = ns; j < row.length-1; ++j){
            double mean_j = _dinfo.normSub(j-ns);
            double scale_j = _dinfo.normMul(j-ns);
            gram[k][j] = gram[k][j] - mean_j*scale_j*wsum[i];
          }
        }
        for(int k = numstart; k < gram.length; ++k){
          int i = _newCols[k];
          double mean_i = _dinfo.normSub(i-ns);
          double scale_i = _dinfo.normMul(i-ns);
          // categoricals
          for(int j = 0; j < _dinfo.numStart(); ++j){
            gram[k][j]-=mean_i*scale_i*wsum[j];
          }
          //nums
          for(int j = ns; j < gram[k].length-1; ++j){
            double mean_j = _dinfo.normSub(j-ns);
            double scale_j = _dinfo.normMul(j-ns);
            gram[k][j] = gram[k][j] - mean_j*scale_j*wsum[i] - mean_i*scale_i*wsum[j] + mean_i*mean_j*scale_i*scale_j*wsum[wsum.length-1];
          }
          gram[k][gram[k].length-1] -= mean_i*scale_i*wsum[gram[k].length-1];
          xy[k] -= ywsum * mean_i * scale_i;
        }
      }
      _gram = gram;
      _xy = xy;
    }

    public void reduce(GLMIncrementalGramTask gt) {
      ArrayUtils.add(_xy,gt._xy);
      for(int i = 0; i< _gram.length; ++i)
        ArrayUtils.add(_gram[i],gt._gram[i]);
    }
  }
}

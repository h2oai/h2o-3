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
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.MathUtils;
import water.util.MathUtils.BasicStats;

import java.util.Arrays;

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



  static class GLMGenericWeightsTask extends  FrameTask2<GLMGenericWeightsTask> {

    final double [] _beta;
    double _sparseOffset;

    private final GLMWeightsFun _glmw;
    private transient GLMWeights _ws;

    double _likelihood;

    public GLMGenericWeightsTask(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey, double [] beta, GLMWeightsFun glmw) {
      super(cmp, dinfo, jobKey);
      _beta = beta;
      _glmw = glmw;
      assert _glmw._family != Family.multinomial:"Generic glm weights task does not work for family multinomial";
    }

    @Override public void chunkInit(){
      _ws = new GLMWeights();
      if(_sparse) _sparseOffset = GLM.sparseOffset(_beta,_dinfo);
    }
    @Override
    protected void processRow(Row row) {
      double eta = row.innerProduct(_beta) + _sparseOffset;
      _glmw.computeWeights(row.response(0),eta,row.offset,row.weight,_ws);
      row.setOutput(0,_ws.w);
      row.setOutput(1,_ws.z);
      _likelihood += _ws.l;
    }

    @Override public void reduce(GLMGenericWeightsTask gwt) {
      _likelihood += gwt._likelihood;
    }
  }

  static class GLMMultinomialWeightsTask extends  FrameTask2<GLMGenericWeightsTask> {

    final double [] _beta;
    double _sparseOffset;

    double _likelihood;
    final int classId;

    public GLMMultinomialWeightsTask(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey, double [] beta, int cid) {
      super(cmp, dinfo, jobKey);
      _beta = beta;
      classId = cid;
    }

    @Override public void chunkInit(){
      if(_sparse) _sparseOffset = GLM.sparseOffset(_beta,_dinfo);
    }
    @Override
    protected void processRow(Row row) {
      double y = row.response(0);
      double maxRow = row.getOutput(2);
      double etaY = row.getOutput(3);
      double eta = row.innerProduct(_beta) + _sparseOffset;
      if(classId == y){
        etaY = eta;
        row.setOutput(3,eta);
      }
      if(eta > maxRow) {
        maxRow = eta;
        row.setOutput(2,eta);
      }
      double etaExp = Math.exp(eta - maxRow);
      double sumExp = row.getOutput(4) + etaExp;
      double mu = etaExp/sumExp;
      if(mu < 1e-16) mu = 1e-16;
      double d = mu*(1-mu);
      row.setOutput(0,row.weight * d);
      row.setOutput(1,eta + (y-mu)/d); // wz = r.weight * (eta * d + (y-mu));
      _likelihood += row.weight * (etaY - Math.log(sumExp) - maxRow);
    }

    @Override public void reduce(GLMGenericWeightsTask gwt) {
      _likelihood += gwt._likelihood;
    }
  }

  static class GLMBinomialWeightsTask extends  FrameTask2<GLMGenericWeightsTask> {
    final double [] _beta;
    double _sparseOffset;
    double _likelihood;

    public GLMBinomialWeightsTask(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey, double [] beta) {
      super(cmp, dinfo, jobKey);
      _beta = beta;
    }

    @Override public void chunkInit(){
      if(_sparse) _sparseOffset = GLM.sparseOffset(_beta,_dinfo);
    }
    @Override
    protected void processRow(Row row) {
      double y = row.response(0);
      double eta = row.innerProduct(_beta) + _sparseOffset;
      double mu = 1/(Math.exp(-eta) + 1);
      if(mu < 1e-16) mu = 1e-16;
      double d = mu*(1-mu);
      row.setOutput(0,row.weight * d);
      row.setOutput(1,eta + (y-mu)/d); // wz = r.weight * (eta * d + (y-mu));
      _likelihood += row.weight * (MathUtils.y_log_y(y, mu) + MathUtils.y_log_y(1 - y, 1 - mu));
    }

    @Override public void reduce(GLMGenericWeightsTask gwt) {
      _likelihood += gwt._likelihood;
    }
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
        double NA = _dinfo._numMeans[cid];
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
        double NA = _dinfo._numMeans[cid];
        Chunk c = chks[cid+_dinfo._cats];
        double scale = _dinfo._normMul == null?1:_dinfo._normMul[cid];
        if(c.isSparseZero()){
          double g = 0;
          int nVals = c.getSparseDoubles(vals,ids,NA);
          for(int i = 0; i < nVals; ++i)
            g += vals[i]*scale*etas[ids[i]];
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
      Chunk response = chks[chks.length-1];
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
          l += ws[i] * _glmf.likelihood(ys[i], mu);
          double var = _glmf.variance(mu);
          if (var < 1e-6) var = 1e-6;
          es[i] = ws[i] * (mu - ys[i]) / (var * _glmf.linkDeriv(mu));
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
          l += ws[i] * (yr == 0?mu:yr*Math.log(yr/mu) + diff);
          es[i] = ws[i]*diff;
        }
      }
      _likelihood = 2*l;
    }
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
      assert parms._family == Family.binomial && parms._link == Link.logit;
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
        double NA = _dinfo._numMeans[cid];
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
        double NA = _dinfo._numMeans[cid];
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
        _glmf.computeWeights(y, r.innerProduct(_beta) + _sparseOffset, r.offset, r.weight, _w);
        w = _w.w;
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
      double eta = r.innerProduct(_betaNew) + _sparseOffsetNew;
//      double mu = _parms.linkInv(eta);
      _sumsqe += w*(eta - z)*(eta - z);
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

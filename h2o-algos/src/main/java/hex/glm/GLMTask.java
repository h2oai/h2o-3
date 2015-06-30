package hex.glm;

import hex.DataInfo;
import hex.DataInfo.Row;

import hex.FrameTask2;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.gram.Gram;
import hex.glm.GLMModel.GLMParameters.Family;
import jsr166y.CountedCompleter;
import water.H2O.H2OCountedCompleter;
import water.*;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.FrameUtils;

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

 static class YMUTask extends MRTask<YMUTask> {
   double _ymu;
   double _yMin = Double.POSITIVE_INFINITY, _yMax = Double.NEGATIVE_INFINITY;
   long _nobs;
   double _wsum;
   final Vec _fVec; // boolean row filter
   final int _responseId;

   final int _weightId;
   final int _offsetId;

   public YMUTask(DataInfo dinfo, Vec mVec, H2OCountedCompleter cmp){
     super(cmp);
     _fVec = mVec;
     _responseId = dinfo.responseChunkId();
     _weightId = dinfo._weights?dinfo.weightChunkId():-1;
     _offsetId = dinfo._offset?dinfo.offsetChunkId():-1;
   }

   @Override public void setupLocal(){
     _fVec.preWriting();
   }

   @Override public void map(Chunk [] chunks) {
     boolean [] skip = MemoryManager.mallocZ(chunks[0]._len);
     for(int i = 0; i < chunks.length; ++i)
       for(int r = chunks[i].nextNZ(-1); r < chunks[i]._len; r = chunks[i].nextNZ(r))
         skip[r] |= chunks[i].isNA(r);
     Chunk response = chunks[_responseId];
     Chunk weight = _weightId >= 0?chunks[_weightId]:new C0DChunk(1,chunks[0]._len);
     for(int r = 0; r < response._len; ++r) {
       if(skip[r]) continue;
       double w = weight.atd(r);
       if(w == 0) {
         skip[r] = true;
         continue;
       }
       _wsum += w;
       double d = w*response.atd(r);
       assert !Double.isNaN(d);
       assert !Double.isNaN(_ymu+d):"got NaN by adding " + _ymu + " + " + d;
       _ymu += d;
       if(d < _yMin)
         _yMin = d;
       if(d > _yMax)
         _yMax = d;
       _nobs++;
     }
     if(_fVec != null)
       DKV.put(_fVec.chunkKey(chunks[0].cidx()), new CBSChunk(skip));
   }
   @Override public void postGlobal() {
     _ymu /= _wsum;
     Futures fs = new Futures();
     _fVec.postWrite(fs); // we just overwrote the vec
     fs.blockForPending();
   }
   @Override public void reduce(YMUTask ymt) {
     if(_nobs > 0 && ymt._nobs > 0) {
       _wsum += ymt._wsum;
       _ymu += ymt._ymu;
       _nobs += ymt._nobs;
       if(_yMin > ymt._yMin)
         _yMin = ymt._yMin;
       if(_yMax < ymt._yMax)
         _yMax = ymt._yMax;
     } else if (_nobs == 0) {
       _wsum = ymt._wsum;
       _ymu = ymt._ymu;
       _nobs = ymt._nobs;
       _yMin = ymt._yMin;
       _yMax = ymt._yMax;
     }
   }
 }

  static class GLMLineSearchTask extends MRTask<GLMLineSearchTask> {
    final DataInfo _dinfo;
    final double [] _beta;
    final double [] _direction;
    final double _step;
    final int _nSteps;
    final GLMParameters _params;
    final double _reg;
    Vec _rowFilter;
    boolean _useFasterMetrics = false;

    public GLMLineSearchTask(DataInfo dinfo, GLMParameters params, double reg, double [] beta, double [] direction, double step, int nsteps, Vec rowFilter){this(dinfo, params, reg, beta, direction, step, nsteps, rowFilter, null);}
    public GLMLineSearchTask(DataInfo dinfo, GLMParameters params, double reg, double [] beta, double [] direction, double step, int nsteps, Vec rowFilter, CountedCompleter cc) {
      super ((H2OCountedCompleter)cc);
      _dinfo = dinfo;
      _reg = reg;
      _beta = beta;
      _direction = direction;
      _step = step;
      _nSteps = nsteps;
      _params = params;
      _rowFilter = rowFilter;
    }
    public GLMLineSearchTask setFasterMetrics(boolean b){
      _useFasterMetrics = b;
      return this;
    }
    long _nobs;
    double [] _likelihoods; // result

//    private final double beta(int i, int j) {
//      return _beta[j] + _direction[j] * _steps[i];
//    }
    // compute linear estimate by summing contributions for all columns
    // (looping by column in the outer loop to have good access pattern and to exploit sparsity)
    @Override
    public void map(Chunk [] chks) {
      Chunk rowFilter = _rowFilter != null?_rowFilter.chunkForChunkIdx(chks[0].cidx()):null;
      Chunk responseChunk = chks[_dinfo.responseChunkId()];
      boolean[] skip = MemoryManager.mallocZ(chks[0]._len);
      if(rowFilter != null)
        for(int r = 0; r < skip.length; ++r)
          skip[r] = rowFilter.at8(r) == 1;
      double [][] eta = new double[responseChunk._len][_nSteps];
      if(_dinfo._offset) {
        Chunk offsetChunk = chks[_dinfo.offsetChunkId()];
        for (int r = 0; r < eta.length; ++r)
          Arrays.fill(eta[r], offsetChunk.atd(r));
      }
      Chunk weightsChunk = _dinfo._weights?chks[_dinfo.weightChunkId()]:new C0DChunk(1,chks[0]._len);
      double [] beta = _beta;
      double [] pk = _direction;

      // intercept
      for (int r = 0; r < eta.length; ++r) {
        double b = beta[beta.length - 1];
        double t = pk[beta.length - 1];
        for (int j = 0; j < _nSteps; ++j, t *= _step) {
          eta[r][j] += b + t;
        }
      }
      // categoricals
      for(int i = 0; i < _dinfo._cats; ++i) {
        Chunk c = chks[i];
        for(int r = 0; r < c._len; ++r) { // categoricals can not be sparse
          if(skip[r] || c.isNA(r)) {
            skip[r] = true;
            continue;
          }
          int off = _dinfo.getCategoricalId(i,(int)c.at8(r));
          if(off != -1) {
            double t = pk[off];
            double b = beta[off];
            for (int j = 0; j < _nSteps; ++j, t *= _step)
              eta[r][j] += b + t;
          }
        }
      }
      // compute default eta offset for 0s
      final int numStart = _dinfo.numStart();
      double [] off = new double[_nSteps];
      if(_dinfo._normMul != null && _dinfo._normSub != null) {
        for (int i = 0; i < _dinfo._nums; ++i) {
          double b = beta[numStart+i];
          double s = pk[numStart+i];
          double d = _dinfo._normSub[i] * _dinfo._normMul[i];
          for (int j = 0; j < _nSteps; ++j, s *= _step)
            off[j] -= (b + s) * d;
        }
      }
      // non-zero numbers
      for (int i = 0; i < _dinfo._nums; ++i) {
        Chunk c = chks[i + _dinfo._cats];
        for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
          if(skip[r] || c.isNA(r)) {
            skip[r] = true;
            continue;
          }
          double d = c.atd(r);
          if (_dinfo._normMul != null)
            d *= _dinfo._normMul[i];
          double b = beta[numStart+i];
          double s = pk[numStart+i];
          for (int j = 0; j < _nSteps; ++j, s *= _step)
            eta[r][j] += (b + s) * d;
        }
      }
      _likelihoods = MemoryManager.malloc8d(_nSteps);
      for (int r = 0; r < chks[0]._len; ++r) {
        double w = weightsChunk.atd(r);
        if(skip[r] || responseChunk.isNA(r))
          continue;
        _nobs++;
        double y = responseChunk.atd(r);
        double yy = -1 + 2*y;
        for (int i = 0; i < _nSteps; ++i) {
          double e = eta[r][i] + off[i];
          if (_params._family == Family.binomial && _useFasterMetrics) {
            _likelihoods[i] += w*Math.log(1 + Math.exp(-yy * e));
          } else {
            double mu = _params.linkInv(e);
            _likelihoods[i] += w*_params.likelihood(y,mu);
          }
        }
      }
    }
    @Override public void reduce(GLMLineSearchTask glt){
      ArrayUtils.add(_likelihoods,glt._likelihoods);
      _nobs += glt._nobs;
    }
  }
  static class GLMGradientTask extends MRTask<GLMGradientTask> {
    final GLMParameters _params;
    GLMValidation _val;
    double _currentLambda;
    final double [] _beta;
    final protected DataInfo _dinfo;
    final double _reg;
    public double [] _gradient;
    public double _likelihood;
    protected transient boolean [] _skip;
    boolean _validate;
    Vec _rowFilter;
    long _nobs;
    double _wsum;
    double _ymu;

    public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, Vec rowFilter){this(dinfo,params, lambda, beta,reg,rowFilter, null);}
    public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, Vec rowFilter, H2OCountedCompleter cc){
      super(cc);
      _dinfo = dinfo;
      _params = params;
      _beta = beta;
      _reg = reg;
      _currentLambda = lambda;
      _rowFilter = rowFilter;
    }

    public GLMGradientTask setValidate(double ymu, boolean validate) {
      _ymu = ymu;
      _validate = validate;
      return this;
    }

    protected void goByRows(Chunk [] chks, boolean [] skp){
      Row row = _dinfo.newDenseRow();
      double [] g = _gradient;
      double [] b = _beta;
      for(int rid = 0; rid < chks[0]._len; ++rid) {
        if(skp[rid]) continue;
        row = _dinfo.extractDenseRow(chks, rid, row);
        if(row.bad || row.weight == 0) continue;
        _nobs++;
        _wsum += row.weight;
        double eta = row.innerProduct(b) + row.offset;
        double mu = _params.linkInv(eta);
        _val.add(row.response(0), mu, row.weight, row.offset);
        _likelihood += row.weight*_params.likelihood(row.response(0), mu);
        double var = _params.variance(mu);
        if(var < 1e-6) var = 1e-6; // to avoid numerical problems with 0 variance
        double gval = (mu-row.response(0)) / (var * _params.linkDeriv(mu));
        // categoricals
        for(int i = 0; i < row.nBins; ++i)
          g[row.binIds[i]] += gval;
        int off = _dinfo.numStart();
        // numbers
        for(int j = 0; j < _dinfo._nums; ++j)
          g[j + off] += row.numVals[j] * gval;
        // intercept
        if(_dinfo._intercept)
          g[g.length-1] += gval;
      }
    }
    @Override
    public void postGlobal(){
      ArrayUtils.mult(_gradient,_reg);
      for(int j = 0; j < _beta.length - (_dinfo._intercept?1:0); ++j)
        _gradient[j] += _currentLambda * _beta[j];
    }

    // compute linear estimate by summing contributions for all columns
    // (looping by column in the outer loop to have good access pattern and to exploit sparsity)
    protected final double [] computeEtaByCols(Chunk [] chks, boolean [] skip) {
      double [] eta = MemoryManager.malloc8d(chks[0]._len);
      if(_dinfo._intercept)
        Arrays.fill(eta,_beta[_beta.length-1]);
      if(_dinfo._offset) {
        for (int i = 0; i < eta.length; ++i) {
          if(!skip[i]) {
            eta[i] += chks[_dinfo.offsetChunkId()].atd(i);
            if (Double.isNaN(eta[i]))
              skip[i] = true;
          }
        }
      }
      double [] b = _beta;
      // do categoricals first
      for(int i = 0; i < _dinfo._cats; ++i) {
        Chunk c = chks[i];
        for(int r = 0; r < c._len; ++r) { // categoricals can not be sparse
          if(skip[r] || c.isNA(r)) {
            skip[r] = true;
            continue;
          }
          int off = _dinfo.getCategoricalId(i,(int)c.at8(r));
          if(off != -1)
            eta[r] += b[off];
        }
      }
      final int numStart = _dinfo.numStart();
      // compute default eta offset for 0s
      if(_dinfo._normMul != null && _dinfo._normSub != null) {
        double off = 0;
        for (int i = 0; i < _dinfo._nums; ++i)
          off -= b[numStart + i] * _dinfo._normSub[i] * _dinfo._normMul[i];
        for(int r = 0; r < chks[0]._len; ++r)
          eta[r] += off;
      }
      // now numerics
      for (int i = 0; i < _dinfo._nums; ++i) {
        Chunk c = chks[i + _dinfo._cats];
        for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
          if(skip[r] || c.isNA(r)) {
            skip[r] = true;
            continue;
          }
          double d = c.atd(r);
          if (_dinfo._normMul != null)
            d *= _dinfo._normMul[i];
          eta[r] += b[numStart + i] * d;
        }
      }
      return eta;
    }

    protected void goByCols(Chunk [] chks, boolean [] skp){
      int numStart = _dinfo.numStart();
      double  [] eta = computeEtaByCols(chks, skp);
      double  [] b = _beta;
      double  [] g = _gradient;
      Chunk offsetChunk = _dinfo._offset?chks[_dinfo.offsetChunkId()]:new C0DChunk(0,chks[0]._len);
      Chunk weightChunk = _dinfo._weights ?chks[_dinfo.weightChunkId()]:new C0DChunk(1,chks[0]._len);
      Chunk responseChunk = chks[_dinfo.responseChunkId()];
      double eta_sum = 0;
      // compute the predicted mean and variance and gradient for each row
      for(int r = 0; r < chks[0]._len; ++r){
        if(skp[r] || responseChunk.isNA(r))
          continue;
        double w = weightChunk.atd(r);
        if(w == 0 || Double.isNaN(w))
          continue;
        _nobs++;
        _wsum += w;
        double y = responseChunk.atd(r);
        double mu = _params.linkInv(eta[r]);
        _val.add(y, mu, w, offsetChunk.atd(r));
        _likelihood += w * _params.likelihood(y, mu);
        double var = _params.variance(mu);
        if(var < 1e-6) var = 1e-6; // to avoid numerical problems with 0 variance
        eta[r] = w * (mu-y) / (var * _params.linkDeriv(mu));
        eta_sum += eta[r];
      }
      // finally go over the columns again and compute gradient for each column
      // first handle eta offset and intercept
      if(_dinfo._intercept)
        g[g.length-1] = eta_sum;
      if(_dinfo._normMul != null && _dinfo._normSub != null)
        for(int i = 0; i < _dinfo._nums; ++i)
          g[numStart + i] = -_dinfo._normSub[i]*_dinfo._normMul[i]*eta_sum;
      // categoricals
      for(int i = 0; i < _dinfo._cats; ++i) {
        Chunk c = chks[i];
        for(int r = 0; r < c._len; ++r) { // categoricals can not be sparse
          if(skp[r]) continue;
          int off = _dinfo.getCategoricalId(i,(int)chks[i].at8(r));
          if(off != -1)
            g[off] += eta[r];
        }
      }
      // numerics
      for (int i = 0; i < _dinfo._nums; ++i) {
        Chunk c = chks[i + _dinfo._cats];
        for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
          if(skp[r] || c.isNA(r))
            continue;
          double d = c.atd(r);
          if (_dinfo._normMul != null)
            d = d*_dinfo._normMul[i];
          g[numStart + i] += eta[r] * d;
        }
      }
      _skip = skp;
    }

    private boolean mostlySparse(Chunk [] chks){
      int cnt = 0;
      for(Chunk chk:chks)
        if(chk.isSparse())
          ++cnt;
      return cnt >= chks.length >> 1;
    }

    private boolean _forceRows;
    private boolean _forceCols;

    public GLMGradientTask forceColAccess() {
      _forceCols = true;
      _forceRows = false;
      return this;
    }
    public GLMGradientTask forceRowAccess() {
      _forceCols = false;
      _forceRows = true;
      return this;
    }


    public void map(Chunk [] chks){
      int rank = 0;
      for(int i = 0; i < _beta.length; ++i)
        if(_beta[i] != 0)
          ++rank;
      _gradient = MemoryManager.malloc8d(_beta.length);
      String [] domain = _dinfo._adaptedFrame.lastVec().domain();
      if(domain == null && _params._family == Family.binomial)
        domain = new String[]{"0","1"}; // special hard-coded case for binomial on binary col
      _val = new GLMValidation(domain,_params._intercept, _ymu, _params,rank,0,_validate);
      boolean [] skp = MemoryManager.mallocZ(chks[0]._len);
      if(_rowFilter != null) {
        Chunk c = _rowFilter.chunkForChunkIdx(chks[0].cidx());
        for(int r = 0; r < chks[0]._len; ++r)
          skp[r] = c.at8(r) == 1;
      }
      if(_forceCols || (!_forceRows && (chks.length >= 100 || mostlySparse(chks))))
        goByCols(chks, skp);
      else
        goByRows(chks, skp);
      // apply reg
    }
    public void reduce(GLMGradientTask grt) {
      _likelihood += grt._likelihood;
      _nobs += grt._nobs;
      _wsum += grt._wsum;
      _val.reduce(grt._val);
      ArrayUtils.add(_gradient, grt._gradient);
    }
  }


  static class GLMWeightsTask extends MRTask<GLMWeightsTask> {
    final GLMParameters _params;
    GLMWeightsTask(GLMParameters params){_params = params;}

    @Override public void map(Chunk [] chks) {
      Chunk yChunk = chks[0];
      Chunk zChunk = chks[1];
      Chunk wChunk = chks[2];
      Chunk eChunk = chks[3];
      for(int i = 0; i < yChunk._len; ++i) {
        double y = yChunk.atd(i);
        double eta = eChunk.atd(i);
        double mu = _params.linkInv(eta);
        double var = Math.max(1e-6, _params.variance(mu)); // avoid numerical problems with 0 variance
        double d = _params.linkDeriv(mu);
        zChunk.set(i,eta + (y-mu)*d);
        wChunk.set(i,1.0/(var*d*d));
      }
    }
    @Override public void reduce(GLMWeightsTask gwt) {}
  }


  /**
   * Tassk with simplified gradient computation for logistic regression (and least squares)
   * Looks like
   */
  public static class LBFGS_LogisticGradientTask extends GLMGradientTask {

    public LBFGS_LogisticGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, Vec rowFilter) {
      super(dinfo, params, lambda, beta, reg, rowFilter);
    }

    @Override   protected void goByRows(Chunk [] chks, boolean [] skp){
      Row row = _dinfo.newDenseRow();
      double [] g = _gradient;
      double [] b = _beta;
      for(int rid = 0; rid < chks[0]._len; ++rid) {
        if(skp[rid])continue;
        row = _dinfo.extractDenseRow(chks, rid, row);
        double y = -1 + 2*row.response(0);
        if(row.bad) continue;
        ++_nobs;
        double eta = row.innerProduct(b) + row.offset;
        double gval;
        double d = 1 + Math.exp(-y * eta);
        _likelihood += row.weight*Math.log(d);
        gval = row.weight*-y*(1-1.0/d);
        // categoricals
        for(int i = 0; i < row.nBins; ++i)
          g[row.binIds[i]] += gval;
        int off = _dinfo.numStart();
        // numbers
        for(int j = 0; j < _dinfo._nums; ++j)
          g[j + off] += row.numVals[j] * gval;
        // intercept
        if(_dinfo._intercept)
          g[g.length-1] += gval;
      }
    }

    @Override protected void goByCols(Chunk [] chks, boolean [] skp){
      int numStart = _dinfo.numStart();
      double  [] eta = computeEtaByCols(chks,skp);
      double  [] g = _gradient;
      Chunk offsetChunk = null;
      int nxs = chks.length-1; // -1 for response
      if(_dinfo._offset) {
        nxs -= 1;
        offsetChunk = chks[nxs];
      }
      Chunk responseChunk = chks[nxs];
      Chunk weightsChunk = _dinfo._weights?chks[_dinfo.weightChunkId()]:new C0DChunk(1,chks[0]._len);

      double eta_sum = 0;
      // compute the predicted mean and variance and gradient for each row
      for(int r = 0; r < chks[0]._len; ++r){
        double w = weightsChunk.atd(r);
        if(skp[r] || responseChunk.isNA(r))
          continue;
        ++_nobs;
        double off = (_dinfo._offset?offsetChunk.atd(r):0);
        double e = eta[r]  + off;
        switch(_params._family) {
          case gaussian:
            double diff = e - responseChunk.atd(r);
            _likelihood += w*diff*diff;
            eta[r] = diff;
            break;
          case binomial:
            double y = -1 + 2*responseChunk.atd(r);
            double d = 1 + Math.exp(-y * e);
            _likelihood += w*Math.log(d);
            eta[r] = w * -y * (1 - 1.0 / d);
            break;
          default:
            throw H2O.unimpl();
        }
        eta_sum += eta[r];
      }
      // finally go over the columns again and compute gradient for each column
      // first handle eta offset and intercept
      if(_dinfo._intercept)
        g[g.length-1] = eta_sum;
      if(_dinfo._normMul != null && _dinfo._normSub != null)
        for(int i = 0; i < _dinfo._nums; ++i)
          g[numStart + i] = -_dinfo._normSub[i]*_dinfo._normMul[i]*eta_sum;
      // categoricals
      for(int i = 0; i < _dinfo._cats; ++i) {
        Chunk c = chks[i];
        for(int r = 0; r < c._len; ++r) { // categoricals can not be sparse
          if(skp[r]) continue;
          int off = _dinfo.getCategoricalId(i,(int)chks[i].at8(r));
          if(off != -1)
            g[off] += eta[r];
        }
      }
      // numerics
      for (int i = 0; i < _dinfo._nums; ++i) {
        Chunk c = chks[i + _dinfo._cats];
        for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
          if(skp[r] || c.isNA(r))
            continue;
          double d = c.atd(r);
          if (_dinfo._normMul != null)
            d = d*_dinfo._normMul[i];
          g[numStart + i] += eta[r] * d;
        }
      }
      _skip = skp;
    }
  }



  public static class GLMCoordinateDescentTask extends MRTask<GLMCoordinateDescentTask> {
    final double [] _betaUpdate;
    final double [] _beta;
    final double _xOldSub;
    final double _xOldMul;
    final double _xNewSub;
    final double _xNewMul;

    double [] _xy;

    public GLMCoordinateDescentTask(double [] betaUpdate, double [] beta, double xOldSub, double xOldMul, double xNewSub, double xNewMul) {
      _betaUpdate = betaUpdate;
      _beta = beta;
      _xOldSub = xOldSub;
      _xOldMul = xOldMul;
      _xNewSub = xNewSub;
      _xNewMul = xNewMul;
    }

    public void map(Chunk [] chks) {
      Chunk xOld = chks[0];
      Chunk xNew = chks[1];
      if(xNew.vec().isEnum()){
        _xy = MemoryManager.malloc8d(xNew.vec().domain().length);
      } else
      _xy = new double[1];
      Chunk eta = chks[2];
      Chunk weights = chks[3];
      Chunk res = chks[4];
      for(int i = 0; i < eta._len; ++i) {
        double w = weights.atd(i);
        double e = eta.atd(i);
        if(_betaUpdate != null) {
          if (xOld.vec().isEnum()) {
            int cid = (int) xOld.at8(i);
            e = +_betaUpdate[cid];
          } else
            e += _betaUpdate[0] * (xOld.atd(i) - _xOldSub) * _xOldMul;
          eta.set(i, e);
        }
        int cid = 0;
        double x = w;
        if(xNew.vec().isEnum()) {
          cid = (int) xNew.at8(i);
          e -= _beta[cid];
        } else {
          x = (xNew.atd(i) - _xNewSub) * _xNewMul;
          e -= _beta[0] * x;
          x *= w;
        }
        _xy[cid] += x * (res.atd(i) - e);
      }
    }
    @Override public void reduce(GLMCoordinateDescentTask t) {
      ArrayUtils.add(_xy, t._xy);
    }
  }
  /**
   * One iteration of glm, computes weighted gram matrix and t(x)*y vector and t(y)*y scalar.
   *
   * @author tomasnykodym
   */
  public static class GLMIterationTask extends FrameTask2<GLMIterationTask> {
    final GLMParameters _params;
    final double [] _beta;
    protected Gram  _gram;
    double [] _xy;
    double    _yy;
    GLMValidation _val; // validation of previous model
    final double _ymu;
    long _nobs;
    final boolean _validate;
    int [] _ti;
    public double _likelihood;
    final double _lambda;

    public  GLMIterationTask(Key jobKey, DataInfo dinfo, double lambda, GLMModel.GLMParameters glm, boolean validate, double [] beta, double ymu, Vec rowFilter, H2OCountedCompleter cmp) {
      super(cmp,dinfo,jobKey,rowFilter);
      _params = glm;
      _beta = beta;
      _ymu = ymu;
      _validate = validate;
      _lambda = lambda;
    }

    @Override public boolean handlesSparseData(){return true;}

    @Override public double sparseOffset(){
      double etaOffset = 0;
      if(_dinfo._normMul != null && _dinfo._normSub != null && _beta != null) {
        int ns = _dinfo.numStart();
        for (int i = 0; i < _dinfo._nums; ++i)
          etaOffset -= _beta[i + ns] * _dinfo._normSub[i] * _dinfo._normMul[i];
      }
      return etaOffset;
    }

    @Override
    public void chunkInit() {
      // initialize
      _gram = new Gram(_dinfo.fullN(), _dinfo.largestCat(), _dinfo._nums, _dinfo._cats,true);
      // public GLMValidation(Key dataKey, double ymu, GLMParameters glm, int rank, float [] thresholds){
      if(_validate) {
        int rank = 0;
        if(_beta != null) for(double d:_beta) if(d != 0)++rank;
        String [] domain = _dinfo._adaptedFrame.lastVec().domain();
        if(domain == null && _params._family == Family.binomial)
          domain = new String[]{"0","1"}; // special hard-coded case for binomial on binary col
        _val = new GLMValidation(domain, true, _ymu, _params, rank, .5, true); // todo pass correct threshold
      }
      _xy = MemoryManager.malloc8d(_dinfo.fullN()+1); // + 1 is for intercept
      if(_params._family == Family.binomial && _validate){
        _ti = new int[2];
      }
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
        eta = r.innerProduct(_beta);
        mu = _params.linkInv(eta + r.offset);
        var = Math.max(1e-6, _params.variance(mu)); // avoid numerical problems with 0 variance
        d = _params.linkDeriv(mu);
        z = eta + (y-mu)*d;
        w = r.weight/(var*d*d);
      }
      if(_validate)
        _val.add(y, mu, r.weight, r.offset);
      _likelihood += r.weight*_params.likelihood(y,mu);
      assert w >= 0|| Double.isNaN(w) : "invalid weight " + w; // allow NaNs - can occur if line-search is needed!
      double wz = w * z;
      _yy += wz * z;
      for(int i = 0; i < r.nBins; ++i)
        _xy[r.binIds[i]] += wz;
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
    public void reduce(GLMIterationTask git){
      ArrayUtils.add(_xy, git._xy);
      _gram.add(git._gram);
      _yy += git._yy;
      _nobs += git._nobs;
      if (_validate) _val.reduce(git._val);
      _likelihood += git._likelihood;
      super.reduce(git);
    }

    @Override protected void postGlobal(){
      if(_sparse && _dinfo._normSub != null) { // need to adjust gram for missing centering!
        int ns = _dinfo.numStart();
        int interceptIdx = _xy.length-1;
        double [] interceptRow = _gram._xx[interceptIdx-_gram._diagN];
        double nobs = interceptRow[interceptRow.length-1]; // weighted nobs
        for(int i = ns; i < _dinfo.fullN(); ++i) {
          double iMean = _dinfo._normSub[i - ns] * _dinfo._normMul[i - ns];
          for (int j = 0; j < ns; ++j)
            _gram._xx[i - _gram._diagN][j] -= interceptRow[j]*iMean;
          for (int j = ns; j <= i; ++j) {
            double jMean = _dinfo._normSub[j - ns] * _dinfo._normMul[j - ns];
            _gram._xx[i - _gram._diagN][j] -=  interceptRow[i]*jMean + interceptRow[j]*iMean - nobs * iMean * jMean;
          }
        }
        if(_dinfo._intercept) { // do the intercept row
          for(int j = ns; j < _dinfo.fullN(); ++j)
            interceptRow[j] -= nobs * _dinfo._normSub[j-ns]*_dinfo._normMul[j-ns];
        }
        // and the xy vec as well
        for(int i = ns; i < _dinfo.fullN(); ++i)
          _xy[i] -= _xy[_xy.length-1]*_dinfo._normSub[i-ns]*_dinfo._normMul[i-ns];
      }
      if(_val != null){
        _val.computeAIC();
      }
    }

    public boolean hasNaNsOrInf() {
      return ArrayUtils.hasNaNsOrInfs(_xy) || _gram.hasNaNsOrInfs();
    }
  }

//  public static class GLMValidationTask<T extends GLMValidationTask<T>> extends MRTask<T> {
//    protected final GLMModel _model;
//    protected GLMValidation _res;
//    public final double _lambda;
//    public boolean _improved;
//    Key _jobKey;
//    public static Key makeKey(){return Key.make("__GLMValidation_" + Key.make().toString());}
//    public GLMValidationTask(GLMModel model, double lambda){this(model,lambda,null);}
//    public GLMValidationTask(GLMModel model, double lambda, H2OCountedCompleter completer){super(completer); _lambda = lambda; _model = model;}
//    @Override public void map(Chunk[] chunks){
//      _res = new GLMValidation(null,_model._ymu,_model._parms,_model.rank(_lambda));
//      final int nrows = chunks[0]._len;
//      double [] row   = MemoryManager.malloc8d(_model._output._names.length);
//      float  [] preds = MemoryManager.malloc4f(_model._parms._family == Family.binomial?3:1);
//      OUTER:
//      for(int i = 0; i < nrows; ++i){
//        if(chunks[chunks.length-1].isNA(i))continue;
//        for(int j = 0; j < chunks.length-1; ++j){
//          if(chunks[j].isNA(i))continue OUTER;
//          row[j] = chunks[j].atd(i);
//        }
//        _model.score0(row, preds);
//        double response = chunks[chunks.length-1].atd(i);
//        _res.add(response, _model._parms._family == Family.binomial?preds[2]:preds[0]);
//      }
//    }
//    @Override public void reduce(GLMValidationTask gval){_res.add(gval._res);}
//    @Override public void postGlobal(){
//      _res.computeAIC();
//      _res.computeAUC();
//    }
//  }
  // use general score to reduce number of possible different code paths
//  public static class GLMXValidationTask extends GLMValidationTask<GLMXValidationTask>{
//    protected final GLMModel [] _xmodels;
//    protected GLMValidation [] _xvals;
//    long _nobs;
//    final float [] _thresholds;
//    public static Key makeKey(){return Key.make("__GLMValidation_" + Key.make().toString());}
//
//    public GLMXValidationTask(GLMModel mainModel,double lambda, GLMModel [] xmodels, float [] thresholds){this(mainModel,lambda,xmodels,thresholds,null);}
//    public GLMXValidationTask(GLMModel mainModel,double lambda, GLMModel [] xmodels, float [] thresholds, final H2OCountedCompleter completer){
//      super(mainModel, lambda,completer);
//      _xmodels = xmodels;
//      _thresholds = thresholds;
//    }
//    @Override public void map(Chunk [] chunks) {
//      long gid = chunks[0].start();
//      _xvals = new GLMValidation[_xmodels.length];
//      for(int i = 0; i < _xmodels.length; ++i)
//        _xvals[i] = new GLMValidation(null,_xmodels[i]._ymu,_xmodels[i]._parms,_xmodels[i]._output.rank(),_thresholds);
//      final int nrows = chunks[0]._len;
//      double [] row   = MemoryManager.malloc8d(_xmodels[0]._output._names.length);
//      float  [] preds = MemoryManager.malloc4f(_xmodels[0]._parms._family == Family.binomial?3:1);
//      OUTER:
//      for(int i = 0; i < nrows; ++i){
//        if(chunks[chunks.length-1].isNA(i))continue;
//        for(int j = 0; j < chunks.length-1; ++j) {
//          if(chunks[j].isNA(i))continue OUTER;
//          row[j] = chunks[j].atd(i);
//        }
//        ++_nobs;
//        final int mid = (int)((i + gid)  % _xmodels.length);
//        final GLMModel model = _xmodels[mid];
//        final GLMValidation val = _xvals[mid];
//        model.score0(row, preds);
//        double response = chunks[chunks.length-1].at8(i);
//        val.add(response, model._parms._family == Family.binomial?preds[2]:preds[0]);
//      }
//    }
//    @Override public void reduce(GLMXValidationTask gval){
//      _nobs += gval._nobs;
//      for(int i = 0; i < _xvals.length; ++i)
//        _xvals[i].add(gval._xvals[i]);}
//
//    @Override public void postGlobal() {
//      H2OCountedCompleter cmp = (H2OCountedCompleter)getCompleter();
//      if(cmp != null)cmp.addToPendingCount(_xvals.length + 1);
//      for (int i = 0; i < _xvals.length; ++i) {
//        _xvals[i].computeAIC();
//        _xvals[i].computeAUC();
//        _xvals[i].nobs = _nobs - _xvals[i].nobs;
//        GLMModel.setXvalidation(cmp, _xmodels[i]._key, _lambda, _xvals[i]);
//      }
//      GLMModel.setXvalidation(cmp, _model._key, _lambda, new GLMXValidation(_model, _xmodels, _xvals, _lambda, _nobs,_thresholds));
//    }
//  }
}

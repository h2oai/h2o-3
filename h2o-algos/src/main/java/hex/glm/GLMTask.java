package hex.glm;

import hex.DataInfo;
import hex.DataInfo.Row;

import hex.DataInfo.Rows;
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

   double _yMin = Double.POSITIVE_INFINITY, _yMax = Double.NEGATIVE_INFINITY;
   long _nobs;
   double _wsum;

   final int _responseId;

   final int _weightId;
   final int _offsetId;
   final int _nums; // number of numeric columns
   final int _numOff;

   final boolean _comupteWeightedSigma;

   double [] _xsum; // weighted sum of x
   double [] _xxsum; // weighted sum of x^2
   double [] _yMu;
   final int _nClasses;

   public YMUTask(DataInfo dinfo, int nclasses, boolean computeWeightedSigma, H2OCountedCompleter cmp){
     super(cmp);
     _nums = dinfo._nums;
     _numOff = dinfo._cats;
     _responseId = dinfo.responseChunkId();
     _weightId = dinfo._weights?dinfo.weightChunkId():-1;
     _offsetId = dinfo._offset?dinfo.offsetChunkId():-1;
     _nClasses = nclasses;
     _comupteWeightedSigma = computeWeightedSigma;
   }
   @Override public void setupLocal(){}

//   public double _wY; // (Weighted) sum of the response
//   public double _wYY; // (Weighted) sum of the squared response
//
//   public  double weightedSigma() {
////      double sampleCorrection = _count/(_count-1); //sample variance -> depends on the number of ACTUAL ROWS (not the weighted count)
//     double sampleCorrection = 1; //this will make the result (and R^2) invariant to globally scaling the weights
//     return _count <= 1 ? 0 : Math.sqrt(sampleCorrection*(_wYY/_wcount - (_wY*_wY)/(_wcount*_wcount)));
//   }

   @Override public void map(Chunk [] chunks) {
     _yMu = new double[_nClasses > 2?_nClasses:1];
     boolean [] good = MemoryManager.mallocZ(chunks[0]._len);
     Arrays.fill(good,true);
     Chunk weight = chunks[_weightId];
     for(int i = 0; i < chunks.length; ++i) {
       for (int r = chunks[i].nextNZ(-1); r < chunks[i]._len; r = chunks[i].nextNZ(r)) {
         if (weight.atd(r) != 0 && chunks[i].isNA(r))
           good[r] = false;
       }
     }
     Chunk response = chunks[_responseId];
     if(_comupteWeightedSigma) {
       _xsum = MemoryManager.malloc8d(_nums);
       _xxsum = MemoryManager.malloc8d(_nums);
     }
     for(int r = 0; r < response._len; ++r) {
       double w = weight.atd(r);
       if(!good[r] || w == 0) continue;
       if(_comupteWeightedSigma) {
         for(int i = 0; i < _nums; ++i) {
           double d = chunks[i+_numOff].atd(r);
           _xsum[i]  += w*d;
           _xxsum[i] += w*d*d;
         }
       }
       _wsum += w;
       double d = w*response.atd(r);
       assert !Double.isNaN(d);
       if(_nClasses > 2)
         _yMu[(int)d] += 1;
       else
        _yMu[0] += d;
       if(d < _yMin)
         _yMin = d;
       if(d > _yMax)
         _yMax = d;
       _nobs++;
     }
     boolean all_good = true;
     for(boolean b:good)all_good &= b;
     if(!all_good) {

       if (weight instanceof C0DChunk && weight.atd(0) == 1) // shortcut for h2o-made binary weights
         DKV.put(weight.vec().chunkKey(chunks[0].cidx()), new CBSChunk(good));
       else {
         for(int i = 0; i  < good.length; ++i) // already got weights, need to set the zeros
           if(!good[i]) weight.set(i,0);
       }
     }
   }
   @Override public void postGlobal() {
     ArrayUtils.mult(_yMu,1.0/_wsum);
     Futures fs = new Futures();
//     _fVec.postWrite(fs); // we just overwrote the vec
     fs.blockForPending();
   }
   @Override public void reduce(YMUTask ymt) {
     if(_nobs > 0 && ymt._nobs > 0) {
       _wsum += ymt._wsum;
       ArrayUtils.add(_yMu,ymt._yMu);
       _nobs += ymt._nobs;
       if(_yMin > ymt._yMin)
         _yMin = ymt._yMin;
       if(_yMax < ymt._yMax)
         _yMax = ymt._yMax;
       if(_comupteWeightedSigma) {
         ArrayUtils.add(_xsum, ymt._xsum);
         ArrayUtils.add(_xxsum, ymt._xxsum);
       }
     } else if (_nobs == 0) {
       _wsum = ymt._wsum;
       _yMu = ymt._yMu;
       _nobs = ymt._nobs;
       _yMin = ymt._yMin;
       _yMax = ymt._yMax;
       _xsum = ymt._xsum;
       _xxsum = ymt._xxsum;
     }
   }
 }





  static class GLMLineSearchTask extends MRTask<GLMLineSearchTask> {
    final DataInfo _dinfo;
    final double [] _beta;
    final double [][] _betaMultinomial;
    final int _c;
    final double [] _direction;
    final double _step;
    final double _initStep;
    final int _nSteps;
    final GLMParameters _params;
    boolean _useFasterMetrics = false;

    public GLMLineSearchTask(DataInfo dinfo, GLMParameters params, double [] beta, double [] direction, double initStep, double step, int nsteps, CountedCompleter cc) {
      super ((H2OCountedCompleter)cc);
      _dinfo = dinfo;
      _beta = beta;
      _betaMultinomial = null;
      _direction = direction;
      _step = step;
      _nSteps = nsteps;
      _params = params;

      _initStep = initStep;
      _c = -1;
    }

    long _nobs;
    double [] _likelihoods; // result

    @Override
    public void map(Chunk [] chks) {

      Chunk responseChunk = chks[_dinfo.responseChunkId()];
      boolean[] skip = MemoryManager.mallocZ(chks[0]._len);

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
        double t = pk[beta.length - 1] * _initStep;
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
          int off = _dinfo.getCategoricalId(i,(int)c.at8(r)); // get pos in beta vector.
          if(off != -1) {
            double t = pk[off] * _initStep;
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
          double s = pk[numStart+i] * _initStep;
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
          if(d == 0) continue;
          if (_dinfo._normMul != null)
            d *= _dinfo._normMul[i];
          double b = beta[numStart+i];
          double s = pk[numStart+i] * _initStep;
          for (int j = 0; j < _nSteps; ++j, s *= _step)
            eta[r][j] += (b + s) * d;
        }
      }
      _likelihoods = MemoryManager.malloc8d(_nSteps);
      for (int r = 0; r < chks[0]._len; ++r) {
        double w = weightsChunk.atd(r);
        if(w == 0 || responseChunk.isNA(r))
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


  static class GLMMultinomialLineSearchTask extends MRTask<GLMMultinomialLineSearchTask> {
    private final DataInfo _dinfo;
    public final double _initialStep;
    public final double _stepDec;
    public final int    _nSteps;
    private double [][] _betaBase;
    private double [][] _direction2D;
    private double []   _direction1D;

    final int _c;
    public long _nobs;
    // output
    double [] _likelihoods;

    static double[][] reshapeAry(double[] ary, int n) {
      int d = ary.length/n;
      if(d*n != ary.length)
        throw new IllegalArgumentException("Array length is not multiple of n");;

      double [][] res = new double[d][n];
      int off = 0;
      for(int i = 0; i < d; ++i)
        for(int j = 0; j < n; ++j)
          res[i][j] = ary[off++];
      return res;
    }

    GLMMultinomialLineSearchTask(H2OCountedCompleter cc, DataInfo dinfo, double [] beta, double [] direction, double initialStep, double stepDec, int nSteps) {
      this(cc,dinfo,reshapeAry(beta,dinfo.fullN()+1),reshapeAry(direction,dinfo.fullN()+1),initialStep, stepDec, nSteps);
    }

    GLMMultinomialLineSearchTask(H2OCountedCompleter cc, DataInfo dinfo, double [][] beta, double [] direction, int c, double initialStep, double stepDec, int nSteps) {
      super(cc);
      _dinfo = dinfo;
      _betaBase = beta;
      _direction1D = direction;
      _direction2D = null;
      _c = c;
      _initialStep = initialStep;
      _stepDec = stepDec;
      _nSteps = nSteps;
    }
    GLMMultinomialLineSearchTask(H2OCountedCompleter cc, DataInfo dinfo, double [][] beta, double [][] direction, double initialStep, double stepDec, int nSteps) {
      super(cc);
      _dinfo = dinfo;
      _betaBase = beta;
      _direction2D = direction;
      _direction1D = null;
      _initialStep = initialStep;
      _stepDec = stepDec;
      _nSteps = nSteps;
      _c = -1;
    }

    public void map(Chunk [] chks) {
      double t = _initialStep;
      _nobs = 0;
      double [][] beta  = _betaBase.clone();
      for(int i = 0; i < beta.length; ++i)
        beta[i] = beta[i].clone();
      Rows rows = _dinfo.rows(chks);

      double [] etas = new double[_betaBase.length];
      double [] etaOffsets = new double [etas.length];


      double [] exps = new double[_betaBase.length+1];
      double [] likelihoods = new double[_nSteps];

      for(int i = 0; i < _nSteps; i++) {
        for(int j = 0; j < _betaBase.length; ++j) {
          double [] base = _betaBase[j];
          double [] b = beta[j];
          if(_direction2D != null) {
            double [] d = _direction2D[j];
            for(int k = 0; k < base.length; ++k)
              b[k] = base[k] + t*d[k];
          } else if(j == _c){
            for(int k = 0; k < base.length; ++k)
              b[k] = base[k] + t*_direction1D[k];
          }
          if(rows._sparse)
            etaOffsets[j] = GLM.sparseOffset(b,_dinfo);
        }
        for(int j = 0; j < rows._nrows; ++j) {
          Row row = rows.row(j);
          if(i == 0)++_nobs;
          double logSumExp = computeMultinomialEtas(row,beta,etas,etaOffsets,exps);
          likelihoods[i] -= row.weight * (etas[(int)row.response(0)] - logSumExp);
        }
        t *= _stepDec;
      }
      _likelihoods = likelihoods;
      _betaBase = null;
      _direction1D = null;
      _direction2D = null;
    }
    public void reduce(GLMMultinomialLineSearchTask glmt) {
      ArrayUtils.add(_likelihoods,glmt._likelihoods);
      _nobs += glmt._nobs;
    }
  }

  static double  computeMultinomialEtas(Row row, double [][]beta, final double [] etas, double [] etaOffsets, double [] exps) {
    double maxRow = 0;
    for (int c = 0; c < beta.length; ++c) {
      double e = etaOffsets[c] + row.innerProduct(beta[c]);
      if (e > maxRow) maxRow = e;
      etas[c] = e;
    }
    double sumExp = 0;
    for(int c = 0; c < beta.length; ++c) {
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

  static class GLMMultinomialGradientTask extends MRTask<GLMMultinomialGradientTask> {
    final double [][] _beta;
    final DataInfo _dinfo;
    final double _currentLambda;
    final double _reg;
    final double [] _ymu;
    double [] _gradient;
    long _nobs;
    double _wsum;
    double _likelihood;
    GLMValidation _val;
    final boolean _validate;

    public GLMMultinomialGradientTask(DataInfo dinfo, double lambda, double [] ymu, double[][] beta, double reg,boolean validate, H2OCountedCompleter cmp) {
      super(cmp);
      _dinfo = dinfo;
      _currentLambda = lambda;
      _reg = reg;
      _validate = validate;
      _beta = beta;
      _ymu = ymu;
    }

    private final void processRow(Row row, double [][] beta, final double [] etas, double [] etaOffsets, final double [] exps) {
      int y = (int)row.response(0);
      assert y == row.response(0);
      double logSumExp = computeMultinomialEtas(row, beta, etas, etaOffsets, exps);
      int P = _dinfo.fullN()+1;
      _likelihood -= row.weight * (etas[(int)row.response(0)] - logSumExp);
      _val.add(y,exps, row.weight,row.offset);
      int numOff = _dinfo.numStart();
      for(int c = 0; c < beta.length; ++c) {
        double val = row.weight * (exps[c+1] - (y == c?1:0));
        for (int j = 0; j < row.nBins; ++j)
          _gradient[c*P + row.binIds[j]] += val;
        for (int j = 0; j < row.nNums; ++j)
          _gradient[c*P + (row.numIds == null ? j + numOff : row.numIds[j])] += row.numVals[j] * val;
        _gradient[(c+1) * P - 1] += val;
      }
    }
    public void map(Chunk [] chks) {
      int rank = 0;
      for(int i = 0; i < _beta.length; ++i)
        for(int j = 0; j < _beta[i].length; ++j)
          if(_beta[i][j] != 0)
            ++rank;
      _gradient = new double[_beta.length*_beta[0].length];
      _val = new GLMValidation(_dinfo._adaptedFrame.lastVec().domain(), _ymu, new GLMParameters(Family.multinomial),rank,0,_validate, true);
      final int P = _beta[0].length;
      double [] etas = new double[_beta.length];
      double [] exps = new double[_beta.length+1];
      double [] etaOffsets = new double[_beta.length] ;
      Rows rows = _dinfo.rows(chks);
      if(rows._sparse)
        for(int i = 0; i < _beta.length; ++i)
          etaOffsets[i] = GLM.sparseOffset(_beta[i],_dinfo);
      for(int r = 0; r < rows._nrows; ++r) {
        final Row row = rows.row(r);
        if(row.bad || row.weight == 0) continue;
        _wsum += row.weight;
        _nobs++;
        processRow(row, _beta, etas, etaOffsets,  exps);
      }
      if (rows._sparse && _dinfo._normSub != null) { // adjust for centering
        int off = _dinfo.numStart();
        for(int c = 0; c < _beta.length; ++c) {
          double val = _gradient[(c+1)*P-1];
          for (int i = 0; i < _dinfo._nums; ++i)
            _gradient[c * P + off + i] -= val * _dinfo._normSub[i] * _dinfo._normMul[i];
        }
      }
    }

    @Override
    public void reduce(GLMMultinomialGradientTask gmgt){
      ArrayUtils.add(_gradient,gmgt._gradient);
      _nobs += gmgt._nobs;
      _wsum += gmgt._wsum;
      _likelihood += gmgt._likelihood;
      _val.reduce(gmgt._val);
    }

    @Override public void postGlobal(){
      ArrayUtils.mult(_gradient, _reg);
      int P = _beta[0].length;
      for(int c = 0; c < _beta.length; ++c)
        for(int j = 0; j < P-1; ++j)
          _gradient[c*P+j] += _currentLambda * _beta[c][j];
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
    long _nobs;
    double _wsum;
    double _ymu;
    final boolean _intercept;

    public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg,boolean intercept){this(dinfo,params, lambda, beta,reg, intercept, null);}
    public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, boolean intercept, H2OCountedCompleter cc){
      super(cc);
      _dinfo = dinfo;
      _params = params;
      _beta = beta;
      _reg = reg;
      _currentLambda = lambda;
      _intercept = intercept;
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
        if(row.weight == 0 || row.bad) continue;
        _nobs++;
        _wsum += row.weight;
        double eta = row.innerProduct(b) + row.offset;
        double mu = _params.linkInv(eta);
        _val.add(row.response(0), mu, row.weight, row.offset);
        _likelihood += row.weight*_params.likelihood(row.response(0), mu);
        double var = _params.variance(mu);
        if(var < 1e-6) var = 1e-6; // to avoid numerical problems with 0 variance
        double gval =row.weight * (mu-row.response(0)) / (var * _params.linkDeriv(mu));
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
      // compute the predicted mean and variance and ginfo for each row
      for(int r = 0; r < chks[0]._len; ++r){
        if(skp[r] || responseChunk.isNA(r))
          continue;
        double w = weightChunk.atd(r);
        if(w == 0 || Double.isNaN(w))
          continue;
        _nobs++;
        _wsum += w;
        assert w > 0;
        double y = responseChunk.atd(r);
        double mu = _params.linkInv(eta[r]);
        _val.add(y, mu, w, offsetChunk.atd(r));
        _likelihood += w * _params.likelihood(y, mu);
        double var = _params.variance(mu);
        if(var < 1e-6) var = 1e-6; // to avoid numerical problems with 0 variance
        eta[r] = w * (mu-y) / (var * _params.linkDeriv(mu));
        eta_sum += eta[r];
      }
      // finally go over the columns again and compute ginfo for each column
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
      _val = new GLMValidation(domain, new double[]{_ymu}, _params,rank,0,_validate,_intercept);
      boolean [] skp = MemoryManager.mallocZ(chks[0]._len);
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


  /**
   * Tassk with simplified ginfo computation for logistic regression (and least squares)
   * Looks like
   */
  public static class LBFGS_LogisticGradientTask extends GLMGradientTask {

    public LBFGS_LogisticGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, boolean intercept) {
      super(dinfo, params, lambda, beta, reg, intercept);
    }

    @Override   protected void goByRows(Chunk [] chks, boolean [] skp){
      Row row = _dinfo.newDenseRow();
      double [] g = _gradient;
      double [] b = _beta;
      for(int rid = 0; rid < chks[0]._len; ++rid) {
        if(skp[rid])continue;
        row = _dinfo.extractDenseRow(chks, rid, row);
        if(row.bad || row.weight == 0) continue;
        double y = -1 + 2*row.response(0);
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
      // compute the predicted mean and variance and ginfo for each row
      for(int r = 0; r < chks[0]._len; ++r){
        double w = weightsChunk.atd(r);
        if(skp[r] || responseChunk.isNA(r) || w == 0)
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
      // finally go over the columns again and compute ginfo for each column
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
        Chunk c = chks[i + _dinfo._cats]; //not expanded
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
   * One iteration of glm, computes weighted gram matrix and t(x)*y vector and t(y)*y scalar.
   *
   * @author tomasnykodym
   */
  public static class GLMIterationTask extends FrameTask2<GLMIterationTask> {
    final GLMParameters _params;
    final double [][]_beta_multinomial;
    final double []_beta;
    final int _c;
    protected Gram  _gram; // wx%*%x
    double [] _xy; // wx^t%*%z,

    GLMValidation _val; // validation of previous model
    final double [] _ymu;

    long _nobs;
    final boolean _validate;
    int [] _ti;
    public double _likelihood;
    final double _lambda;
    double wsum, wsumu;
    final boolean _intercept;

    public  GLMIterationTask(Key jobKey, DataInfo dinfo, double lambda, GLMModel.GLMParameters glm, boolean validate,
                             double [][] betaMultinomial, double [] beta, int c, double [] ymu, boolean intercept, H2OCountedCompleter cmp) {
      super(cmp,dinfo,jobKey);
      _params = glm;
      _beta = beta;
      _beta_multinomial = betaMultinomial.clone();
      _beta_multinomial[c] = beta;
      _c = c;
      _ymu = ymu;
      _validate = validate;
      _lambda = lambda;
      _intercept = intercept;
    }

    public  GLMIterationTask(Key jobKey, DataInfo dinfo, double lambda, GLMModel.GLMParameters glm, boolean validate,
                             double [] beta, double ymu,boolean intercept,  H2OCountedCompleter cmp) {
      super(cmp,dinfo,jobKey);
      _params = glm;
      _beta = beta;
      _beta_multinomial = null;
      _c = -1;
      _ymu = new double[]{ymu};
      _validate = validate;
      _lambda = lambda;
      _intercept = intercept;
    }

    @Override public boolean handlesSparseData(){return true;}


    transient private double [] _etas;
    transient private double [] _sparseOffsets;
    transient private double _sparseOffset;
    @Override
    public void chunkInit() {
      // initialize
      _gram = new Gram(_dinfo.fullN(), _dinfo.largestCat(), _dinfo._nums, _dinfo._cats,true);
      if(_params._family == Family.multinomial)
        _etas = new double[_beta_multinomial.length];
      // public GLMValidation(Key dataKey, double ymu, GLMParameters glm, int rank, float [] thresholds){
      if(_validate) {
        int rank = 0;
        if(_beta != null) for(double d:_beta) if(d != 0)++rank;
        String [] domain = _dinfo._adaptedFrame.lastVec().domain();
        if(domain == null && _params._family == Family.binomial)
          domain = new String[]{"0","1"}; // special hard-coded case for binomial on binary col
        _val = new GLMValidation(domain, _ymu, _params, rank, .5, true,_intercept); // todo pass correct threshold
      }
      _xy = MemoryManager.malloc8d(_dinfo.fullN()+1); // + 1 is for intercept
      if(_params._family == Family.binomial && _validate){
        _ti = new int[2];
      }

      if(_params._family == Family.multinomial) {
        _sparseOffsets = new double[_beta_multinomial.length];
        if(_sparse)
          for (int i = 0; i < _beta_multinomial.length; ++i)
          _sparseOffsets[i] = GLM.sparseOffset(_beta_multinomial[i],_dinfo);
      } else if(_sparse)
        _sparseOffset = GLM.sparseOffset(_beta,_dinfo);
    }

    @Override
    protected void processRow(Row r) { // called for every row in the chunk
      if(r.bad || r.weight == 0) return;
      ++_nobs;
      double y = r.response(0);
      assert ((_params._family != Family.gamma) || y > 0) : "illegal response column, y must be > 0  for family=Gamma.";
      assert ((_params._family != Family.binomial) || (0 <= y && y <= 1)) : "illegal response column, y must be <0,1>  for family=Binomial. got " + y;
      final double w;
      final double eta;
      double mu;
      final double var;
      final double wz;
      final int numStart = _dinfo.numStart();
      double d = 1;
      if( _params._family == Family.gaussian && _params._link == Link.identity){
        w = r.weight;
        wz = w*(y - r.offset);
        mu = 0;
        eta = mu;
      } else {
        if(_params._family == Family.multinomial) {
          y = (y == _c)?1:0;
          double maxrow = 0;
          for(int i = 0; i < _beta_multinomial.length; ++i) {
            _etas[i] = r.innerProduct(_beta_multinomial[i]) + _sparseOffsets[i];
            if(_etas[i] > maxrow /*|| -_etas[i] > maxrow*/)
              maxrow = _etas[i];
          }
          eta = _etas[_c];
          double etaExp = Math.exp(_etas[_c]-maxrow);
          double sumExp = 0;
          for(int i = 0; i < _beta_multinomial.length; ++i)
            sumExp += Math.exp(_etas[i]-maxrow);
          mu = etaExp / sumExp;
          if(mu < 1e-16)
            mu = 1e-16;//
          double logSumExp = Math.log(sumExp) + maxrow;
          _likelihood -= r.weight * (_etas[(int)r.response(0)] - logSumExp);
          d = mu*(1-mu);
          wz = r.weight * (eta * d + (y-mu));
          w  = r.weight * d;
        } else {
          eta = r.innerProduct(_beta) + _sparseOffset;
          mu = _params.linkInv(eta + r.offset);
          _likelihood += r.weight*_params.likelihood(y,mu);
          var = Math.max(1e-6, _params.variance(mu)); // avoid numerical problems with 0 variance
          d = _params.linkDeriv(mu);
          double z = eta + (y-mu)*d;
          w = r.weight/(var*d*d);
          wz = w*z;
          if(_validate)
            _val.add(y, mu, r.weight, r.offset);
        }
      }
      assert w >= 0|| Double.isNaN(w) : "invalid weight " + w; // allow NaNs - can occur if line-search is needed!
      wsum+=w;
      wsumu+=r.weight; // just add the user observation weight for the scaling.
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
    public void reduce(GLMIterationTask git){
      ArrayUtils.add(_xy, git._xy);
      _gram.add(git._gram);
      _nobs += git._nobs;
      wsum += git.wsum;
      wsumu += git.wsumu;
      if (_validate) _val.reduce(git._val);
      _likelihood += git._likelihood;
      super.reduce(git);
    }

    @Override protected void postGlobal(){
      if(_sparse && _dinfo._normSub != null) { // need to adjust gram for missing centering!
        int ns = _dinfo.numStart();
        int interceptIdx = _xy.length-1;
        double [] interceptRow = _gram._xx[interceptIdx-_gram._diagN];
        double nobs = interceptRow[interceptRow.length-1]; // weighted _nobs
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
        for(int i = ns; i < _dinfo.fullN(); ++i) {
          _xy[i] -= _xy[_xy.length - 1] * _dinfo._normSub[i - ns] * _dinfo._normMul[i - ns];
      }
      }
      if(_val != null){
        _val.computeAIC();
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
        if (r.bad || r.weight == 0) {
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
//        _xvals[i]._nobs = _nobs - _xvals[i]._nobs;
//        GLMModel.setXvalidation(cmp, _xmodels[i]._key, _lambda, _xvals[i]);
//      }
//      GLMModel.setXvalidation(cmp, _model._key, _lambda, new GLMXValidation(_model, _xmodels, _xvals, _lambda, _nobs,_thresholds));
//    }
//  }
}

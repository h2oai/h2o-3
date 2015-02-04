package hex.glm;

import hex.DataInfo;
import hex.DataInfo.Row;


import java.util.Arrays;

import hex.glm.GLMModel.GLMParameters;
import hex.gram.Gram;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMValidation.GLMXValidation;
import water.H2O.H2OCountedCompleter;
import water.*;
import water.fvec.Chunk;
import water.util.ArrayUtils;
import water.util.FrameUtils;

/**
 * Contains all GLM related distributed tasks.
 *
 * @author tomasnykodym
 *
 */

public abstract class GLMTask  {


 static class YMUTask extends MRTask<YMUTask> {
   double _ymu;
   double _yMin = Double.POSITIVE_INFINITY, _yMax = Double.NEGATIVE_INFINITY;
   long   _nobs;

   public YMUTask(DataInfo dinfo, H2OCountedCompleter cmp){super(cmp);}

   @Override public void map(Chunk [] chunks) {
     Chunk filterChunk = chunks[chunks.length-1];
     boolean [] skip = MemoryManager.mallocZ(chunks[0]._len);
     for(int i = 0; i < chunks.length-1; ++i) {
       for(int r = chunks[i].nextNZ(-1); r < chunks[i]._len; r = chunks[i].nextNZ(r)) {
         skip[r] |= chunks[i].isNA(r);
       }
     }
     Chunk response = chunks[chunks.length-2];
     for(int r = 0; r < response._len; ++r) {
       if(filterChunk.at8(r) == 0) continue;
       if(!skip[r] && !response.isNA(r)) {
         double d = response.atd(r);
         _ymu += d;
         if(d < _yMin)
           _yMin = d;
         if(d > _yMax)
           _yMax = d;
         ++_nobs;
       }
     }
     _ymu /= _nobs;
     for(int i = 0; i < skip.length; ++i)
       filterChunk.set(i, skip[i]?0:1);
   }

   @Override public void reduce(YMUTask ymt) {
     if(_nobs > 0 && ymt._nobs > 0) {
       double a = _nobs / (_nobs + ymt._nobs);
       double b = ymt._nobs / (_nobs + ymt._nobs);
       _ymu = _ymu * a  + ymt._ymu * b;
       _nobs += ymt._nobs;
       if(_yMin > ymt._yMin)
         _yMin = ymt._yMin;
       if(_yMax < ymt._yMax)
         _yMax = ymt._yMax;
     } else if (_nobs == 0) {
       _ymu = ymt._ymu;
       _nobs = ymt._nobs;
       _yMin = ymt._yMin;
       _yMax = ymt._yMax;
     }
   }
   public double ymu(int foldId) {return _ymu;  } // TODO add folds to support cross validation!
   public long  nobs(int foldId) {return _nobs; } // TODO add folds to support cross validation!
 }

  /**
   * Task to compute Lambda Max for the given dataset.
   * @author tomasnykodym
   */
  static class LMAXTask extends GLMGradientTask {
    private final double _gPrimeMu;
    private final double _reg;
    private final double _ymu;
    private double[] _z;
    double _lmax;

    private static double [][] nullBeta(double intercept, DataInfo dinfo) {
      double [][] res = new double[][]{ MemoryManager.malloc8d(dinfo.fullN() + (dinfo._intercept?1:0))};
      if(dinfo._intercept)
        res[0][dinfo.fullN()] = intercept;
      return res;
    }

    public LMAXTask(DataInfo dinfo, GLMModel.GLMParameters params, double ymu, long nobs, H2OCountedCompleter cmp) {
      super(dinfo, params, Double.NaN, nullBeta(params.link(ymu), dinfo),1.0/nobs, cmp);
      _gPrimeMu = params.linkDeriv(ymu);
      _reg = params.variance(ymu) / (nobs * Math.max(params._alpha[0],1e-3));
      _ymu = ymu;
    }

    @Override public void map(Chunk [] chunks) {
      super.map(chunks);
      _z = MemoryManager.malloc8d(_dinfo.fullN());
      Chunk response = chunks[chunks.length-2];
      Chunk filter = chunks[chunks.length-1];
      for( int i = 0; i < _dinfo._cats; ++i ) {
        for(int r = chunks[i].nextNZ(-1); r < chunks[i]._len; r = chunks[i].nextNZ(r)) {
          int off;
          if(filter.at8(r) == 1 && (off = _dinfo.getCategoricalId(i, (int)chunks[i].at8(r))) != -1)
            _z[off] += (response.atd(r) - _ymu) * _gPrimeMu;
        }
      }
      final int numStart = _dinfo.numStart();
      for(int i = 0; i < _dinfo._nums; ++i)
        for(int r = chunks[i].nextNZ(-1); r < chunks[i]._len; ++r)
          if(filter.at8(r) == 1)
            _z[i+numStart] += (response.atd(r) - _ymu) * _gPrimeMu*chunks[i].atd(r);
    }

    @Override public void reduce(GLMGradientTask glmt){
      super.reduce(glmt);
      LMAXTask lmax = (LMAXTask)glmt;
      ArrayUtils.add(_z, lmax._z);
    }
    @Override public void postGlobal(){
      double res = Math.abs(_z[0]);
      for( int i = 1; i < _z.length; ++i )
        if(res < _z[i])res = _z[i];
        else if(res < -_z[i])res = -_z[i];
      _lmax = _reg * res;
      _currentLambda = _lmax;
      super.postGlobal();
    }
    public double lmax(){ return _lmax;}
  }

  public static class GLMGradientTask extends MRTask<GLMGradientTask> {
    final GLMParameters _params;
    double _currentLambda;
    final double [][] _beta;
    final protected DataInfo _dinfo;
    final double _reg;
    public double [][] _gradient;
    public double []   _objVals;



    public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[][] beta, double reg){this(dinfo,params, lambda, beta,reg,null);}
    public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[][] beta, double reg, H2OCountedCompleter cc){
      super(cc);
      _dinfo = dinfo;
      _params = params;
      _beta = beta;
      _reg = reg;
      _currentLambda = lambda;
    }

    private final void goByRows(Chunk [] chks){
      Row row = _dinfo.newDenseRow();
      for(int rid = 0; rid < chks[0]._len; ++rid) {
        row = _dinfo.extractDenseRow(chks, rid, row);
        for(int i = 0; i < _beta.length; ++i) {
          double [] g = _gradient[i];
          double [] b = _beta[i];
          double eta = row.innerProduct(b);
          double mu = _params.linkInv(eta);
          double var = _params.variance(mu);
          if(var < 1e-6) var = 1e-6; // to avoid numerical problems with 0 variance
          double gval = (mu-row.response(0)) / (var * _params.linkDeriv(mu));
          // categoricals
          for(int c: row.binIds)
            g[c] += gval;
          int off = _dinfo.numStart();
          // numbers
          for(int j = 0; j < _dinfo._nums; ++i)
            g[j + off] += row.numVals[i] * gval;
          // intercept
          if(_dinfo._intercept)
            g[g.length-1] += gval;
        }
      }
    }

    @Override
    public void postGlobal(){
      for(int i = 0; i < _objVals.length; ++i) {
        _objVals[i] += .5 * _currentLambda * ArrayUtils.l2norm2(_beta[i],_dinfo._intercept);
        for(int j = 0; j < _beta[i].length - (_dinfo._intercept?1:0); ++j)
          _gradient[i][j] += _currentLambda * _beta[i][j];
      }
    }

    // compute linear estimate by summing contributions for all columns
    // (looping by column in the outer loop to have good access pattern and to exploit sparsity)
    protected final double [][] computeEtaByCols(Chunk [] chks, boolean [] skip) {
      double  [][] eta = new double[_beta.length][];
      for(int i = 0; i < eta.length; ++i)
        eta[i] = MemoryManager.malloc8d(chks[0]._len);

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
            for(int j = 0; j < eta.length; ++j)
              eta[j][r] += _beta[j][off];
        }
      }
      final int numStart = _dinfo.numStart();
      // compute default eta offset for 0s
      if(_dinfo._normMul != null && _dinfo._normSub != null) {
        for (int j = 0; j < eta.length; ++j) {
          double off = 0;
          for (int i = 0; i < _dinfo._nums; ++i)
            off -= _beta[j][numStart + i] * _dinfo._normSub[i] * _dinfo._normMul[i];
          for(int r = 0; r < chks[0]._len; ++r)
            eta[j][r] += off;
        }
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
          for (int j = 0; j < eta.length; ++j)
            eta[j][r] += _beta[j][numStart + i] * d;
        }
      }
      return eta;
    }

    private final void goByCols(Chunk [] chks){
      int numStart = _dinfo.numStart();
      boolean []   skp = MemoryManager.mallocZ(chks[0]._len);
      double  [][] eta = computeEtaByCols(chks,skp);

      Chunk offsetChunk = null;
      int nxs = chks.length-1; // -1 for response
      if(_dinfo._offset) {
        nxs -= 1;
        offsetChunk = chks[nxs];
      }
      Chunk responseChunk = chks[nxs];

      double []      obj = MemoryManager.malloc8d(_beta.length);
      double [] eta_sums = MemoryManager.malloc8d(eta.length);

      // compute the predicted mean and variance and gradient for each row
      for(int r = 0; r < chks[0]._len; ++r){
        if(skp[r] || responseChunk.isNA(r))
          continue;
        double off = (_dinfo._offset?offsetChunk.atd(r):0);
        double y = responseChunk.atd(r);
        for(int j = 0; j < eta.length; ++j) {
          double offset = off + (_dinfo._intercept?_beta[j][_beta[j].length-1]:0);
          double mu = _params.linkInv(eta[j][r] + offset);
          obj[j] += _params.deviance(y,mu);
          double var = _params.variance(mu);
          if(var < 1e-6) var = 1e-6; // to avoid numerical problems with 0 variance
          eta[j][r] = (mu-y) / (var * _params.linkDeriv(mu));
          eta_sums[j] += eta[j][r];
        }
      }
      _gradient = new double[_beta.length][];
      for(int i = 0; i < _gradient.length; ++i)
        _gradient[i] = MemoryManager.malloc8d(_beta[i].length);

      // finally go over the columns again and compute gradient for each column
      // first handle eta offset and intercept
      for(int j = 0; j < _gradient.length; ++j) {
        if(_dinfo._intercept)
          _gradient[j][_gradient[j].length-1] = eta_sums[j];
        if(_dinfo._normMul != null && _dinfo._normSub != null)
          for(int i = 0; i < _dinfo._nums; ++i)
            _gradient[j][numStart + i] = -_dinfo._normSub[i]*_dinfo._normMul[i]*eta_sums[j];
      }
      // categoricals
      for(int i = 0; i < _dinfo._cats; ++i) {
        Chunk c = chks[i];
        for(int r = 0; r < c._len; ++r) { // categoricals can not be sparse
          if(skp[r]) continue;
          int off = _dinfo.getCategoricalId(i,(int)chks[i].at8(r));
          if(off != -1)
            for(int j = 0; j < eta.length; ++j)
              _gradient[j][off] += eta[j][r];
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
          for (int j = 0; j < eta.length; ++j)
            _gradient[j][numStart + i] += eta[j][r] * d;
        }
      }
      // apply reg
      for(int i = 0; i < _beta.length; ++i) {
        obj[i] *= _reg;
        for (int j = 0; j < _beta[i].length; ++j)
          _gradient[i][j] *= _reg;
      }
      _objVals = obj;
    }

    private final boolean mostlySparse(Chunk [] chks){
      int cnt = 0;
      for(Chunk chk:chks)
        if(chk.isSparse())
          ++cnt;
      return cnt >= chks.length >> 1;
    }

    public void map(Chunk [] chks){
      if(chks.length >= 100 || mostlySparse(chks))
        goByCols(chks);
      else
        goByRows(chks);
    }

    public void reduce(GLMGradientTask grt){
      ArrayUtils.add(_objVals, grt._objVals);
      for(int i = 0; i < _beta.length; ++i)
        ArrayUtils.add(_gradient[i],grt._gradient[i]);
    }
  }
  /**
   * One iteration of glm, computes weighted gram matrix and t(x)*y vector and t(y)*y scalar.
   *
   * @author tomasnykodym
   */
  public static class GLMIterationTask extends MRTask<GLMIterationTask> {
    final Key _jobKey;
    final DataInfo _dinfo;
    final GLMParameters _glm;
    final double [] _beta;
    protected Gram  _gram;
    double [] _xy;
    protected double [] _grad;
    double    _yy;
    GLMValidation _val; // validation of previous model
    final double _ymu;
    protected final double _reg;
    long _nobs;
    final boolean _validate;
    final float [] _thresholds;
    float [][] _newThresholds;
    int [] _ti;
    final boolean _computeGradient;
    final boolean _computeGram;
    public static final int N_THRESHOLDS = 50;

    public  GLMIterationTask(Key jobKey, DataInfo dinfo, GLMModel.GLMParameters glm, boolean computeGram, boolean validate, boolean computeGradient, double [] beta, double ymu, double reg, float [] thresholds, H2OCountedCompleter cmp) {
      _jobKey = jobKey;
      _dinfo = dinfo;
      _glm = glm;
      _beta = beta;
      _ymu = ymu;
      _reg = reg;
      _computeGram = computeGram;
      _validate = validate;
      assert glm._family != Family.binomial || thresholds != null;
      _thresholds = _validate?thresholds:null;
      _computeGradient = computeGradient;
      assert !_computeGradient || validate;
    }

    private void sampleThresholds(int yi){
      _ti[yi] = (_newThresholds[yi].length >> 2);
      try{ Arrays.sort(_newThresholds[yi]);} catch(Throwable t){
        System.out.println("got AIOOB during sort?! ary = " + Arrays.toString(_newThresholds[yi]));
        return;
      } // sort throws AIOOB sometimes!
      for (int i = 0; i < _newThresholds.length; i += 4)
        _newThresholds[yi][i >> 2] = _newThresholds[yi][i];
    }

    public double objVal(){throw H2O.unimpl();}

    @Override
    public void map(Chunk [] chks) {
      if(!Job.isRunning(_jobKey))
        throw new Job.JobCancelledException();
      // initialize
      if(_computeGram)_gram = new Gram(_dinfo.fullN(), _dinfo.largestCat(), _dinfo._nums, _dinfo._cats,true);
      _xy = MemoryManager.malloc8d(_dinfo.fullN()+1); // + 1 is for intercept

      if(_computeGradient)
        _grad = MemoryManager.malloc8d(_dinfo.fullN()+1); // + 1 is for intercept
      if(_glm._family == Family.binomial && _validate){
        _ti = new int[2];
        _newThresholds = new float[2][4*N_THRESHOLDS];
      }

      // compute
      boolean sparse = FrameUtils.sparseRatio(chks) > .5;
      if(sparse) {
        for(Row r:_dinfo.extractSparseRows(chks, _beta))
          processRow(r);
      } else {
        Row row = _dinfo.newDenseRow();
        for(int r = 0 ; r < chks[0]._len; ++r)
          processRow(_dinfo.extractDenseRow(chks,r, row));
      }

      // need to adjust gradient by centered zeros
      int numStart = _dinfo.numStart();
      if(_computeGradient && sparse && _dinfo._normSub != null) {
        for(int i = 0; i < _dinfo._nums; ++i)
          _grad[numStart+i] -= _gsum * _dinfo._normSub[i] * _dinfo._normMul[i];
      }
      // finalize
      if(_computeGram)
        _gram.mul(_reg);
      for(int i = 0; i < _xy.length; ++i)
        _xy[i] *= _reg;
      if(_grad != null)
        for(int i = 0; i < _grad.length; ++i)
          _grad[i] *= _reg;
      _yy *= _reg;
      if(_validate && _glm._family == Family.binomial) {
        assert _val != null;
        _newThresholds[0] = Arrays.copyOf(_newThresholds[0],_ti[0]);
        _newThresholds[1] = Arrays.copyOf(_newThresholds[1],_ti[1]);
        Arrays.sort(_newThresholds[0]);
        Arrays.sort(_newThresholds[1]);
      }
    }

    private transient double _gsum;

    protected final void processRow(Row r) {
      if(!r.good) return;
      ++_nobs;
      final double y = r.response(0);
      assert ((_glm._family != Family.gamma) || y > 0) : "illegal response column, y must be > 0  for family=Gamma.";
      assert ((_glm._family != Family.binomial) || (0 <= y && y <= 1)) : "illegal response column, y must be <0,1>  for family=Binomial. got " + y;
      final double w, eta, mu, var, z;
      final int numStart = _dinfo.numStart();
      double d = 1;
      if( _glm._family == Family.gaussian){
        w = 1;
        z = y;
        mu = (_validate || _computeGradient)?r.innerProduct(_beta):0;
        var = 1;
      } else {
        eta = r.innerProduct(_beta);
        mu = _glm.linkInv(eta);
        var = Math.max(1e-6, _glm.variance(mu)); // avoid numerical problems with 0 variance
        d = _glm.linkDeriv(mu);
        z = eta + (y-mu)*d;
        w = 1.0/(var*d*d);
      }
      if(_validate) {
        _val.add(y, mu);
        if(_glm._family == Family.binomial) {
          int yi = (int) y;
          if (_ti[yi] == _newThresholds[yi].length)
            sampleThresholds(yi);
          _newThresholds[yi][_ti[yi]++] = (float) mu;
        }
      }
      assert w >= 0|| Double.isNaN(w) : "invalid weight " + w; // allow NaNs - can occur if line-search is needed!
      double wz = w * z;
      _yy += wz * z;
      if(_computeGradient || _computeGram){
        double grad = _computeGradient?((mu-y) / (var * d)):0;
        _gsum += grad;
        for(int i = 0; i < r.nBins; ++i){
          int ii = r.binIds[i];
          if(_computeGradient)_grad[ii] += grad;
          _xy[ii] += wz;
        }
        for(int i = 0; i < r.nNums; ++i){
          int id = r.numIds == null?(i+r.numStart):r.numIds[i];
          double val = r.numVals[i];
          _xy[id] += wz*val;
          if(_computeGradient)
            _grad[id] += grad*val;
        }
        if(_computeGradient)_grad[numStart + _dinfo._nums] += grad;
        _xy[numStart + _dinfo._nums] += wz;
        if(_computeGram)_gram.addRow(r, w);
      }
    }


    @Override
    public void reduce(GLMIterationTask git){
      if(_jobKey == null || Job.isRunning(_jobKey)) {
        ArrayUtils.add(_xy, git._xy);
        if (_computeGram) _gram.add(git._gram);
        _yy += git._yy;
        _nobs += git._nobs;
        if (_validate) _val.add(git._val);
        if (_computeGradient) ArrayUtils.add(_grad, git._grad);
        if(_validate && _glm._family == Family.binomial) {
          _newThresholds[0] = ArrayUtils.join(_newThresholds[0], git._newThresholds[0]);
          _newThresholds[1] = ArrayUtils.join(_newThresholds[1], git._newThresholds[1]);
          if (_newThresholds[0].length >= 2 * N_THRESHOLDS) {
            for (int i = 0; i < 2 * N_THRESHOLDS; i += 2)
              _newThresholds[0][i >> 1] = _newThresholds[0][i];
          }
          if (_newThresholds[0].length > N_THRESHOLDS)
            _newThresholds[0] = Arrays.copyOf(_newThresholds[0], N_THRESHOLDS);
          if (_newThresholds[1].length >= 2 * N_THRESHOLDS) {
            for (int i = 0; i < 2 * N_THRESHOLDS; i += 2)
              _newThresholds[1][i >> 1] = _newThresholds[1][i];
          }
          if (_newThresholds[1].length > N_THRESHOLDS)
            _newThresholds[1] = Arrays.copyOf(_newThresholds[1], N_THRESHOLDS);
        }
        super.reduce(git);
      }
    }

    @Override protected void postGlobal(){
      if(_val != null){
        _val.computeAIC();
        _val.computeAUC();
      }
    }
    public double [] gradient(double alpha, double lambda){
      final double [] res = _grad.clone();
      if(_beta != null)
        for(int i = 0; i < res.length-1; ++i) res[i] += (1-alpha)*lambda*_beta[i];
      return res;
    }
  }

  public static class GLMValidationTask<T extends GLMValidationTask<T>> extends MRTask<T> {
    protected final GLMModel _model;
    protected GLMValidation _res;
    public final double _lambda;
    public boolean _improved;
    Key _jobKey;
    public static Key makeKey(){return Key.make("__GLMValidation_" + Key.make().toString());}
    public GLMValidationTask(GLMModel model, double lambda){this(model,lambda,null);}
    public GLMValidationTask(GLMModel model, double lambda, H2OCountedCompleter completer){super(completer); _lambda = lambda; _model = model;}
    @Override public void map(Chunk[] chunks){
      _res = new GLMValidation(null,_model._ymu,_model._parms,_model.rank(_lambda));
      final int nrows = chunks[0]._len;
      double [] row   = MemoryManager.malloc8d(_model._output._names.length);
      float  [] preds = MemoryManager.malloc4f(_model._parms._family == Family.binomial?3:1);
      OUTER:
      for(int i = 0; i < nrows; ++i){
        if(chunks[chunks.length-1].isNA(i))continue;
        for(int j = 0; j < chunks.length-1; ++j){
          if(chunks[j].isNA(i))continue OUTER;
          row[j] = chunks[j].atd(i);
        }
        _model.score0(row, preds);
        double response = chunks[chunks.length-1].atd(i);
        _res.add(response, _model._parms._family == Family.binomial?preds[2]:preds[0]);
      }
    }
    @Override public void reduce(GLMValidationTask gval){_res.add(gval._res);}
    @Override public void postGlobal(){
      _res.computeAIC();
      _res.computeAUC();
    }
  }
  // use general score to reduce number of possible different code paths
  public static class GLMXValidationTask extends GLMValidationTask<GLMXValidationTask>{
    protected final GLMModel [] _xmodels;
    protected GLMValidation [] _xvals;
    long _nobs;
    final float [] _thresholds;
    public static Key makeKey(){return Key.make("__GLMValidation_" + Key.make().toString());}

    public GLMXValidationTask(GLMModel mainModel,double lambda, GLMModel [] xmodels, float [] thresholds){this(mainModel,lambda,xmodels,thresholds,null);}
    public GLMXValidationTask(GLMModel mainModel,double lambda, GLMModel [] xmodels, float [] thresholds, final H2OCountedCompleter completer){
      super(mainModel, lambda,completer);
      _xmodels = xmodels;
      _thresholds = thresholds;
    }
    @Override public void map(Chunk [] chunks){
      long gid = chunks[0].start();
      _xvals = new GLMValidation[_xmodels.length];
      for(int i = 0; i < _xmodels.length; ++i)
        _xvals[i] = new GLMValidation(null,_xmodels[i]._ymu,_xmodels[i]._parms,_xmodels[i]._output.rank(),_thresholds);
      final int nrows = chunks[0]._len;
      double [] row   = MemoryManager.malloc8d(_xmodels[0]._output._names.length);
      float  [] preds = MemoryManager.malloc4f(_xmodels[0]._parms._family == Family.binomial?3:1);
      OUTER:
      for(int i = 0; i < nrows; ++i){
        if(chunks[chunks.length-1].isNA(i))continue;
        for(int j = 0; j < chunks.length-1; ++j){
          if(chunks[j].isNA(i))continue OUTER;
          row[j] = chunks[j].atd(i);
        }
        ++_nobs;
        final int mid = (int)((i + gid)  % _xmodels.length);
        final GLMModel model = _xmodels[mid];
        final GLMValidation val = _xvals[mid];
        model.score0(row, preds);
        double response = chunks[chunks.length-1].at8(i);
        val.add(response, model._parms._family == Family.binomial?preds[2]:preds[0]);
      }
    }
    @Override public void reduce(GLMXValidationTask gval){
      _nobs += gval._nobs;
      for(int i = 0; i < _xvals.length; ++i)
        _xvals[i].add(gval._xvals[i]);}

    @Override public void postGlobal() {
      H2OCountedCompleter cmp = (H2OCountedCompleter)getCompleter();
      if(cmp != null)cmp.addToPendingCount(_xvals.length + 1);
      for (int i = 0; i < _xvals.length; ++i) {
        _xvals[i].computeAIC();
        _xvals[i].computeAUC();
        _xvals[i].nobs = _nobs - _xvals[i].nobs;
        GLMModel.setXvalidation(cmp, _xmodels[i]._key, _lambda, _xvals[i]);
      }
      GLMModel.setXvalidation(cmp, _model._key, _lambda, new GLMXValidation(_model, _xmodels, _xvals, _lambda, _nobs,_thresholds));
    }
  }

}

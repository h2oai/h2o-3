package hex;

import water.*;
import water.H2O.H2OCountedCompleter;
import water.Job.JobCancelledException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

public abstract class FrameTask<T extends FrameTask<T>> extends MRTask<T>{
  protected transient DataInfo _dinfo;
  public DataInfo dinfo() { return _dinfo; }
  final Key _dinfoKey;
  final int [] _activeCols;
  final protected Key _jobKey;
//  double    _ymu = Double.NaN; // mean of the response
  // size of the expanded vector of parameters

  protected float _useFraction = 1.0f;
  protected boolean _shuffle = false;
  protected boolean _skipMissing = true;

  protected boolean skipMissing() { return _skipMissing; }

  public FrameTask(Key jobKey, DataInfo dinfo) {
    this(jobKey, dinfo._key, dinfo._activeCols,null);
  }
  public FrameTask(Key jobKey, DataInfo dinfo, H2OCountedCompleter cmp) {
    this(jobKey, dinfo._key, dinfo._activeCols,cmp);
  }
  public FrameTask(Key jobKey, Key dinfoKey, int [] activeCols) {
    this(jobKey,dinfoKey, activeCols,null);
  }
  public FrameTask(Key jobKey, Key dinfoKey, int [] activeCols, H2OCountedCompleter cmp) {
    super(cmp);
    assert dinfoKey == null || DKV.get(dinfoKey) != null;
    _jobKey = jobKey;
    _dinfoKey = dinfoKey;
    _activeCols = activeCols;
  }
  protected FrameTask(FrameTask ft){
    _dinfo = ft._dinfo;
    _jobKey = ft._jobKey;
    _useFraction = ft._useFraction;
    _shuffle = ft._shuffle;
    _activeCols = ft._activeCols;
    _dinfoKey = ft._dinfoKey;
    assert DKV.get(_dinfoKey) != null;
  }
  @Override protected void setupLocal(){
    DataInfo dinfo = DKV.get(_dinfoKey).get();
    _dinfo = _activeCols == null?dinfo:dinfo.filterExpandedColumns(_activeCols);
  }
  @Override protected void closeLocal(){ _dinfo = null;}

  /**
   * Method to process one row of the data for GLM functions.
   * Numeric and categorical values are passed separately, as is response.
   * Categoricals are passed as absolute indexes into the expanded beta vector, 0-levels are skipped
   * (so the number of passed categoricals will not be the same for every row).
   *
   * Categorical expansion/indexing:
   *   Categoricals are placed in the beginning of the beta vector.
   *   Each cat variable with n levels is expanded into n-1 independent binary variables.
   *   Indexes in cats[] will point to the appropriate coefficient in the beta vector, so e.g.
   *   assume we have 2 categorical columns both with values A,B,C, then the following rows will have following indexes:
   *      A,A - ncats = 0, we do not pass any categorical here
   *      A,B - ncats = 1, indexes = [2]
   *      B,B - ncats = 2, indexes = [0,2]
   *      and so on
   *
   * @param gid      - global id of this row, in [0,_adaptedFrame.numRows())
   * @param nums     - numeric values of this row
   * @param ncats    - number of passed (non-zero) categoricals
   * @param cats     - indexes of categoricals into the expanded beta-vector.
   * @param response - numeric value for the response
   */
  protected void processRow(long gid, double [] nums, int ncats, int [] cats, double [] response){throw new RuntimeException("should've been overriden!");}
  protected void processRow(long gid, double [] nums, int ncats, int [] cats, double [] response, NewChunk [] outputs){throw new RuntimeException("should've been overriden!");}


  public static class DataInfo extends Keyed {
    public int [] _activeCols;
    public Frame _adaptedFrame;
    public int _responses; // number of responses

    @Override
    public long checksum() {throw H2O.unimpl();} // don't really need checksum

    public enum TransformType { NONE, STANDARDIZE, NORMALIZE, DEMEAN, DESCALE }
    public TransformType _predictor_transform;
    public TransformType _response_transform;
    public boolean _useAllFactorLevels;
    public int _nums;
    public int _cats;
    public int [] _catOffsets;
    public int [] _catMissing;
    public double [] _normMul;
    public double [] _normSub;
    public double [] _normRespMul;
    public double [] _normRespSub;
    public int _foldId;
    public int _nfolds;

    public DataInfo deep_clone() {
      AutoBuffer ab = new AutoBuffer();
      this.write(ab);
      ab.flipForReading();
      return (DataInfo)new DataInfo().read(ab);
    }

    private DataInfo() {super(null);_catLvls = null;}

    private DataInfo(Key selfKey, DataInfo dinfo, int foldId, int nfolds){
      super(selfKey);
      assert dinfo._catLvls == null:"Should not be called with filtered levels (assuming the selected levels may change with fold id) ";
      assert dinfo._predictor_transform != null;
      assert dinfo. _response_transform != null;
      _predictor_transform = dinfo._predictor_transform;
      _response_transform = dinfo._response_transform;
      _responses = dinfo._responses;
      _nums = dinfo._nums;
      _cats = dinfo._cats;
      _adaptedFrame = dinfo._adaptedFrame;
      _catOffsets = dinfo._catOffsets;
      _catMissing = dinfo._catMissing;
      _normMul = dinfo._normMul;
      _normSub = dinfo._normSub;
      _normRespMul = dinfo._normRespMul;
      _normRespSub = dinfo._normRespSub;
      _foldId = foldId;
      _nfolds = nfolds;
      _useAllFactorLevels = dinfo._useAllFactorLevels;
      _catLvls = null;
    }

    public DataInfo(Key selfKey, Frame train, Frame valid, int hasResponses, boolean useAllFactorLvls, double [] normSub, double [] normMul, TransformType predictor_transform, double [] normRespSub, double [] normRespMul){
      this(selfKey, train, valid, hasResponses,useAllFactorLvls,
        normMul != null && normSub != null ? predictor_transform : TransformType.NONE, //just allocate, doesn't matter whether standardize or normalize is used (will be overwritten below)
        normRespMul != null && normRespSub != null ? TransformType.STANDARDIZE : TransformType.NONE);
      assert predictor_transform != null;
      assert (normSub == null) == (normMul == null);
      assert (normRespSub == null) == (normRespMul == null);
      if(normSub != null) {
        System.arraycopy(normSub, 0, _normSub, 0, normSub.length);
        System.arraycopy(normMul, 0, _normMul, 0, normMul.length);
      }
      if(normRespSub != null) {
        System.arraycopy(normRespSub, 0, _normRespSub, 0, normRespSub.length);
        System.arraycopy(normRespMul, 0, _normRespMul, 0, normRespMul.length);
      }
    }

    final int [][] _catLvls;

    public DataInfo(Key selfKey, Frame train, Frame valid, int nResponses, boolean useAllFactors, TransformType predictor_transform) {
      this(selfKey, train, valid, nResponses, useAllFactors, predictor_transform, TransformType.NONE);
    }

    //new DataInfo(f,catLvls, _responses, _standardize, _response_transform);
    public DataInfo(Key selfKey, Frame fr, int[][] catLevels, int responses, TransformType predictor_transform, TransformType response_transform, int foldId, int nfolds){
      super(selfKey);
      assert predictor_transform != null;
      assert  response_transform != null;
      _predictor_transform = predictor_transform;
      _response_transform  =  response_transform;

      _adaptedFrame = fr;
      _catOffsets = MemoryManager.malloc4(catLevels.length+1);
      _catMissing = new int[catLevels.length];
      int s = 0;

      for(int i = 0; i < catLevels.length; ++i){
        _catOffsets[i] = s;
        s += catLevels[i].length;
      }
      _catLvls = catLevels;
      _catOffsets[_catOffsets.length-1] = s;
      _responses = responses;
      _cats = catLevels.length;
      _nums = fr.numCols()-_cats - responses;
      if( _nums > 0 ) {
        switch(_predictor_transform) {
        case STANDARDIZE:
          _normMul = MemoryManager.malloc8d(_nums);
          _normSub = MemoryManager.malloc8d(_nums);
          for (int i = 0; i < _nums; ++i) {
            Vec v = fr.vec(catLevels.length+i);
            _normMul[i] = (v.sigma() != 0)?1.0/v.sigma():1.0;
            _normSub[i] = v.mean();
          }
          break;
        case NORMALIZE:
          _normMul = MemoryManager.malloc8d(_nums);
          _normSub = MemoryManager.malloc8d(_nums);
          for (int i = 0; i < _nums; ++i) {
            Vec v = fr.vec(catLevels.length+i);
            _normMul[i] = (v.max() - v.min() > 0)?1.0/(v.max() - v.min()):1.0;
            _normSub[i] = v.mean();
          }
          break;
        case DEMEAN:
          _normMul = null;
          _normSub = MemoryManager.malloc8d(_nums);
          for (int i = 0; i < _nums; ++i) {
            Vec v = fr.vec(catLevels.length+i);
            _normSub[i] = v.mean();
          }
          break;
        case DESCALE:
          _normMul = MemoryManager.malloc8d(_nums);
          _normSub = null;
          for (int i = 0; i < _nums; ++i) {
            Vec v = fr.vec(catLevels.length+i);
            _normMul[i] = (v.sigma() != 0)?1.0/v.sigma():1.0;
          }
          break;
        case NONE:
          _normMul = null;
          _normSub = null;
          break;
        default:
          throw H2O.unimpl();
        }
      }
      if(responses > 0){
        switch(_response_transform) {
          case STANDARDIZE:
            _normRespMul = MemoryManager.malloc8d(responses);
            _normRespSub = MemoryManager.malloc8d(responses);
            for (int i = 0; i < responses; ++i) {
              Vec v = fr.vec(fr.numCols()-responses+i);
              _normRespMul[i] = (v.sigma() != 0)?1.0/v.sigma():1.0;
              _normRespSub[i] = v.mean();
            }
            break;
          case NORMALIZE:
            _normRespMul = MemoryManager.malloc8d(responses);
            _normRespSub = MemoryManager.malloc8d(responses);
            for (int i = 0; i < responses; ++i) {
              Vec v = fr.vec(fr.numCols()-responses+i);
              _normRespMul[i] = (v.max() - v.min() > 0)?1.0/(v.max() - v.min()):1.0;
              _normRespSub[i] = v.mean();
            }
            break;
          case DEMEAN:
            _normRespMul = null;
            _normRespSub = MemoryManager.malloc8d(responses);
            for (int i = 0; i < responses; ++i) {
              Vec v = fr.vec(fr.numCols()-responses+i);
              _normRespSub[i] = v.mean();
            }
            break;
          case DESCALE:
            _normRespSub = null;
            _normRespMul = MemoryManager.malloc8d(responses);
            for (int i = 0; i < responses; ++i) {
              Vec v = fr.vec(fr.numCols()-responses+i);
              _normRespMul[i] = (v.sigma() != 0)?1.0/v.sigma():1.0;
            }
            break;
          case NONE:
            _normRespMul = null;
            _normRespSub = null;
            break;
          default:
            throw H2O.unimpl();
        }
      }
      _useAllFactorLevels = false;
      _adaptedFrame.reloadVecs();
      _nfolds = nfolds;
      _foldId = foldId;
    }

    // Modify the train & valid frames directly; sort the categorical columns
    // up front according to size; compute the mean/sigma for each column for
    // later normalization.
    public DataInfo(Key selfKey, Frame train, Frame valid, int nResponses, boolean useAllFactorLevels, TransformType predictor_transform, TransformType response_transform) {
      super(selfKey);
      assert predictor_transform != null;
      assert  response_transform != null;

      _nfolds = _foldId = 0;
      _predictor_transform = predictor_transform;
      _response_transform = response_transform;
      _responses = nResponses;
      _useAllFactorLevels = useAllFactorLevels;
      _catLvls = null;
      final Vec[] tvecs = train.vecs();
      final Vec[] vvecs = valid.vecs();

      // Count categorical-vs-numerical
      final int n = tvecs.length-_responses;
      assert n >= 1;            // Checked in init() before
      int [] nums = MemoryManager.malloc4(n);
      int [] cats = MemoryManager.malloc4(n);
      int nnums = 0, ncats = 0;
      for(int i = 0; i < n; ++i)  
        if (tvecs[i].isEnum() && (tvecs[i].domain() != null)) {
          cats[ncats++] = i;
        }
        else {
          if (tvecs[i].isEnum()) {
            Log.debug("In DataInfo:  Confused isEnum column number " + i);
          }
          nums[nnums++] = i;
        }
      _nums = nnums;
      _cats = ncats;
      // sort the cats in the decreasing order according to their size
      for(int i = 0; i < ncats; ++i)
        for(int j = i+1; j < ncats; ++j)
          if( tvecs[cats[i]].domain().length < tvecs[cats[j]].domain().length ) {
            int x = cats[i];
            cats[i] = cats[j];
            cats[j] = x;
          }

      String[] names = new String[train.numCols()];
      Vec[] tvecs2 = new Vec[train.numCols()];
      Vec[] vvecs2 = new Vec[train.numCols()];

      // Compute the cardinality of each cat
      _catOffsets = MemoryManager.malloc4(ncats+1);
      _catMissing = new int[ncats];
      int len = _catOffsets[0] = 0;
      for(int i = 0; i < ncats; ++i) {
        names[i]  =   train._names[cats[i]];
        vvecs2         [i] = vvecs[cats[i]];
        Vec v = (tvecs2[i] = tvecs[cats[i]]);
        _catMissing[i] = v.naCnt() > 0 ? 1 : 0; //needed for test time
        _catOffsets[i+1] = (len += v.domain().length - (useAllFactorLevels?0:1) + (v.naCnt()>0?1:0)); //missing values turn into a new factor level
      }

      // Compute the mean/sigma for each predictor
      switch(predictor_transform) {
      case STANDARDIZE:
      case NORMALIZE:  _normSub = MemoryManager.malloc8d(nnums);  _normMul = MemoryManager.malloc8d(nnums); Arrays.fill(_normMul, 1);  break;
      case DEMEAN:     _normSub = MemoryManager.malloc8d(nnums);  _normMul = null;                                                     break;
      case DESCALE:    _normSub = null;                           _normMul = MemoryManager.malloc8d(nnums);                            break;
      case NONE:       _normSub = null;                           _normMul = null;                                                     break;
      default:         throw H2O.unimpl();
      }
      for(int i = 0; i < nnums; ++i){
        names[ncats+i]  =   train._names[nums[i]];
        vvecs2         [ncats+i] = vvecs[nums[i]];
        Vec v = (tvecs2[ncats+i] = tvecs[nums[i]]);
        double vs = (v.sigma()      ) == 0 ? 1.0 : 1.0/(v.sigma()      );
        double vm = (v.max()-v.min()) == 0 ? 1.0 : 1.0/(v.max()-v.min());
        switch(predictor_transform){
        case STANDARDIZE:  _normSub[i] = v.mean();  _normMul[i] = vs;  break;
        case NORMALIZE:    _normSub[i] = v.mean();  _normMul[i] = vm;  break;
        case DEMEAN:       _normSub[i] = v.mean();                     break;
        case DESCALE:                               _normMul[i] = vs;  break;
        case NONE:                                                     break;
        default:           throw H2O.unimpl();
        }
      }

      // Compute the mean/sigma for each response
      if (_responses > 0) {
        switch(response_transform){
        case STANDARDIZE:
        case NORMALIZE: _normRespSub = MemoryManager.malloc8d(_responses);  _normRespMul = MemoryManager.malloc8d(_responses); Arrays.fill(_normRespMul, 1);  break;
        case DEMEAN:    _normRespSub = MemoryManager.malloc8d(_responses);  _normRespMul = null;                                                              break;
        case DESCALE:   _normRespSub = null;                                _normRespMul = MemoryManager.malloc8d(_responses);                                break;
        case NONE:      _normRespSub = null;                                _normRespMul = null;                                                              break;
        default:        throw H2O.unimpl();
        }
        for(int i = 0; i < _responses; ++i){
          names[ncats+nnums+i]  =   train._names[ncats+nnums+i];
          vvecs2         [ncats+nnums+i] = vvecs[ncats+nnums+i];
          Vec v = (tvecs2[ncats+nnums+i] = tvecs[ncats+nnums+i]);
          double vs = (v.sigma()      ) == 0 ? 1.0 : 1.0/(v.sigma()      );
          double vm = (v.max()-v.min()) == 0 ? 1.0 : 1.0/(v.max()-v.min());
          switch( response_transform ) {
          case STANDARDIZE:  _normRespSub[i] = v.mean();  _normRespMul[i] = vs;  break;
          case NORMALIZE:    _normRespSub[i] = v.mean();  _normRespMul[i] = vm;  break;
          case DEMEAN:       _normRespSub[i] = v.mean();                         break;
          case DESCALE:                                   _normRespMul[i] = vs;  break;
          case NONE:                                                             break;
          default:           throw H2O.unimpl();
          }
        }
      }

      train.restructure(names,tvecs2);
      valid.restructure(names,vvecs2);
      _adaptedFrame = train;
    }

    public DataInfo filterExpandedColumns(int [] cols){
      assert _predictor_transform != null;
      assert  _response_transform != null;
      if(cols == null)return this;
      int i = 0, j = 0, ignoredCnt = 0;
      //public DataInfo(Frame fr, int hasResponses, boolean useAllFactorLvls, double [] normSub, double [] normMul, double [] normRespSub, double [] normRespMul){
      int [][] catLvls = new int[_cats][];
      int [] ignoredCols = MemoryManager.malloc4(_nums + _cats);
      // first do categoricals...
      if(_catOffsets != null)
        while(i < cols.length && cols[i] < _catOffsets[_catOffsets.length-1]){
          int [] levels = MemoryManager.malloc4(_catOffsets[j+1] - _catOffsets[j]);
          int k = 0;
          while(i < cols.length && cols[i] < _catOffsets[j+1])
            levels[k++] = cols[i++]-_catOffsets[j];
          if(k > 0)
            catLvls[j] = Arrays.copyOf(levels, k);
          ++j;
        }
      for(int k =0; k < catLvls.length; ++k)
        if(catLvls[k] == null)ignoredCols[ignoredCnt++] = k;
      if(ignoredCnt > 0){
        int [][] c = new int[_cats-ignoredCnt][];
        int y = 0;
        for (int[] catLvl : catLvls) if (catLvl != null) c[y++] = catLvl;
        assert y == c.length;
        catLvls = c;
      }
      // now numerics
      int prev = j = 0;
      for(; i < cols.length; ++i){
        for(int k = prev; k < (cols[i]-numStart()); ++k ){
          ignoredCols[ignoredCnt++] = k+_cats;
          ++j;
        }
        prev = ++j;
      }
      for(int k = prev; k < _nums; ++k)
        ignoredCols[ignoredCnt++] = k+_cats;
      Frame f = new Frame(_adaptedFrame.names().clone(),_adaptedFrame.vecs().clone());
      if(ignoredCnt > 0) f.remove(Arrays.copyOf(ignoredCols,ignoredCnt));
      assert catLvls.length < f.numCols():"cats = " + catLvls.length + " numcols = " + f.numCols();
      DataInfo dinfo = new DataInfo(_key,f,catLvls, _responses, _predictor_transform, _response_transform, _foldId, _nfolds);
      dinfo._activeCols = cols;
      return dinfo;
    }
    public String toString(){
      return "";
    }
    public DataInfo getFold(int foldId, int nfolds){
      return new DataInfo(Key.make(),this, foldId, nfolds);
    }
    public final int fullN(){return _nums + _catOffsets[_cats];}
    public final int largestCat(){return _cats > 0?_catOffsets[1]:0;}
    public final int numStart(){return _catOffsets[_cats];}
    public final String [] coefNames(){
      int k = 0;
      final int n = fullN();
      String [] res = new String[n];
      final Vec [] vecs = _adaptedFrame.vecs();
      for(int i = 0; i < _cats; ++i) {
        for (int j = _useAllFactorLevels ? 0 : 1; j < vecs[i].domain().length; ++j)
          res[k++] = _adaptedFrame._names[i] + "." + vecs[i].domain()[j];
        if (vecs[i].naCnt() > 0) res[k++] = _adaptedFrame._names[i] + ".missing(NA)";
      }
      final int nums = n-k;
      System.arraycopy(_adaptedFrame._names, _cats, res, k, nums);
      return res;
    }

    /**
     * Undo the standardization/normalization of numerical columns
     * @param in input values
     * @param out output values (can be the same as input)
     */
    public final void unScaleNumericals(float[] in, float[] out) {
      if (_nums == 0) return;
      assert (in.length == out.length);
      assert (in.length == fullN());
      for (int k=numStart(); k < fullN(); ++k)
        out[k] = in[k] / (float)_normMul[k-numStart()] + (float)_normSub[k-numStart()];
    }
  }

  @Override
  public T dfork(Frame fr){
    assert fr == _dinfo._adaptedFrame;
    return super.dfork(fr);
  }

  /**
   * Override this to initialize at the beginning of chunk processing.
   */
  protected void chunkInit(){}
  /**
   * Override this to do post-chunk processing work.
   * @param n Number of processed rows
   */
  protected void chunkDone(long n){}


  /**
   * Extracts the values, applies regularization to numerics, adds appropriate offsets to categoricals,
   * and adapts response according to the CaseMode/CaseValue if set.
   */
  @Override public final void map(Chunk [] chunks, NewChunk [] outputs){
    if(_jobKey != null && !Job.isRunning(_jobKey))throw new JobCancelledException();
    final int nrows = chunks[0]._len;
    final long offset = chunks[0].start();
    chunkInit();
    double [] nums = MemoryManager.malloc8d(_dinfo._nums);
    int    [] cats = MemoryManager.malloc4(_dinfo._cats);
    double [] response = _dinfo._responses == 0 ? null : MemoryManager.malloc8d(_dinfo._responses);
    int start = 0;
    int end = nrows;

    Random skip_rng = null; //random generator for skipping rows

    //Example:
    // _useFraction = 0.8 -> 1 repeat with fraction = 0.8
    // _useFraction = 1.0 -> 1 repeat with fraction = 1.0
    // _useFraction = 1.1 -> 2 repeats with fraction = 0.55
    // _useFraction = 2.1 -> 3 repeats with fraction = 0.7
    // _useFraction = 3.0 -> 3 repeats with fraction = 1.0
    final int repeats = (int)Math.ceil(_useFraction);
    final float fraction = _useFraction / repeats;

    if (fraction < 1.0) {
      skip_rng = RandomUtils.getDeterRNG(new Random().nextLong());
    }

    long[] shuf_map = null;
    if (_shuffle) {
      shuf_map = new long[end-start];
      for (int i=0;i<shuf_map.length;++i)
        shuf_map[i] = start + i;
      ArrayUtils.shuffleArray(shuf_map, new Random().nextLong());
    }
    long num_processed_rows = 0;
    for(int rrr = 0; rrr < repeats; ++rrr) {
      OUTER:
      for(int rr = start; rr < end; ++rr){
        final int r = shuf_map != null ? (int)shuf_map[rr-start] : rr;
        final long lr = r + chunks[0].start();
        if ((_dinfo._nfolds > 0 && (lr % _dinfo._nfolds) == _dinfo._foldId)
          || (skip_rng != null && skip_rng.nextFloat() > fraction))continue;
        ++num_processed_rows; //count rows with missing values even if they are skipped
        for(Chunk c:chunks)if(skipMissing() && c.isNA0(r))continue OUTER; // skip rows with NAs!
        int i = 0, ncats = 0;
        for(; i < _dinfo._cats; ++i){
          int c;
          if (chunks[i].isNA0(r)) {
            cats[ncats++] = (_dinfo._catOffsets[i+1]-1); //missing value turns into extra (last) factor
          } else {
            c = (int) chunks[i].at80(r);
            if (_dinfo._catLvls != null) { // some levels are ignored?
              c = Arrays.binarySearch(_dinfo._catLvls[i], c);
              if (c >= 0)
                cats[ncats++] = c + _dinfo._catOffsets[i];
            } else if (_dinfo._useAllFactorLevels)
              cats[ncats++] = c + _dinfo._catOffsets[i];
            else if (c != 0)
              cats[ncats++] = c + _dinfo._catOffsets[i] - 1;
          }
        }
        final int n = chunks.length-_dinfo._responses;
        for(;i < n;++i){
          double d = chunks[i].at0(r); //can be NA if skipMissing() == false
          if(_dinfo._normMul != null) d = (d - _dinfo._normSub[i-_dinfo._cats])*_dinfo._normMul[i-_dinfo._cats];
          nums[i-_dinfo._cats] = d;
        }
        for(i = 0; i < _dinfo._responses; ++i) {
          response[i] = chunks[chunks.length-_dinfo._responses + i].at0(r);
          if (_dinfo._normRespMul != null) response[i] = (response[i] - _dinfo._normRespSub[i])*_dinfo._normRespMul[i];
          if(Double.isNaN(response[i]))continue OUTER; // skip rows without a valid response (no supervised training possible)
        }
        long seed = offset + rrr*(end-start) + r;
        if (outputs != null && outputs.length > 0)
          processRow(seed, nums, ncats, cats, response, outputs);
        else
          processRow(seed, nums, ncats, cats, response);
      }
    }
    chunkDone(num_processed_rows);
  }

}

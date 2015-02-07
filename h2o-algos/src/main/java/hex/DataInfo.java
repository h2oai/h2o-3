package hex;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.Arrays;

/**
* Created by tomasnykodym on 1/29/15.
*/
public class DataInfo extends Keyed {
  public int [] _activeCols;
  public Frame _adaptedFrame;
  public int _responses; // number of responses



  @Override protected long checksum_impl() {throw H2O.unimpl();} // don't really need checksum

  public enum TransformType { NONE, STANDARDIZE, NORMALIZE, DEMEAN, DESCALE }
  public TransformType _predictor_transform;
  public TransformType _response_transform;
  public boolean _useAllFactorLevels;
  public int _nums;
  public int _bins;
  public int _cats;
  public int [] _catOffsets;
  public int [] _catMissing;
  public double [] _normMul;
  public double [] _normSub;
  public double [] _normRespMul;
  public double [] _normRespSub;
  public int _foldId;
  public int _nfolds;
  public boolean _intercept = true;
  public boolean _offset = false;
  final boolean _skipMissing;
  final int [][] _catLvls;


  public double _etaOffset;
  public DataInfo deep_clone() {
    AutoBuffer ab = new AutoBuffer();
    this.write(ab);
    ab.flipForReading();
    return (DataInfo)new DataInfo().read(ab);
  }

  private DataInfo() {super(null);_catLvls = null; _skipMissing = true;}

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
    _skipMissing = dinfo._skipMissing;
    if(_normMul != null)
      for(int i = 0; i < _normMul.length; ++i)
        _etaOffset -= _normSub[i] * _normMul[i];
  }


//  public DataInfo(Key selfKey, Frame train, Frame valid, int hasResponses, boolean useAllFactorLvls, double[] normSub, double[] normMul, TransformType predictor_transform, double[] normRespSub, double[] normRespMul){
//    this(selfKey, train, valid, hasResponses,useAllFactorLvls,
//      normMul != null && normSub != null ? predictor_transform : TransformType.NONE, //just allocate, doesn't matter whether standardize or normalize is used (will be overwritten below)
//      normRespMul != null && normRespSub != null ? TransformType.STANDARDIZE : TransformType.NONE);
//    assert predictor_transform != null;
//    assert (normSub == null) == (normMul == null);
//    assert (normRespSub == null) == (normRespMul == null);
//    if(normSub != null) {
//      System.arraycopy(normSub, 0, _normSub, 0, normSub.length);
//      System.arraycopy(normMul, 0, _normMul, 0, normMul.length);
//    }
//    if(normRespSub != null) {
//      System.arraycopy(normRespSub, 0, _normRespSub, 0, normRespSub.length);
//      System.arraycopy(normRespMul, 0, _normRespMul, 0, normRespMul.length);
//    }
//  }
//
//
//  public DataInfo(Key selfKey, Frame train, Frame valid, int nResponses, boolean useAllFactors, TransformType predictor_transform) {
//    this(selfKey, train, valid, nResponses, useAllFactors, predictor_transform, TransformType.NONE);
//  }

  //new DataInfo(f,catLvls, _responses, _standardize, _response_transform);
  public DataInfo(Key selfKey, Frame fr, int[][] catLevels, int responses, TransformType predictor_transform, TransformType response_transform, boolean skipMissing, int foldId, int nfolds){
    super(selfKey);
    assert predictor_transform != null;
    assert  response_transform != null;
    _predictor_transform = predictor_transform;
    _response_transform  =  response_transform;
    _skipMissing = skipMissing;
    _adaptedFrame = fr;
    _catOffsets = MemoryManager.malloc4(catLevels.length + 1);
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
  public DataInfo(Key selfKey, Frame train, Frame valid, int nResponses, boolean useAllFactorLevels, TransformType predictor_transform, TransformType response_transform, boolean skipMissing) {
    super(selfKey);
    assert predictor_transform != null;
    assert  response_transform != null;
    _skipMissing = skipMissing;
    _nfolds = _foldId = 0;
    _predictor_transform = predictor_transform;
    _response_transform = response_transform;
    _responses = nResponses;
    _useAllFactorLevels = useAllFactorLevels;
    _catLvls = null;
    final Vec[] tvecs = train.vecs();
    final Vec[] vvecs = (valid == null) ? null : valid.vecs();

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
    Vec[] vvecs2 = (valid == null) ? null : new Vec[train.numCols()];
    // Compute the cardinality of each cat
    _catOffsets = MemoryManager.malloc4(ncats+1);
    _catMissing = new int[ncats];
    int len = _catOffsets[0] = 0;
    for(int i = 0; i < ncats; ++i) {
      names[i]  =   train._names[cats[i]];
      if (valid != null) vvecs2         [i] = vvecs[cats[i]];
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
      if (valid != null) vvecs2         [ncats+i] = vvecs[nums[i]];
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
        if (valid != null) vvecs2         [ncats+nnums+i] = vvecs[ncats+nnums+i];
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
    if (valid != null) valid.restructure(names,vvecs2);
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
    DataInfo dinfo = new DataInfo(_key,f,catLvls, _responses, _predictor_transform, _response_transform, _skipMissing, _foldId, _nfolds);
    // do not put activeData into K/V - active data is recreated on each node based on active columns
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

  public class Row {
    public boolean good;
    public double [] numVals;
    public double [] response;
    public int    [] numIds;
    public int    [] binIds;

    public int       nBins;
    public int       nNums;
    public final double etaOffset;

    public final boolean isSparse(){return numIds != null;}

    public Row(boolean sparse, int nNums, int nBins, int nresponses, double etaOffset) {
      binIds = MemoryManager.malloc4(nBins);
      numVals = MemoryManager.malloc8d(nNums);
      response = MemoryManager.malloc8d(nresponses);
      if(sparse)
        numIds = MemoryManager.malloc4(nNums);
      this.etaOffset = etaOffset;
      this.nNums = nNums;
    }

    public double response(int i) {return response[i];}

    public void addBinId(int id) {
      if(binIds.length == nBins)
        binIds = Arrays.copyOf(binIds,Math.max(4, (binIds.length + (binIds.length >> 1))));
      binIds[nBins++] = id;
    }
    public void addNum(int id, double val) {
      if(numIds != null) {
        if(numIds.length == nNums) {
          int newSz = Math.max(4,numIds.length + (numIds.length >> 1));
          numIds = Arrays.copyOf(numIds, newSz);
          numVals = Arrays.copyOf(numVals, newSz);
        }
        int i = nNums++;
        numIds[i] = id;
        numVals[i] = val;
      } else
        numVals[id] = val;
    }


    public double innerProduct(double [] vec) {
      double res = 0;
      int numStart = numStart();
      for(int i = 0; i < nBins; ++i)
        res += vec[binIds[i]];
      if(numIds == null) {
        for (int i = 0; i < numVals.length; ++i)
          res += numVals[i] * vec[numStart + i];
      } else {
        res = etaOffset;
        for (int i = 0; i < nNums; ++i)
          res += numVals[i] * vec[numIds[i]];
      }
      if(_intercept)
        res += vec[vec.length-1];
      return res;
    }

    public String toString() {
      return Arrays.toString(Arrays.copyOf(binIds,nBins)) + ", " + Arrays.toString(numVals);
    }
  }

  public final int getCategoricalId(int cid, int val) {
    if (_catLvls != null) { // some levels are ignored?
      int c = Arrays.binarySearch(_catLvls[cid], val);
      if (c >= 0)
        return c + _catOffsets[cid];
    } else if (_useAllFactorLevels)
      return val + _catOffsets[cid];
    return val + _catOffsets[cid] - 1;
  }

  public final Row extractDenseRow(Chunk[] chunks, int rid, Row row) {
    if (_skipMissing)
      for (Chunk c : chunks)
        if(c.isNA(rid)) {
          row.good = false;
          return row;
        }
    int nbins = 0;
    for (int i = 0; i < _cats; ++i) {
      if (chunks[i].isNA(rid)) {
          row.binIds[nbins++] = _catOffsets[i + 1] - 1; // missing value turns into extra (last) factor
      } else {
        int c = getCategoricalId(i,(int)chunks[i].at8(rid));
        if(c >= 0)
          row.binIds[nbins++] = c;
      }
    }
    row.nBins = nbins;
    final int n = _nums;
    for (int i = 0; i < n; ++i) {
      double d = chunks[_cats + i].atd(rid); // can be NA if skipMissing() == false
      if (_normMul != null)
        d = (d - _normSub[i]) * _normMul[i];
      row.numVals[i] = d;
    }
    for (int i = 0; i < _responses; ++i) {
      row.response[i] = chunks[chunks.length - _responses + i].atd(rid);
      if (_normRespMul != null)
        row.response[i] = (row.response[i] - _normRespSub[i]) * _normRespMul[i];
      if (Double.isNaN(row.response[i])) {
        row.good = false;
        return row;
      }
    }
    row.good = true;
    return row;
  }
  public Row newDenseRow(){
    return new Row(false,_nums,_cats,_responses,0);
  }
  /**
   * Extract (sparse) rows from given chunks.
   * Essentially turns the dataset 90 degrees.
   * @param chunks
   * @return
   */
  public final Row[]  extractSparseRows(Chunk [] chunks, double [] beta) {
    if(!_skipMissing) // treat as categorical?
      throw H2O.unimpl();
    Row[] rows = new Row[chunks[0]._len];
    double etaOffset = 0;
    if(_normMul != null)
      for(int i = 0; i < _nums; ++i)
        etaOffset -= beta[i] * _normSub[i] * _normMul[i];
    for (int i = 0; i < rows.length; ++i)
      rows[i] = new Row(true, Math.max(_bins >> 10, 16) + _cats, Math.max(16, (_nums - _bins) >> 10), _responses, etaOffset);
    // categoricals
    for (int i = 0; i < _cats; ++i) {
      for (int r = 0; r < chunks[0]._len; ++r) {
        Row row = rows[r];
        if (row.good) continue;
        if (chunks[i].isNA(r)) {
          if (_skipMissing) {
            row.good = true;
            continue;
          }
          row.binIds[row.nBins++] = _catOffsets[i + 1] - 1; // missing value turns into extra (last) factor
        } else {
          int c = getCategoricalId(i,(int)chunks[i].at8(r));
          if(c >=0)
            row.binIds[row.nBins++] = c;
        }
      }
    }
    int numStart = numStart();
    // binary cols
    for (int cid = 0; cid < _bins; ++cid) {
      Chunk c = chunks[cid + _cats];
      for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
        Row row = rows[r];
        if (row.good) continue;
        if (c.isNA(r)) {
          row.good = _skipMissing;
          continue;
        }
        row.addBinId(cid + numStart);
      }
    }
    // generic numbers
    for (int cid = 0; cid < _nums; ++cid) {
      Chunk c = chunks[_cats + cid];
      for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
        Row row = rows[r];
        if (c.isNA(r)) row.good = _skipMissing;
        if (row.good) continue;
        if(_normMul != null) // no centering here, we already have etaOffset
          row.addNum(cid + numStart + _bins, c.atd(r) * _normMul[cid]);
      }
    }
    // response(s)
    for (int r = 0; r < chunks[0]._len; ++r) {
      Row row = rows[r];
      for (int i = 1; i <= _responses; ++i) {
        row.response[row.response.length - i] = chunks[chunks.length - 1].atd(r);
        if (_normRespMul != null)
          row.response[i] = (row.response[i] - _normRespSub[i]) * _normRespMul[i];
        if (Double.isNaN(row.response[row.response.length - i]))
          row.good = true;
      }
    }
    return rows;
  }
}

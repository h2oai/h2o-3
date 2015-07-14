package hex;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Arrays;

/**
* Created by tomasnykodym on 1/29/15.
 *
 * Provides higher level interface for accessing data row-wise.
 *
 * Performs on the fly auto-expansion of categorical variables (to 1 hot encoding) and standardization ( or normalize/demean/descale/none) of predictors and response.
 * Supports sparse data, sparse columns can be transformed to sparse rows on the fly with some (significant) memory overhead,
 * as the data of the whole chunk(s) will be copied.
 *
*/
public class DataInfo extends Keyed {
  public int [] _activeCols;
  public Frame _adaptedFrame;
  public int _responses;   // number of responses

  public enum TransformType {
    NONE, STANDARDIZE, NORMALIZE, DEMEAN, DESCALE;

    public boolean isMeanAdjusted(){
      switch(this){
        case NONE:
        case DESCALE:
        case NORMALIZE:
          return false;
        case STANDARDIZE:
        case DEMEAN:
          return true;
        default:
          throw H2O.unimpl();
      }
    }
    public boolean isSigmaScaled(){
      switch(this){
        case NONE:
        case DEMEAN:
        case NORMALIZE:
          return false;
        case STANDARDIZE:
        case DESCALE:
          return true;
        default:
          throw H2O.unimpl();
      }
    }
  }
  public TransformType _predictor_transform;
  public TransformType _response_transform;
  public boolean _useAllFactorLevels;
  public int _nums;
  public int _bins;
  public int _cats;
  public int [] _catOffsets;
  public int [] _catMissing;  // bucket for missing categoricals
  public int [] _permutation; // permutation matrix mapping input col indices to adaptedFrame
  public double [] _normMul;
  public double [] _normSub;
  public double [] _normRespMul;
  public double [] _normRespSub;
  public boolean _intercept = true;
  public final boolean _offset;
  public final boolean _weights;
  public int responseChunkId(){return _cats + _nums + (_weights?1:0) + (_offset?1:0);}
  public int offsetChunkId(){return _cats + _nums + (_weights ?1:0);}
  public int weightChunkId(){return _cats + _nums;}
  public final boolean _skipMissing;
  public boolean _valid; // DataInfo over validation data set, can have unseen (unmapped) categorical levels
  final int [][] _catLvls;

  @Override protected long checksum_impl() {throw H2O.unimpl();} // don't really need checksum

  private DataInfo() {super(null);_catLvls = null; _skipMissing = true; _valid = false; _offset = false; _weights = false; }

  public DataInfo deep_clone() {
    AutoBuffer ab = new AutoBuffer();
    this.write(ab);
    ab.flipForReading();
    return (DataInfo)new DataInfo().read(ab);
  }
  // make *empty* data info for numeric only datasets with no regularization
  public static DataInfo makeEmpty(int fullN) {
    return new DataInfo(fullN);
  }

  private DataInfo(int nums) {
    _offset = false;
    _weights = false;
    _nums = nums;
    _catOffsets = new int[]{0};
    _predictor_transform = TransformType.NONE;
    _response_transform = TransformType.NONE;
    _skipMissing = true;
    _catLvls = null;
  }
  // Modify the train & valid frames directly; sort the categorical columns
  // up front according to size; compute the mean/sigma for each column for
  // later normalization.
  public DataInfo(Key selfKey, Frame train, Frame valid, int nResponses, boolean useAllFactorLevels, TransformType predictor_transform, TransformType response_transform, boolean skipMissing, boolean missingBucket, boolean weight, boolean offset) {
    super(selfKey);
    _valid = false;
    assert predictor_transform != null;
    assert  response_transform != null;
    _offset = offset;
    _weights = weight;
    _skipMissing = skipMissing;
    _predictor_transform = predictor_transform;
    _response_transform = response_transform;
    _responses = nResponses;
    _useAllFactorLevels = useAllFactorLevels;
    _catLvls = null;
    _permutation = new int[train.numCols()];
    final Vec[] tvecs = train.vecs();
    final Vec[] vvecs = (valid == null) ? null : valid.vecs();

    // Count categorical-vs-numerical
    final int n = tvecs.length-_responses - (offset?1:0) - (weight?1:0);
    int [] nums = MemoryManager.malloc4(n);
    int [] cats = MemoryManager.malloc4(n);
    int nnums = 0, ncats = 0;
    for(int i = 0; i < n; ++i)
      if (tvecs[i].isEnum())
        cats[ncats++] = i;
      else
        nums[nnums++] = i;
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
    // Compute the cardinality of each cat
    _catOffsets = MemoryManager.malloc4(ncats+1);
    _catMissing = new int[ncats];
    int len = _catOffsets[0] = 0;
    for(int i = 0; i < ncats; ++i) {
      _permutation[i] = cats[i];
      names[i]  =   train._names[cats[i]];
      Vec v = (tvecs2[i] = tvecs[cats[i]]);
      _catMissing[i] = missingBucket ? 1 : 0; //needed for test time
      _catOffsets[i+1] = (len += v.domain().length - (useAllFactorLevels?0:1) + (missingBucket ? 1 : 0)); //missing values turn into a new factor level
    }
    for(int i = 0; i < _nums; ++i){
      names[i+_cats] = train._names[nums[i]];
      tvecs2[i+_cats] = train.vec(nums[i]);
      _permutation[i+_cats] = nums[i];
    }
    for(int i = names.length-nResponses - (weight?1:0) - (offset?1:0); i < names.length; ++i) {
      names[i] = train._names[i];
      tvecs2[i] = train.vec(i);
    }
    _adaptedFrame = new Frame(names,tvecs2);
    train.restructure(names,tvecs2);
    if (valid != null)
      valid.restructure(names,valid.vecs(names));
//    _adaptedFrame = train;
    setPredictorTransform(predictor_transform);
    if(_responses > 0)
      setResponseTransform(response_transform);
  }

  public DataInfo(Key selfKey, Frame train, Frame valid, int nResponses, boolean useAllFactorLevels, TransformType predictor_transform, TransformType response_transform, boolean skipMissing, boolean missingBucket, boolean weight, boolean offset, boolean intercept) {
    this(selfKey, train, valid, nResponses, useAllFactorLevels, predictor_transform, response_transform, skipMissing, missingBucket, weight, offset);
    _intercept = intercept;
  }

  public DataInfo validDinfo(Frame valid) {
    DataInfo res = new DataInfo(Key.make(),_adaptedFrame,null,1,_useAllFactorLevels,TransformType.NONE,TransformType.NONE,_skipMissing,false, _weights,_offset);
    res._adaptedFrame = new Frame(_adaptedFrame.names(),valid.vecs(_adaptedFrame.names()));
    res._valid = true;
    return res;
  }

  public double[] denormalizeBeta(double [] beta) {
    assert beta.length == fullN()+1;
    beta = MemoryManager.arrayCopyOf(beta,beta.length);
    if (_predictor_transform == DataInfo.TransformType.STANDARDIZE) {
      double norm = 0.0;        // Reverse any normalization on the intercept
      // denormalize only the numeric coefs (categoricals are not normalized)
      final int numoff = numStart();
      for (int i = numoff; i < beta.length - 1; i++) {
        double b = beta[i] * _normMul[i - numoff];
        norm += b * _normSub[i - numoff]; // Also accumulate the intercept adjustment
        beta[i] = b;
      }
      beta[beta.length-1] -= norm;
    }
    return beta;
  }

  // private constructor called by filterExpandedColumns
  private DataInfo(Key selfKey, Frame fr, int[][] catLevels, int responses, TransformType predictor_transform, TransformType response_transform, boolean skipMissing, boolean weight, boolean offset){
    super(selfKey);
    _offset = offset;
    _weights = weight;
    _valid = false;
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
    _nums = fr.numCols()-_cats - responses - (_offset?1:0) - (_weights?1:0);
    _useAllFactorLevels = true;
    setPredictorTransform(predictor_transform);
    setResponseTransform(response_transform);
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
    DataInfo dinfo = new DataInfo(_key,f,catLvls, _responses, _predictor_transform, _response_transform, _skipMissing, _weights, _offset);
    // do not put activeData into K/V - active data is recreated on each node based on active columns
    dinfo._activeCols = cols;
    return dinfo;
  }

  public void updateWeightedSigmaAndMean(double [] sigmas, double [] mean) {
    if(_predictor_transform.isSigmaScaled()) {
      if(sigmas.length != _normMul.length)
        throw new IllegalArgumentException("Length of sigmas does not match number of scaled columns.");
      for(int i = 0; i < sigmas.length; ++i)
        _normMul[i] = sigmas[i] != 0?1.0/sigmas[i]:1;
    }
    if(_predictor_transform.isMeanAdjusted()) {
      if(sigmas.length != _normMul.length)
        throw new IllegalArgumentException("Length of sigmas does not match number of scaled columns.");
      for(int i = 0; i < sigmas.length; ++i)
        _normSub[i] = mean[i];
    }
  }

  private void setTransform(TransformType t, double [] normMul, double [] normSub, int vecStart, int n) {
    for (int i = 0; i < n; ++i) {
      Vec v = _adaptedFrame.vec(vecStart + i);
      switch (t) {
        case STANDARDIZE:
          normMul[i] = (v.sigma() != 0)?1.0/v.sigma():1.0;
          normSub[i] = v.mean();
          break;
        case NORMALIZE:
          normMul[i] = (v.max() - v.min() > 0)?1.0/(v.max() - v.min()):1.0;
          normSub[i] = v.mean();
          break;
        case DEMEAN:
          normMul[i] = 1;
          normSub[i] = v.mean();
          break;
        case DESCALE:
          normMul[i] = (v.sigma() != 0)?1.0/v.sigma():1.0;
          normSub[i] = 0;
          break;
        default:
          throw H2O.unimpl();
      }
      assert !Double.isNaN(normMul[i]);
      assert !Double.isNaN(normSub[i]);
    }
  }
  public void setPredictorTransform(TransformType t){
    _predictor_transform = t;
    if(t == TransformType.NONE) {
      _normMul = null;
      _normSub = null;
    } else {
      _normMul = MemoryManager.malloc8d(_nums);
      _normSub = MemoryManager.malloc8d(_nums);
      setTransform(t,_normMul,_normSub,_cats,_nums);
    }
  }

  public void setResponseTransform(TransformType t){
    _response_transform = t;
    if(t == TransformType.NONE) {
      _normRespMul = null;
      _normRespSub = null;
    } else {
      _normRespMul = MemoryManager.malloc8d(_responses);
      _normRespSub = MemoryManager.malloc8d(_responses);
      setTransform(t,_normRespMul,_normRespSub,_adaptedFrame.numCols()-_responses,_responses);
    }
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
      if (_catMissing[i] > 0) res[k++] = _adaptedFrame._names[i] + ".missing(NA)";
    }
    final int nums = n-k;
    System.arraycopy(_adaptedFrame._names, _cats, res, k, nums);
    return res;
  }

  // Return permutation matrix mapping input names to adaptedFrame colnames
  public int[] mapNames(String[] names) {
    assert names.length == _adaptedFrame._names.length : "Names must be the same length!";
    int[] idx = new int[names.length];
    Arrays.fill(idx, -1);

    for(int i = 0; i < _adaptedFrame._names.length; i++) {
      for(int j = 0; j < names.length; j++) {
        if( names[j].equals(_adaptedFrame.name(i)) ) {
          idx[i] = j; break;
        }
      }
    }
    return idx;
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

  public final class Row {
    public boolean bad;
    public double [] numVals;
    public double [] response;
    public int    [] numIds;
    public int    [] binIds;
    public long      rid;
    public int       nBins;
    public int       nNums;
    public double    offset = 0;
    public double    weight = 1;
    public final double etaOffset;

    public final boolean isSparse(){return numIds != null;}

    public Row(boolean sparse, int nNums, int nBins, int nresponses, double etaOffset) {
      binIds = MemoryManager.malloc4(nBins);
      numVals = MemoryManager.malloc8d(nNums);
      response = MemoryManager.malloc8d(nresponses);
      if(sparse)
        numIds = MemoryManager.malloc4(nNums);
      this.etaOffset = etaOffset;
      this.nNums = sparse?0:nNums;
    }

    public Row(boolean sparse, double[] numVals, int[] binIds, double[] response, double etaOffset) {
      int nNums = numVals == null ? 0:numVals.length;
      this.numVals = numVals;
      if(sparse)
        numIds = MemoryManager.malloc4(nNums);
      this.etaOffset = etaOffset;
      this.nNums = sparse ? 0:nNums;
      this.nBins = binIds == null ? 0:binIds.length;
      this.binIds = binIds;
      this.response = response;
    }

    public double response(int i) {return response[i];}

    public double get(int i) {
      int off = numStart();
      if(i >= off) { // numbers
        if(numIds == null)
          return numVals[i-off];
        int j = Arrays.binarySearch(numIds,i);
        return j >= 0?numVals[j]:0;
      } else { // categoricvasl
        int j = Arrays.binarySearch(binIds,i);
        return j >= 0?1:0;
      }
    }

    public void addBinId(int id) {
      if(binIds.length == nBins)
        binIds = Arrays.copyOf(binIds,Math.max(4, (binIds.length + (binIds.length >> 1))));
      binIds[nBins++] = id;
    }
    public void addNum(int id, double val) {
      if(numIds.length == nNums) {
        int newSz = Math.max(4,numIds.length + (numIds.length >> 1));
        numIds = Arrays.copyOf(numIds, newSz);
        numVals = Arrays.copyOf(numVals, newSz);
      }
      int i = nNums++;
      numIds[i] = id;
      numVals[i] = val;
    }

    public final double innerProduct(double [] vec) {
      double res = 0;
      int numStart = numStart();
      for(int i = 0; i < nBins; ++i)
        res += vec[binIds[i]];
      if(numIds == null) {
        for (int i = 0; i < numVals.length; ++i)
          res += numVals[i] * vec[numStart + i];
      } else {
        res += etaOffset;
        for (int i = 0; i < nNums; ++i)
          res += numVals[i] * vec[numIds[i]];
      }
      if(_intercept)
        res += vec[vec.length-1];
      return res;
    }

    public String toString() {
      return this.rid + Arrays.toString(Arrays.copyOf(binIds,nBins)) + ", " + Arrays.toString(numVals);
    }
  }

  public final int getCategoricalId(int cid, int val) {
    final int c;
    if (_catLvls != null)  // some levels are ignored?
      c = Arrays.binarySearch(_catLvls[cid], val);
    else c = val - (_useAllFactorLevels?0:1);
    if( c < 0) return -1;
    int v = c + _catOffsets[cid];
    if(v >= _catOffsets[cid+1]) { // previously unseen level
      assert _valid:"categorical value out of bounds, got " + v + ", next cat starts at " + _catOffsets[cid+1];
      return -2;
    }
    return v;
  }

  public final Row extractDenseRow(Chunk[] chunks, int rid, Row row) {
    row.bad = false;
    row.rid = rid + chunks[0].start();
    if (_skipMissing)
      for (Chunk c : chunks)
        if(c.isNA(rid)) {
          row.bad = true;
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
      if (_normMul != null && _normSub != null)
        d = (d - _normSub[i]) * _normMul[i];
      row.numVals[i] = d;
    }
    for (int i = 0; i < _responses; ++i) {
      row.response[i] = chunks[responseChunkId()].atd(rid);
      if (_normRespMul != null)
        row.response[i] = (row.response[i] - _normRespSub[i]) * _normRespMul[i];
      if (Double.isNaN(row.response[i])) {
        row.bad = true;
        return row;
      }
    }
    if(_offset)
      row.offset = chunks[offsetChunkId()].atd(rid);
    if(_weights)
      row.weight = chunks[weightChunkId()].atd(rid);
    return row;
  }
  public Row newDenseRow(){
    return new Row(false,_nums,_cats,_responses,0);
  }
  public Row newDenseRow(double[] numVals) {
    return new Row(false, numVals, null, null, 0);
  }
  public double computeSparseOffset(double [] coefficients) {
    double etaOffset = 0;
    if(_normMul != null && _normSub != null && coefficients != null)
      for(int i = 0; i < _nums; ++i)
        etaOffset -= coefficients[i+numStart()] * _normSub[i] * _normMul[i];
    return etaOffset;
  }
  /**
   * Extract (sparse) rows from given chunks.
   * Essentially turns the dataset 90 degrees.
   * @param chunks - chunk of dataset
   * @param offset - adjustment for 0s if running with on-the-fly standardization (i.e. zeros are not really zeros because of centering)
   * @return array of sparse rows
   */
  public final Row[]  extractSparseRows(Chunk [] chunks, double offset) {
    if(!_skipMissing)  throw H2O.unimpl();
    Row[] rows = new Row[chunks[0]._len];

    for (int i = 0; i < rows.length; ++i) {
      rows[i] = new Row(true, Math.min(_nums - _bins, 16), Math.min(_bins, 16) + _cats, _responses, offset);
      rows[i].rid = chunks[0].start() + i;
      if(_offset)  {
        rows[i].offset = chunks[offsetChunkId()].atd(i);
        if(Double.isNaN(rows[i].offset)) rows[i].bad = true;
      }
      if(_weights) {
        rows[i].weight = chunks[weightChunkId()].atd(i);
        if(Double.isNaN(rows[i].weight)) rows[i].bad = true;
      }
    }
    // categoricals
    for (int i = 0; i < _cats; ++i) {
      for (int r = 0; r < chunks[0]._len; ++r) {
        Row row = rows[r];
        if(row.bad)continue;
        if (chunks[i].isNA(r)) {
          if (_skipMissing) {
            row.bad = true;
          } else
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
        if(!c.isSparse() && c.atd(r) == 0)continue;
        Row row = rows[r];
        if (row.bad) continue;
        if (c.isNA(r))
          row.bad = _skipMissing;
        row.addBinId(cid + numStart);
      }
    }
    // generic numbers
    for (int cid = 0; cid < _nums; ++cid) {
      Chunk c = chunks[_cats + cid];
      int oldRow = -1;
      for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
        if(!c.isSparse() && c.atd(r) == 0)continue;
        assert r > oldRow;
        oldRow = r;
        Row row = rows[r];
        if (row.bad) continue;
        if (c.isNA(r)) row.bad = _skipMissing;
        double d = c.atd(r);
        if(_normMul != null)
          d *= _normMul[cid]; // no centering here, we already have etaOffset
        row.addNum(cid + numStart + _bins, d);
      }
    }
    // response(s)
    for (int i = 1; i <= _responses; ++i) {
      Chunk rChunk = chunks[responseChunkId()];
      for (int r = 0; r < chunks[0]._len; ++r) {
        Row row = rows[r];
        if(row.bad) continue;
        row.response[row.response.length - i] = rChunk.atd(r);
        if (_normRespMul != null) {
          assert false;
          row.response[i] = (row.response[i] - _normRespSub[i]) * _normRespMul[i];
        }
        if (Double.isNaN(row.response[row.response.length - i]))
          row.bad = true;
      }
    }
    return rows;
  }
}

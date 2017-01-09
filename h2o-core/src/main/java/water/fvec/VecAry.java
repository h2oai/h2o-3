package water.fvec;

import water.*;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Iterator;


/**
 * Created by tomas on 10/5/16.
 */
public class VecAry extends AVecAry {
  int [] _colFilter; // positive, negative or permuted positive column filter or null
  int [] _blockOffset;
  // permutation  ChunkAry id -> (flattened) Vec[] id.

  public int _numCols;
  public long _numRows;

  public int numCols(){return _numCols;}

  private AVecAry replaceWith(VecAry newSelf){
    _vecIds = newSelf._vecIds;
    _colFilter = newSelf._colFilter;
    _vecs = newSelf._vecs;
    _key = newSelf._key;
    _blockOffset = newSelf._blockOffset;
    _numCols = newSelf._numCols;
    _numRows = newSelf._numRows;
    return this;
  }



  public VecAry(Key<Vec> k, int rowLayout, int numCols){
    super(k,rowLayout,numCols);
  }

  public VecAry(Vec... v){
    _vecIds = new int[0];
    _blockOffset = new int[]{0};
    if(v.length == 0){
      _rowLayout = -1;
    } else {
      _rowLayout = v[0]._rowLayout;
      _key =  v[0].groupKey();
      _numRows = v[0].length();
      for (int i = 0; i < v.length; ++i)
        append(v[i]);
      assert _vecIds.length > 0;
    }
  }
  private VecAry(Vec v, int... ids){
    _key = v.groupKey();
    _vecIds = new int[]{v.vecId()};
    _colFilter = ids;
    _numCols = _colFilter.length;
    _rowLayout = v._rowLayout;
    _numRows = v.numRows();
    _blockOffset = new int[]{0,v.numCols()};
  }
  private VecAry(Key groupKey, long numRows, int rowLayout,int numCols, int [] vecIds, int [] colFilter){
    _key = groupKey;
    _numRows = numRows;
    _rowLayout = rowLayout;
    _numCols = numCols;
    _vecIds = vecIds;
    _colFilter = colFilter;
    assert _vecIds.length > 0;
  }



  public RollupsAry rollupStats(boolean computeHisto) {
    Futures fs = new Futures();
    for(Vec v:vecs()) v.startRollupStats(fs,computeHisto);
    RollupStats [] rs = new RollupStats[_blockOffset[_blockOffset.length-1]];
    int i = 0;
    for(Vec v:vecs()){
      RollupsAry rsa = v.rollupStats(computeHisto);
      for(int j = 0; j < v.numCols(); ++j)
        rs[i++] = rsa.isRemoved(j)?null:rsa.getRollups(j);
    }
    if(_colFilter != null) rs = ArrayUtils.select(rs,_colFilter);
    for(int j = 0 ; j < rs.length; ++j)
      if(rs[j] == null)
        throw new IllegalArgumentException("accessing rollups of a removed vec!");
    return new RollupsAry(rs);
  }

  @Override public long length(){return vecs()[0].length();}
  @Override long chunk2StartElem( int cidx ) { return _vecs[0].chunk2StartElem(cidx); }




  @Override public DBlock chunkIdx(int cidx){
    if(_numCols == 1){ // needed for wrapped vecs, otherwise should not be used
      ChunkAry cary = chunkForChunkIdx(cidx);
      return cary.getChunk(0);
    }
    throw new UnsupportedOperationException();
  }


  @Override
  public ChunkAry chunkForChunkIdx(int cidx) {
    fetchVecs();
    if(_vecIds.length == 1) {
      if(_colFilter == null) return _vecs[0].chunkForChunkIdx(cidx);
      DBlock db = _vecs[0].chunkIdx(cidx);
      assert db instanceof DBlock.MultiChunkBlock; // only one vec, so either no colFilter or must be multichunk
      return new ChunkAry(this,cidx,ArrayUtils.select(((DBlock.MultiChunkBlock) db)._cs,_colFilter),null);
    } else {
      int s = 0;
      Chunk [] csall = new Chunk[_blockOffset[_blockOffset.length - 1]];
      for (int i = 0; i < _vecIds.length; ++i) {
        DBlock db = _vecs[i].chunkIdx(cidx);
        if (db instanceof Chunk) {
          csall[s++] = (Chunk) db;
        } else if (db instanceof DBlock.MultiChunkBlock) {
          int n = _vecs[i].numCols();
          System.arraycopy(((DBlock.MultiChunkBlock) db)._cs, 0, csall, s, n);
          s += n;
        } else
          throw H2O.unimpl();
      }
      return new ChunkAry(this,cidx,_colFilter == null?csall:ArrayUtils.select(csall,_colFilter),null);
    }
  }

  @Override public byte getType(int c){
    if(_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVecs()[vecId].getType(off);
  }


  @Override protected byte setType(int c, byte t){
    if(_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVecs()[vecId].setType(off,t);
  }

  public String[] domain(int c) {
    if(_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVecs()[vecId].domain(off);
  }

  @Override
  public RollupsAry tryFetchRollups(){
    if(_vecIds == null || _vecIds.length == 0)
      return null;
    RollupStats [] rs = new RollupStats[numCols()];
    int i = 0;
    for(Vec v:vecs()){
      RollupsAry rsa = v.tryFetchRollups();
      if(rsa == null) return null;
      for(int j = 0; j < rsa.numCols(); ++j)
        rs[i++] = rsa.getRollups(j);
    }
    if(_colFilter != null) rs = ArrayUtils.select(rs,_colFilter);
    return new RollupsAry(rs);
  }

  @Override public String toString() {
    if(_vecIds == null || _vecIds.length == 0)
      return "[empty]";
    return super.toString();
  }
  @Override
  public Vec setDomain(int c, String [] domain) {
    if(_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    fetchVecs()[vecId].setDomain(off, domain);
    return this;
  }

  public void setDomains(String[][] domains) {
    for(int i = 0; i < numCols(); ++i)
      setDomain(i,domains[i]);
  }

  @Override public String[][] domains(){
    String [][] res = new String[numCols()][];
    for(int i = 0; i < numCols(); ++i)
      res[i] = domain(_colFilter == null?i:_colFilter[i]);
    return res;
  }


  @Override // update changed blocks, need to reverse the mapping
  Futures closeChunk(ChunkAry c, Futures fs) {
    int cidx = c._cidx;
    Vec [] vecs = fetchVecs();
    int [] changedCols = c.changedCols();
    if(changedCols.length == 0) return fs;
    int [] dstCols = _colFilter == null?changedCols:ArrayUtils.select(ArrayUtils.invertedPermutation(_colFilter),changedCols);
    int lb, ub = _blockOffset[1];
    int blockId = -1;
    int i = 0;
    while(i < dstCols.length){
      int cid = dstCols[i];
      blockId = (ub == cid)?blockId+1:getBlockId(cid);
      lb = _blockOffset[blockId];
      ub = _blockOffset[blockId+1];
      int j = i;
      while(i < dstCols.length && lb <= dstCols[i] && dstCols[i] < ub) i++;
      Vec v = vecs[blockId];
      if(v.numCols() == 1) {
        assert j+1 == i;
        DKV.put(v.chunkKey(cidx),c.getChunk(changedCols[j]),fs);
      } else {
        Key key = vecs[blockId].chunkKey(cidx);
        DBlock.MultiChunkBlock block = DKV.getGet(key);
        for(int k = j; k < i; ++k)
          block.setChunk(dstCols[k] - lb,c.getChunk(changedCols[k]));
        DKV.put(key,block);
      }
    }
    return fs;
  }

  private int getBlockId(int cid) {
    int blockId;
    blockId = Arrays.binarySearch(_blockOffset, cid);
    if (blockId < 0) blockId = -blockId - 2;
    return blockId;
  }

  public void reloadVecs() {
    _vecs = null;
    fetchVecs();
  }


  @Override
  public VecAry selectRange(int from, int to){
    return select(ArrayUtils.seq(from,to));
  }
  /**
   * Get (possibly permuted) subset of the vecs.
   * @param idxs ids of the vecs to be selected
   * @return
   */
  @Override
  public VecAry select(int... idxs) {
    VecAry res = new VecAry();
    if(_colFilter != null) idxs = ArrayUtils.select(_colFilter,idxs);
    int vid = getBlockId(idxs[0]);
    int off = _blockOffset[vid];
    ArrayUtils.IntAry ids = new ArrayUtils.IntAry();
    for(int i = 0; i < idxs.length; ++i){
      int x = idxs[i];
      if(numCols() <= x || x < 0)
        throw new ArrayIndexOutOfBoundsException(x);
      if(_blockOffset[vid+1] <= x || x < off){
        if(ids.size() > 0) {
          res.append(new VecAry(fetchVec(vid), ids.toArray()));
          ids.clear();
        }
        vid = getBlockId(x);
        off = _blockOffset[vid];
      }
      ids.add(x-off);
    }
    if(ids.size() > 0)
      res.append(new VecAry(fetchVecs()[vid],ids.toArray()));
    return res;
  }

  @Override
  public VecAry remove(int... idxs){
    VecAry res = select(idxs);
    if(!ArrayUtils.isSorted(idxs)) {
      idxs = idxs.clone();
      Arrays.sort(idxs);
    }
    int [] rem = ArrayUtils.complement(idxs,numCols());
    replaceWith(select(rem));
    // now remove from this
    return res;
  }

  @Override
  public int nChunks(){return vecs()[0].nChunks();}

  /** Remove associated Keys when this guy removes.  For Vecs, remove all
   *  associated Chunks.
   *  @return Passed in Futures for flow-coding  */
  @Override public Futures remove_impl( Futures fs ) {
    if(_colFilter == null){
      for(Vec v:vecs())
        if(v != null)
          v.remove(fs);
      return fs;
    }
    Arrays.sort(_vecIds);
    int off = 0, j = 0;
    for(int i = 0; i < _vecs.length; ++i){
      final int ncols = _vecs[i].numCols();
      int jStart = j;
      while(j < _colFilter.length && _colFilter[j] < off+ncols)j++;
      if(j - jStart == ncols) _vecs[i].remove(fs);
      else _vecs[i].removeCols(Arrays.copyOfRange(_colFilter,jStart,j));
    }
    return fs;
  }

  @Override
  public void removeCols(final int... ids){
    remove(ids).remove();
  }



  private int [] vecOffsets(){
    int [] res = new int[_vecIds.length+1];
    Vec[] vecs = fetchVecs();
    for(int i = 1; i < res.length; ++i)
      res[i] =  vecs[i-1].numCols();
    return res;
  }

  private AVecAry appendAry(VecAry apndee){
    if(_vecIds.length == 0) return replaceWith(apndee);
    int lastVecId = _vecIds[_vecIds.length-1];
    // easy common cases first
    if(_colFilter == null && apndee._colFilter == null) { // no filters, just append the vecIds
      _vecIds = ArrayUtils.join(_vecIds, apndee._vecIds);
    } else {
      if(_colFilter == null) _colFilter = ArrayUtils.seq(0,numCols());
      int [] apndColFilter = apndee._colFilter == null?ArrayUtils.seq(0,apndee.numCols()):apndee._colFilter;
      _colFilter = ArrayUtils.append(_colFilter, apndColFilter);
      if(_vecIdMax < apndee._vecIdMin || _vecIdMin > apndee._vecIdMax) { // strictly non-overlapping
        for(int i = _numCols; i < _colFilter.length; ++i)
          _colFilter[i] += _numCols;
      } else {
        int [] offsetMap = apndee._blockOffset.clone();
        for(int i = 0; i < apndee._vecIds.length; ++i) {
          int vid = apndee._vecIds[i];
          int j = _colFilter.length-1;
          for(;j >= 0 && _vecIds[j] != vid; j--);
          if(j < 0) {
            offsetMap[i] -= apndee._blockOffset[i] + _numCols;
            _vecIds = ArrayUtils.append(_vecIds,vid);
          } else
            offsetMap[i] -= _blockOffset[j];
        }
        for(int i = _numCols; i < _colFilter.length; ++i)
          _colFilter[i] -= offsetMap[i];
      }
    }
    _numCols += apndee.numCols();
    return this;
  }
  private int _vecIdMax = Integer.MIN_VALUE;
  private int _vecIdMin = Integer.MAX_VALUE;

  @Override
  public VecAry append(Vec v){
    if(!checkCompatible(v)) throw new IllegalArgumentException("Can not append incompatible vecs");
    if(v instanceof AVecAry) {
      appendAry((VecAry)v);
    } else if(_colFilter != null){
      appendAry(new VecAry(v,ArrayUtils.seq(0,v.numCols())));
    } else {
      _vecIds = ArrayUtils.append(_vecIds, v.vecId());
//      _blockOffset = ArrayUtils.append(_blockOffset,_blockOffset[_blockOffset.length-1]+v.numCols());
      _numCols += v.numCols();
    }
    if(_colFilter != null && _colFilter.length == _blockOffset[_blockOffset.length-1] && ArrayUtils.isSorted(_colFilter))
      _colFilter = null;
    assert _colFilter == null || _colFilter.length == _numCols;
    _vecs = null;
    _blockOffset = ArrayUtils.append(_blockOffset,_blockOffset[_blockOffset.length-1]+v.numCols());
    return this;
  }

  @Override boolean checkCompatible( Vec v ) {
    return _rowLayout == -1 || v._rowLayout == -1 || super.checkCompatible(v);
  }

  @Override public Key groupKey(){
    return _rowLayout == -1?null: _key;
  }

  @Override
  public void swap(int lo, int hi) {
    if(_colFilter == null)
      _colFilter = ArrayUtils.seq(0, _numCols);
    int loVal = _colFilter[lo];
    int hiVal = _colFilter[hi];
    _colFilter[lo] = hiVal;
    _colFilter[hi] = loVal;
  }

  @Override
  public void moveFirst(int[] cols) {
    int [] colFilter = _colFilter == null?ArrayUtils.seq(0,numCols()):_colFilter;
    _colFilter = MemoryManager.malloc4(numCols());
    int j = 0, k = cols.length;
    for(int i = 0; i < colFilter.length; ++i)
      if(i == cols[j]){
        _colFilter[j++] = colFilter[i];
      } else
        _colFilter[k++] = colFilter[i];
  }

  @Override
  public VecAry replace(int col, Vec nv) {
    if(nv.numCols() != 1) throw new IllegalArgumentException("only 1d vec allowed");
    int newColId = numCols();
    append(nv);
    swap(col,newColId);
    return remove(newColId);
  }
  @Override
  public Vec[] vecs() {return fetchVecs();}

  // TODO: remove in the future
  // Not very efficient. I don't want to be fixing all for(Vec v:fr.vecs()) loops right now. Probably should be removed later.


  @Override
  public void insertVec(int i, VecAry x) {
    replaceWith(selectRange(0,i).append(x).append(selectRange(i,numCols())));
  }

  public double[] means() {
    RollupsAry rsa = rollupStats();
    double [] res = MemoryManager.malloc8d(rsa.numCols());
    for(int i = 0; i < rsa.numCols(); ++i)
      res[i] = rsa.getRollups(i)._mean;
    return res;
  }

  public double [] sds() {
    RollupsAry rsa = rollupStats();
    double [] res = MemoryManager.malloc8d(rsa.numCols());
    for(int i = 0; i < rsa.numCols(); ++i)
      res[i] = rsa.getRollups(i)._sigma;
    return res;
  }

}

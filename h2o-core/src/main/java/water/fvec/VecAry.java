package water.fvec;

import water.*;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * Created by tomas on 10/5/16.
 */
public class VecAry extends Vec implements Iterable<Vec> {
  int [] _vecIds;
  int [] _colFilter; // positive, negative or permuted positive column filter or null
  // permutation  ChunkAry id -> (flattened) Vec[] id.
  private transient Vec [] _vecs;
  private Key _groupKey;

  public int _numCols;
  public long _numRows;

  public int numCols(){return _numCols;}
  private VecAry replaceWith(VecAry newSelf){
    _vecIds = newSelf._vecIds;
    _colFilter = newSelf._colFilter;
    _vecs = newSelf._vecs;
    _groupKey = newSelf._groupKey;
    return this;
  }



  private Vec[] fetchVecs(){
    Vec [] vecs = _vecs;
    if(_vecs == null) {
      vecs = new Vec[_vecIds.length];
      for(int i = 0; i < vecs.length; ++i)
        vecs[i] = getVec(i);
      _vecs = vecs;
    }
    return vecs;
  }

  public VecAry(Vec... v){
    if(v.length == 0){
      _rowLayout = -1;
    } else {
      _rowLayout = v[0]._rowLayout;
      _groupKey =  v[0].groupKey();
      _numRows = v[0].length();
      if (v[0] instanceof VecAry) {
        replaceWith((VecAry) v[0]);
      } else _vecIds = new int[v[0].vecId()];
      _numCols = v[0].numCols();
      for (int i = 1; i < v.length; ++i)
        append(v[i]);
    }
  }

  private VecAry(Key groupKey, long numRows, int rowLayout,int numCols, int [] vecIds, int [] colFilter){
    _groupKey = groupKey;
    _numRows = numRows;
    _rowLayout = rowLayout;
    _numCols = numCols;
    _vecIds = vecIds;
    _colFilter = colFilter;
  }

  Vec getVec(int i){
    Key k = keyTemplate();
    k._kb[0] = Key.VEC;
    Vec.setVecId(k,_vecIds[i]);
    return DKV.getGet(k);
  }


  @Override public DBlock chunkIdx(int cidx){
    if(_numCols == 1){ // needed for wrapped vecs, otherwise should not be used
      ChunkAry cary = chunkForChunkIdx(cidx);
      return new DBlock(cary.getChunk(0));
    }
    throw new UnsupportedOperationException();
  }
  @Override
  public ChunkAry chunkForChunkIdx(int cidx) {
    fetchVecs();
    Key k = chunkKey(cidx);
    int s = 0;
    Chunk [] csall = new Chunk[numCols()];
    for(int i = 0; i < _vecIds.length;++i) {
      DBlock db = _vecs[i].chunkIdx(cidx);
      int n = _vecs[i].numCols();
      System.arraycopy(db._cs,0,csall,s,n);
      s += n;
    }
    return new ChunkAry(this,cidx,_colFilter == null?csall:ArrayUtils.select(csall,_colFilter),null);
  }

  @Override // update changed blocks, need to reverse the mapping
  Futures closeChunk(ChunkAry c, Futures fs) {
    int cidx = c._cidx;
    // columns changed
    Vec [] vecs = fetchVecs();
    int nextId = 0;
    int vecId = 0;
    int nsum = 0;
    DBlock db = null;
    int [] changedCols = c.changedCols();
    int [] dstCols = translateIds(changedCols);
    int [] perm = null;
    if(!ArrayUtils.isSorted(dstCols)) {
      Arrays.sort(dstCols);
      perm = new int[numCols()];
      Arrays.fill(perm,-1);
      for(int i = 0; i < _colFilter.length; ++i)
        perm[_colFilter[i]] = i;
    }
    for(int i = 0; i < changedCols.length; ++i) {
      if(db != null) {
        DKV.put(vecs[vecId].chunkKey(cidx),db,fs);
        db = null;
      }
      int idDst = dstCols[i];
      int idSrc = perm == null?changedCols[i]:perm[dstCols[i]];
      while(idDst >= nextId){
        nsum = nextId;
        db = DKV.getGet(vecs[vecId].chunkKey(cidx));
        nextId += vecs[vecId++].numCols();
      }
      db.setChunk(idDst - nsum, c.getChunk(idSrc));
    }
    if(db != null)
      DKV.put(vecs[vecId].chunkKey(cidx),db,fs);
    return fs;
  }

  public void reloadVecs() {
    _vecs = null;
    fetchVecs();
  }

  // map ids from ChunkAry ids to flattened Vec[] ids
  private int [] translateIds(int [] ids){
    if(_colFilter == null) return ids;
    if(ArrayUtils.isSorted(_colFilter))
      return ArrayUtils.select(ids,_colFilter);
    // cols are permuted
    ids = ids.clone();
    for(int i = 0; i < ids.length; ++i)
      ids[i] = _colFilter[ids[i]];
    return ids;
  }

  /**
   * Get (possibly permuted) subset of the vecs.
   * @param idxs ids of the vecs to be selected
   * @return
   */
  public VecAry select(int... idxs) {
    int [] ids = translateIds(idxs);
    int [] sortedIs = ids;
    if(!ArrayUtils.isSorted(ids)){
      sortedIs = ids.clone();
      Arrays.sort(sortedIs);
    }
    // clean up the blocks not used by the new vec ary
    Vec [] vecs = fetchVecs();
    int to = 0;
    int k = 0, l = 0;
    int rsum = 0;
    int [] vecIds = _vecIds.clone();
    for(int i = 0; i < vecs.length; ++i) {
      to += vecs[i].numCols();
      int kstart = k;
      while(ids[k] < to){
        sortedIs[k] -= rsum;
        k++;
      }
      if(k - kstart <= 1)  // block is not used => need to subtract vecs from this block
        rsum += vecs[i].numCols();
      else vecIds[l++] = _vecIds[i];
    }
    if(l < _vecIds.length)
      vecIds = Arrays.copyOf(vecIds,l);
    return new VecAry(_groupKey, _numRows, _rowLayout,ids.length,vecIds,ids);
  }

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

  /** Remove associated Keys when this guy removes.  For Vecs, remove all
   *  associated Chunks.
   *  @return Passed in Futures for flow-coding  */
  @Override public Futures remove_impl( Futures fs ) {
    if(_colFilter == null){
      for(Vec v:vecs()) v.remove(fs);
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

  private transient int _x;

  private int [] vecOffsets(){
    int [] res = new int[_vecIds.length+1];
    Vec[] vecs = fetchVecs();
    for(int i = 1; i < res.length; ++i)
      res[i] =  vecs[i-1].numCols();
    return res;
  }



  public VecAry append(Vec v){
    VecAry apndee = (v instanceof VecAry)?(VecAry)v:new VecAry(v);
    if(_rowLayout == -1) return replaceWith(new VecAry(apndee));
    if(!checkCompatible(apndee)) throw new IllegalArgumentException("Can not append incompatible vecs");

    if(apndee.numCols() == 1 && apndee._vecIds[0] == _vecIds[_vecIds.length-1])
      _x = _vecIds[_vecIds.length-1];
    // easy common cases first
    if(apndee.numCols() == 1 && apndee._vecIds[0] == _vecIds[_x]){
      if(_colFilter == null) _colFilter = ArrayUtils.seq(0,_numCols);
      int off = 0;
      if(_vecIds.length > 1){
        Vec [] vecs = fetchVecs();
        for(int i = 0; i < vecs.length-1; ++i)
          off += vecs[i].numCols();
      }
      _colFilter = ArrayUtils.append(_colFilter,(apndee._colFilter == null?0:apndee._colFilter[0])+off);
      _numCols++;
      return this;
    }
    if(ArrayUtils.maxValue(_vecIds) < ArrayUtils.minValue(apndee._vecIds) || ArrayUtils.minValue(_vecIds) > ArrayUtils.maxValue(apndee._vecIds)){
      _vecIds = ArrayUtils.join(_vecIds,apndee._vecIds);
      if(_colFilter != null && apndee._colFilter == null)
        _colFilter = ArrayUtils.join(_colFilter,ArrayUtils.seq(_numCols,_numCols+ apndee.numCols()));
      else if(apndee._colFilter != null) {
        _colFilter = ArrayUtils.join(_colFilter == null?ArrayUtils.seq(0, _numCols):_colFilter, apndee._colFilter);
        for(int i = _numCols; i < _colFilter.length; ++i)
          _colFilter[i] += _numCols;
      }
      reloadVecs();
      _numCols += apndee.numCols();
      return this;
    }
    // general append, we have overlapping vecs, colfilter, need to map apndee to this, then join the arrays
    int vid;
    int [] thisVecOffsets = vecOffsets();
    int [] apndeeVecOffsets = apndee.vecOffsets();
    int k = thisVecOffsets[thisVecOffsets.length-1]; // extra vecs
    ArrayUtils.IntAry newVecIds = new ArrayUtils.IntAry();
    outer:
    for(int i = 0; i < apndeeVecOffsets.length-1; ++i) {
      vid = apndee._vecIds[i];
      if (_vecIds[_x] == vid || _vecIds[++_x] == vid){ // cover common case
        apndeeVecOffsets[i] -= thisVecOffsets[_x];
        continue;
      } else for (int j = 0; j < _vecIds.length; ++i) {
        if (_vecIds[j] == vid) {
          apndeeVecOffsets[i] -= thisVecOffsets[_x];
          continue outer;
        }
      }
      apndeeVecOffsets[i] -= k;
      k += apndee.vecs()[i].numCols();
      newVecIds.add(vid);
    }
    assert _colFilter != null || apndee._colFilter != null:"duplicated vecs?";
    if(_colFilter == null)
      _colFilter = ArrayUtils.seq(0,_numCols);
    if(apndee._colFilter == null)
      apndee._colFilter = ArrayUtils.seq(0,apndee._numCols);
    _colFilter = ArrayUtils.join(_colFilter,apndee._colFilter);
    for(int i = _numCols; i < _colFilter.length; ++i)
      _colFilter[i] -= apndeeVecOffsets[i-_numCols];
    if(newVecIds.size() > 0){
      _vecIds = ArrayUtils.join(_vecIds,newVecIds.toArray());
      reloadVecs();
    }
    _numCols += apndee._numCols;
    return this;
  }

  @Override boolean checkCompatible( Vec v ) {
    return _rowLayout == -1 || v._rowLayout == -1 || super.checkCompatible(v);
  }

  @Override public Key groupKey(){
    return _rowLayout == -1?null:_groupKey;
  }

  private int updateNumCols(){
    if(_colFilter != null) return _numCols = _colFilter.length;
    fetchVecs();
    int res = 0;
    for(Vec v:_vecs) res += v.numCols();
    return _numCols = res;
  }

  public void swap(int lo, int hi) {
    if(_colFilter == null)
      _colFilter = ArrayUtils.seq(0,_numCols);
    int loVal = _colFilter[lo];
    int hiVal = _colFilter[hi];
    _colFilter[lo] = hiVal;
    _colFilter[hi] = loVal;
  }

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
  public Iterator<Vec> iterator() {
    return new Iterator<Vec>() {
      int _id;
      @Override
      public boolean hasNext() {
        return _id < _numCols;
      }

      @Override
      public Vec next() {
        return select(_id++);
      }
      @Override public void remove(){throw new UnsupportedOperationException();}
    };
  }

  public void insertVec(int i, VecAry x) {
    throw H2O.unimpl();
  }
}

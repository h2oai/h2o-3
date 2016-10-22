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

  @Override
  public void removeCols(final int... ids){
    remove(ids).remove();
  }

  public VecAry append(Vec v){
    if(_rowLayout == -1) return replaceWith(new VecAry(v));
    if(v instanceof VecAry) return append((VecAry)v);
    if(!checkCompatible(v)) throw new IllegalArgumentException("Can not append incompatible vecs");
    if(_colFilter != null)
      _colFilter = ArrayUtils.join(_colFilter,ArrayUtils.seq(_numCols,_numCols+v.numCols()));
    _vecIds = ArrayUtils.append(_vecIds,v.vecId());
    _numCols += v.numCols();
    return this;
  }


  public VecAry append(VecAry apndee){
    if(_rowLayout == -1) return replaceWith(new VecAry(apndee));
    if(!checkCompatible(apndee)) throw new IllegalArgumentException("Can not append incompatible vecs");
    // need to unify the column numbering
    int [] vecIds = ArrayUtils.join(_vecIds,apndee._vecIds);
    if(_colFilter == null && apndee._colFilter != null)
      _colFilter = ArrayUtils.seq(0,numCols());
    int [] colFilter = apndee._colFilter;
    if(_colFilter != null && colFilter == null)
      colFilter = ArrayUtils.seq(0,apndee.numCols());
    if(colFilter != null) {
      for(int i = 0; i < colFilter.length; ++i)
        colFilter[i] += numCols();
      _colFilter = ArrayUtils.join(_colFilter,colFilter);
    }
    _vecIds = vecIds;
    reloadVecs();
    updateNumCols();
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
}

package water.fvec;

import water.*;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;


/**
 * Created by tomas on 10/5/16.
 */
public final class VecAry extends Iced<VecAry> {
  Key<Vec.VectorGroup> _groupKey;
  int[] _colFilter; // positive, negative or permuted positive column filter or null
  int[] _blockOffset;
  // permutation  ChunkAry id -> (flattened) Vec[] id.

  public int _numCols;
  public long _numRows;

  public int numCols() {
    return _numCols;
  }

  private VecAry replaceWith(VecAry newSelf) {
    _groupKey = newSelf._groupKey;
    _vecIds = newSelf._vecIds;
    _colFilter = newSelf._colFilter;
    _vecs = newSelf._vecs;
    _blockOffset = newSelf._blockOffset;
    _numCols = newSelf._numCols;
    _numRows = newSelf._numRows;
    if(_colFilter != null && _colFilter.length == _blockOffset[_blockOffset.length-1] && ArrayUtils.isSorted(_colFilter))
      _colFilter = null;
    return this;
  }

  public VecAry() {
    _blockOffset = new int[]{0};
    _vecIds = new int[0];
  }

  public VecAry(VecAry vecs) {
    replaceWith(vecs);
    if (_colFilter != null)
      _colFilter = _colFilter.clone();
    if (_vecIds != null)
      _vecIds = _vecIds.clone();
    if (_vecs != null)
      _vecs = _vecs.clone();
    if (_blockOffset != null)
      _blockOffset = _blockOffset.clone();
  }

  public VecAry(Vec v) {
    _groupKey = v.groupKey();
    _vecIds = new int[]{v.vecId()};
    _blockOffset = new int[]{0, v.numCols()};
    _numRows = v.length();
    _vecs = new Vec[]{v};
    _numCols = v.numCols();
    _groupKey = v.groupKey();
  }

  public VecAry(Vec[] v) {
    _vecIds = new int[0];
    _blockOffset = new int[]{0};
    if (v.length != 0) {
      _numRows = v[0].length();
      for (int i = 0; i < v.length; ++i)
        append(v[i]);
      assert _vecIds.length > 0;
    }
  }

  private VecAry(Vec v, int... ids) {
    _groupKey = v.groupKey();
    _vecIds = new int[]{v.vecId()};
    _colFilter = ids;
    _numCols = _colFilter.length;
    _numRows = v.numRows();
    _blockOffset = new int[]{0, v.numCols()};
  }

  public RollupsAry rollupStats() {
    return rollupStats(false);
  }

  public RollupsAry rollupStats(boolean computeHisto) {
    Futures fs = new Futures();
    RollupStats[] rs;
    if (_vecIds.length == 1)
      rs = fetchVec(0).rollupStats(computeHisto)._rs;
    else {
      for (Vec v : fetchVecs()) v.startRollupStats(fs, computeHisto);
      rs = new RollupStats[_blockOffset[_blockOffset.length - 1]];
      int i = 0;
      for (Vec v : vecs()) {
        RollupsAry rsa = v.rollupStats(computeHisto);
        for (int j = 0; j < v.numCols(); ++j)
          rs[i++] = rsa.isRemoved(j) ? null : rsa.getRollups(j);
      }
    }
    if (_colFilter != null) rs = ArrayUtils.select(rs, _colFilter);
    return new RollupsAry(rs);
  }


  public boolean checkCompatible(Vec v) {
    return isEmpty() || fetchVec(0).checkCompatible(v);
  }

  public boolean checkCompatible(VecAry v) {
    return isEmpty() || v.isEmpty() || fetchVec(0).checkCompatible(v.fetchVec(0));
  }

  public final long chunk2StartElem(int i) {
    return fetchVec(0).chunk2StartElem(i);
  }

  public final int elem2ChunkIdx(long l) {
    if (isEmpty()) throw new IllegalArgumentException("accessing empty vec array");
    return fetchVec(0).elem2ChunkIdx(l);
  }


  public ChunkAry chunkForChunkIdx(int cidx) {
    Vec[] vecs = fetchVecs();
    if (_vecIds.length == 1) { // single vec
      DBlock db = vecs[0].chunkIdx(cidx);
      return new ChunkAry(this, cidx, _colFilter == null ? db.chunks() : ArrayUtils.select(db.chunks(), _colFilter));
    }
    if (_numCols == _vecIds.length && _colFilter == null) {  // all simple vecs
      Chunk[] cs = new Chunk[_numCols];
      for (int i = 0; i < vecs.length; ++i)
        cs[i] = (Chunk) vecs[i].chunkIdx(cidx);
      return new ChunkAry(this, cidx, cs);
    }
    DBlock dbs[] = new DBlock[_vecIds.length];
    for (int i = 0; i < _vecIds.length; ++i)
      dbs[i] = fetchVec(i).chunkIdx(cidx);
    Chunk[] csall = new Chunk[_numCols];
    if (_colFilter == null) {
      int j = 0;
      for (int i = 0; i < _vecIds.length; ++i) {
        if (dbs[i] instanceof Chunk) {
          csall[j++] = (Chunk) dbs[i];
        } else {
          Chunk[] chks = dbs[i].chunks();
          System.arraycopy(chks, 0, csall, j, chks.length);
          j += chks.length;
        }
      }
      return new ChunkAry(this, cidx, csall);
    }
    int lb = _numCols;
    int ub = -1;
    DBlock db = null;
    for (int j = 0; j < _colFilter.length; ++j) {
      int c = _colFilter[j];
      if (c < lb || c >= ub) {
        int blockId = getBlockId(c);
        if (dbs[blockId] instanceof Chunk) {
          csall[j] = (Chunk) dbs[blockId];
          continue;
        }
        lb = _blockOffset[blockId];
        ub = _blockOffset[blockId + 1];
        db = dbs[blockId];
      }
      csall[j] = db.getColChunk(c - lb);
    }
    return new ChunkAry(this, cidx, csall);
  }




  public byte getType(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length == 1) return fetchVec(0).getType(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVecs()[vecId].getType(off);
  }


  protected byte setType(int c, byte t) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVecs()[vecId].setType(off, t);
  }

  public String[] domain(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVecs()[vecId].domain(off);
  }


  public RollupsAry tryFetchRollups() {
    if (_vecIds == null || _vecIds.length == 0)
      return null;
    RollupStats[] rs = new RollupStats[numCols()];
    int i = 0;
    for (Vec v : vecs()) {
      RollupsAry rsa = v.tryFetchRollups();
      if (rsa == null) return null;
      for (int j = 0; j < rsa.numCols(); ++j)
        rs[i++] = rsa.getRollups(j);
    }
    if (_colFilter != null) rs = ArrayUtils.select(rs, _colFilter);
    return new RollupsAry(rs);
  }

  @Override
  public String toString() {
    if (_vecIds == null || _vecIds.length == 0)
      return "[empty]";
    return super.toString();
  }

  public VecAry setDomain(int c, String[] domain) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    fetchVecs()[vecId].setDomain(off, domain);
    return this;
  }

  public void setDomains(String[][] domains) {
    for (int i = 0; i < numCols(); ++i)
      setDomain(i, domains[i]);
  }

  public String[][] domains() {
    String[][] res = new String[numCols()][];
    for (int i = 0; i < numCols(); ++i)
      res[i] = domain(i);
    return res;
  }

  int getBlockId(int cid) {
    if(_vecIds.length == 1) return 0;
    int blockId;
    blockId = Arrays.binarySearch(_blockOffset, cid);
    if (blockId < 0) blockId = -blockId - 2;
    return blockId;
  }

  public void reloadVecs() {
    _vecs = null;
    fetchVecs();
  }

  public VecAry selectRange(int from, int to) {
    if(from == to) return new VecAry();
    return select(ArrayUtils.seq(from, to));
  }

  /**
   * Get (possibly permuted) subset of the vecs.
   *
   * @param idxs ids of the vecs to be selected
   * @return
   */
  public VecAry select(int... idxs) {
    VecAry res = new VecAry();
    if(idxs.length == 0) return res;
    if (_colFilter != null) idxs = ArrayUtils.select(_colFilter, idxs);
    int vid = getBlockId(idxs[0]);
    int off = _blockOffset[vid];
    ArrayUtils.IntAry ids = new ArrayUtils.IntAry();
    int maxId = _blockOffset[_blockOffset.length-1];
    for (int i = 0; i < idxs.length; ++i) {
      int x = idxs[i];
      if (maxId <= x || x < 0)
        throw new ArrayIndexOutOfBoundsException(x);
      if (_blockOffset[vid + 1] <= x || x < off) {
        if (ids.size() > 0) {
          res.append(new VecAry(fetchVec(vid), ids.toArray()));
          ids.clear();
        }
        vid = getBlockId(x);
        off = _blockOffset[vid];
      }
      ids.add(x - off);
    }
    if (ids.size() > 0)
      res.append(new VecAry(fetchVecs()[vid], ids.toArray()));
    return res;
  }


  public VecAry removeVecs(int... idxs) {
    VecAry res = select(idxs);
    if (!ArrayUtils.isSorted(idxs)) {
      idxs = idxs.clone();
      Arrays.sort(idxs);
    }
    int[] rem = ArrayUtils.complement(idxs, numCols());
    replaceWith(select(rem));
    return res;
  }

  public int nChunks() {
    return isEmpty() ? 0 : fetchVec(0).nChunks();
  }

  /**
   * Remove associated Keys when this guy removes.  For Vecs, removeVecs all
   * associated Chunks.
   *
   * @return Passed in Futures for flow-coding
   */
  public void remove() {
    remove(new Futures()).blockForPending();
  }

  public Futures remove(Futures fs) {
    Vec [] vecs = fetchVecs();
    if (_colFilter == null) {
      for (Vec v : vecs)
        if (v != null)
          v.remove(fs);
      return fs;
    }
    ArrayUtils.IntAry vids = new ArrayUtils.IntAry();
    ArrayUtils.IntAry colFilter = new ArrayUtils.IntAry();
    for(int i = 0; i < vecs.length; ++i){
      int from = _blockOffset[i];
      int to = _blockOffset[i+1];
      for(int x:_colFilter){
        if(from <= x && x < to)
          vids.add(x-from);
        else
          colFilter.add(x);
      }
      vecs[i].removeCols(fs, vids.toArray());
      _colFilter = colFilter.toArray();
      colFilter.clear();
      vids.clear();
    }
    return fs;
  }

  public VecAry append(VecAry apndee) {
    if(apndee.isEmpty()) return this;
    if (!checkCompatible(apndee)) throw new IllegalArgumentException("Can not append incompatible vecs");
    if (_vecIds.length == 0) return replaceWith(apndee);
    if(_groupKey == null)_groupKey = apndee._groupKey;
    // easy case first
    if (_colFilter == null && apndee._colFilter == null) { // no filters, just append the vecIds
      for(Vec v:apndee.fetchVecs())
        append(v);
      return this;
    } else {
      if (_colFilter == null) _colFilter = ArrayUtils.seq(0, numCols());
      _colFilter = Arrays.copyOf(_colFilter,numCols() + apndee.numCols());
      int [] newVecs = new int[apndee._vecIds.length];
      int newvecCnt = 0;
      int [] offsetMap = apndee._blockOffset.clone();
      int [] blockOffset = Arrays.copyOf(_blockOffset,_blockOffset.length + apndee._vecIds.length);
      int maxId = _blockOffset.length-1;
      for(int i = 0; i < apndee._vecIds.length; ++i){
        int vid = apndee._vecIds[i];
        int j = _vecIds.length-1;
        for (; j >= 0 && _vecIds[j] != vid; j--);
        if (j < 0) { // new vec
          newVecs[newvecCnt++] = i;
          offsetMap[i] = -offsetMap[i] + _blockOffset[maxId];
          blockOffset[maxId+1] = _blockOffset[maxId] + apndee.fetchVec(i).numCols();
          maxId++;
        } else {
          offsetMap[i] = _blockOffset[j] - apndee._blockOffset[i];
        }
      }
      for(int i = 0; i < apndee._numCols; ++i){
        int c = apndee.colFilter(i);
        int vid = apndee.getBlockId(c);
        int max = apndee._blockOffset[vid+1];
        int min = apndee._blockOffset[vid];
        int offAdjst = offsetMap[vid];
        _colFilter[_numCols+i] = c + offAdjst;
        while((i+1) < apndee._numCols && (c = apndee.colFilter(i+1)) >= min && c < max)
          _colFilter[_numCols+ ++i] = c + offAdjst;
      }
      if(newvecCnt > 0) {
        _blockOffset = Arrays.copyOf(blockOffset,maxId+1);
        _vecIds = ArrayUtils.append(_vecIds,ArrayUtils.select(apndee._vecIds,newVecs));
        Vec [] avecs = apndee._vecs;
        if(_vecs != null && avecs != null){
          _vecs = ArrayUtils.append(_vecs,ArrayUtils.select(avecs,newVecs));
        } else _vecs = null;
      }
      _numCols += apndee.numCols();
      if(_colFilter.length == _blockOffset[_blockOffset.length-1] && ArrayUtils.isSorted(_colFilter))
        _colFilter = null;
      return this;
    }

  }

  int colFilter(int i) {
    return _colFilter == null?i:_colFilter[i];
  }

  public VecAry append(Vec v) {
    if (!checkCompatible(v)) throw new IllegalArgumentException("Can not append incompatible vecs");
    if(_groupKey == null) _groupKey = v.groupKey();
    if (_colFilter != null) {
      return append(new VecAry(v, ArrayUtils.seq(0, v.numCols())));
    } else {
      _vecIds = ArrayUtils.append(_vecIds, v.vecId());
      _numCols += v.numCols();
    }
    if (_colFilter != null && _colFilter.length == _blockOffset[_blockOffset.length - 1] && ArrayUtils.isSorted(_colFilter))
      _colFilter = null;
    assert _colFilter == null || _colFilter.length == _numCols;
    _vecs = null;
    _blockOffset = ArrayUtils.append(_blockOffset, _blockOffset[_blockOffset.length - 1] + v.numCols());
    return this;
  }

  public void swap(int lo, int hi) {
    if (_colFilter == null)
      _colFilter = ArrayUtils.seq(0, _numCols);
    int loVal = _colFilter[lo];
    int hiVal = _colFilter[hi];
    _colFilter[lo] = hiVal;
    _colFilter[hi] = loVal;
  }

  public void moveFirst(int[] cols) {
    int[] colFilter = _colFilter == null ? ArrayUtils.seq(0, numCols()) : _colFilter;
    _colFilter = MemoryManager.malloc4(numCols());
    int j = 0, k = cols.length;
    for (int i = 0; i < colFilter.length; ++i)
      if (i == cols[j]) {
        _colFilter[j++] = colFilter[i];
      } else
        _colFilter[k++] = colFilter[i];
  }

  public VecAry replace(int col, VecAry nv) {
    if (nv.numCols() != 1) throw new IllegalArgumentException("only 1d vec allowed");
    int newColId = numCols();
    append(nv);
    swap(col, newColId);
    return removeVecs(newColId);
  }

  public Vec[] vecs() {
    return fetchVecs();
  }


  public void insertVec(int i, VecAry x) {
    replaceWith(selectRange(0, i).append(x).append(selectRange(i, numCols())));
  }

  public double[] means() {
    RollupsAry rsa = rollupStats();
    double[] res = MemoryManager.malloc8d(rsa.numCols());
    for (int i = 0; i < rsa.numCols(); ++i)
      res[i] = rsa.getRollups(i)._mean;
    return res;
  }

  public double[] sds() {
    RollupsAry rsa = rollupStats();
    double[] res = MemoryManager.malloc8d(rsa.numCols());
    for (int i = 0; i < rsa.numCols(); ++i)
      res[i] = rsa.getRollups(i)._sigma;
    return res;
  }


  protected transient Vec[] _vecs;
  protected int[] _vecIds;


  public long[] espc() {
    if (isEmpty()) throw new IllegalArgumentException("Accessing espc of an empty vec array");
    return fetchVec(0).espc();
  }

  public final long length() {
    return fetchVec(0).length();
  }


  public final boolean isEmpty() {
    return _vecIds == null || _vecIds.length == 0;
  }

  protected void prefetchVec(int i) {
    if (isEmpty()) throw new IllegalArgumentException("accessing vec from and empty vec array");
    DKV.prefetch(Vec.VectorGroup.getVecKeyById(_groupKey, _vecIds[i]));
  }

  private Key<Vec> vecKey(int i){
    return Vec.VectorGroup.getVecKeyById(_groupKey, _vecIds[i]);
  }
  private Vec getVec(int i) {
    if (isEmpty()) throw new IllegalArgumentException("accessing vec from and empty vec array");
    Key<Vec> k = vecKey(i);
    Vec v = k.get();
    assert v != null : "missing vec " + k;
    return v;
  }

  public final Futures startRollupStats(Futures fs) {
    return startRollupStats(fs, false);
  }

  public final Futures startRollupStats(Futures fs, boolean computeHisto) {
    for (Vec v : fetchVecs()) v.startRollupStats(fs, computeHisto);
    return fs;
  }

  protected final Vec fetchVec(int i) {
    return (_vecs == null)?fetchVecs()[i]:_vecs[i];
  }

  protected final Vec[] fetchVecs() {
    if (_vecIds == null) return null;
    Vec[] vecs = _vecs;
    if (_vecs == null) {
      for (int i = 0; i < _vecIds.length; ++i)
        prefetchVec(i);
      vecs = new Vec[_vecIds.length];
      for (int i = 0; i < vecs.length; ++i)
        vecs[i] = getVec(i);
      _vecs = vecs;
    }
    return vecs;
  }

  public Iterable<VecAry> singleVecs() {
    return new Iterable<VecAry>() {
      @Override
      public Iterator<VecAry> iterator() {
        return new Iterator<VecAry>() {
          int _id;
          final int _numCols = numCols();

          @Override
          public boolean hasNext() {
            return _id < _numCols;
          }

          @Override
          public VecAry next() {
            return select(_id++);
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public boolean isCategorical() {
    return isCategorical(0);
  }

  public boolean isCategorical(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length == 1) return fetchVec(0).isCategorical(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isCategorical(off);
  }

  public boolean isNumeric() {return isNumeric(0);}
  public boolean isNumeric(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length == 0 || c < _blockOffset[1])
      return fetchVec(0).isNumeric(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isNumeric(off);
  }

  public double min() {return min(0);}

  public final double min(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length == 1 || c < _blockOffset[1]) return fetchVec(0).min(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).min(off);
  }

  public long [] bins(){return bins(0);}
  public long [] bins(int c){
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).bins(off);
  }
  public double mean() {return mean(0);}

  public final double mean(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length == 1) return fetchVec(0).mean(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).mean(off);
  }
  public double sparseRatio(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).sparseRatio(off);
  }
  public double base(int c){
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).base(off);
  }
  public double stride(int c){
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).stride(off);
  }
  public double [] pctiles(int c){
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).pctiles(off);
  }
  public double[] mins() {return mins(0);}
  public double[] mins(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).mins(off);
  }

  public double[] maxs() {return maxs(0);}
  public double[] maxs(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).maxs(off);
  }

  public long[] lazy_bins(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).lazy_bins(off);
  }

  public long nzCnt(){return nzCnt(0);}
  public long nzCnt(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).nzCnt(off);
  }

  public long pinfs(){return pinfs(0);}
  public long pinfs(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).pinfs(off);
  }

  public long ninfs(){return ninfs(0);}
  public long ninfs(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).ninfs(off);
  }
  public double sigma() {return sigma(0);}
  public double sigma(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length == 1) return fetchVec(0).sigma(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).sigma(off);
  }

  public long naCnt() {return naCnt(0);}

  public long naCnt(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length == 1 || c < _blockOffset[1])
      return fetchVec(0).naCnt(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).naCnt(off);
  }

  public double max() {return max(0);}

  public double max(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length==1 || c < _blockOffset[1]) return fetchVec(0).max(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).max(off);
  }

  public boolean isConst() { return isConst(0);}
  public boolean isConst(int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(_vecIds.length == 1 || c < _blockOffset[1]) return vecs()[0].isConst(c);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isConst(off);
  }
  public boolean isBad() { return isBad(0);}
  public boolean isBad(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isBad(off);
  }
  public boolean isBinary() { return isBinary(0);}
  public boolean isBinary(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isBinary(off);
  }

  public boolean isTime(){return isTime(0);}
  public boolean isTime(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isTime(off);
  }
  public boolean isInt() {
    return isInt(0);
  }

  public boolean isInt(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isInt(off);
  }

  public boolean isString() {
    return isString(0);
  }

  public boolean isString(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isString(off);
  }

  public boolean isUUID() {
    return isUUID(0);
  }

  public boolean isUUID(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isUUID(off);
  }

  public String[] domain() {
    return domain(0);
  }

  public int cardinality() { return cardinality(0);}
  public int cardinality(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).cardinality(off);
  }


  public int mode(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).mode(off);
  }

  public String get_type_str(int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).get_type_str(off);
  }

  public String get_type_str() {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < numCols(); ++i) {
      if (i > 0) sb.append(",");
      sb.append(get_type_str(i));
    }
    sb.append("]");
    return sb.toString();
  }

  public byte[] types() {
    byte[] res = MemoryManager.malloc1(_blockOffset[_blockOffset.length - 1]);
    int off = 0;
    for (Vec v : fetchVecs()) {
      byte[] ts = v.get_types();
      System.arraycopy(ts, 0, res, off, ts.length);
      off += ts.length;
    }
    return _colFilter == null ? res : ArrayUtils.select(res, _colFilter);
  }

  public Futures postWrite(Futures fs) {
    Vec[] vecs = fetchVecs();
    for (Vec v : vecs)
      DKV.prefetch(v.rollupStatsKey());
    for (Vec v : vecs)
      v.postWrite(fs);
    return fs;
  }

  public double at(long l) {return at(l,0);}

  public double at(long l, int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(c < _blockOffset[1]) return fetchVec(0).at(l,c);
    if(c < _blockOffset[2]) return fetchVec(1).at(l,c-_blockOffset[1]);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).at(l, off);
  }

  public long at8(long l) {
    return at8(0);
  }

  public long at8(long l, int c) {
    if (_colFilter != null) c = _colFilter[c];
    if(c < _blockOffset[1]) return fetchVec(0).at8(l,c);
    if(c < _blockOffset[2]) return fetchVec(1).at8(l,c-_blockOffset[1]);
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).at8(l, off);
  }

  public long at16l(long l) {
    return at16l(0);
  }

  public long at16l(long l, int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).at16l(l, off);
  }
  public long at16h(long l) {
    return at16l(0);
  }
  public long at16h(long l, int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).at16l(l, off);
  }

  public int at4(long l) {
    return at4(0);
  }

  public int at4(long l, int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).at4(l, off);
  }


  public boolean isNA(long l) {
    return isNA(l, 0);
  }

  public boolean isNA(long l, int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).isNA(l, off);
  }

  public BufferedString atStr(BufferedString bs, long l) {
    return atStr(bs, l, 0);
  }

  public BufferedString atStr(BufferedString bs, long l, int c) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).atStr(bs, l, off);
  }

  public String factor(int c, int val) {
    if (_colFilter != null) c = _colFilter[c];
    int vecId = getBlockId(c);
    int off = c - _blockOffset[vecId];
    return fetchVec(vecId).factor(off, val);
  }

  public VecAry makeCopy() {return makeCopy(domains());}
  public VecAry makeCopy(String [][] domains) {
    if (isEmpty()) return new VecAry();
    Vec v0 = fetchVec(0);
    byte [] types = types();
    for(int i = 0; i < types.length; ++i)
      if((domains == null || domains[i] == null) && types[i] == Vec.T_CAT)
        types[i] = Vec.T_NUM;
    final Vec v1 = new Vec(v0.group().addVec(), v0._rowLayout, domains, types);
    new MRTask() {
      @Override
      public void map(ChunkAry c) {
        DKV.put(v1.chunkKey(c._cidx), c.copyIntoNewBlock(), _fs);
      }
    }.doAll(this);
    DKV.put(v1);
    return new VecAry(v1);
  }


  public VecAry makeZero(){return makeZero(1);}
  public VecAry makeZero(int numcols) {
    return new VecAry(fetchVec(0).makeZeros(numcols));
  }
  public VecAry makeCons(double ... vals) {
    return new VecAry(fetchVec(0).makeCons(vals));
  }

  public VecAry adaptTo( String[] domain ) {
    Vec v = fetchVec(0);
    return new VecAry(new CategoricalWrappedVec(v.group().addVec(),v._rowLayout,domain,this));
  }

  /**
   * Convenience method for converting to a categorical vector.
   * @return A categorical vector based on the contents of the original vector.
   */
  public VecAry toCategoricalVec() {return VecUtils.toCategoricalVec(this);}
  /**
   * Convenience method for converting to a string vector.
   * @return A string vector based on the contents of the original vector.
   */
  public VecAry toStringVec() {return VecUtils.toStringVec(this);}
  /**
   * Convenience method for converting to a numeric vector.
   * @return A numeric vector based on the contents of the original vector.
   */
  public VecAry toNumericVec() {return VecUtils.toNumericVec(this);}


  public ChunkAry<Chunk> chunkForRow(long row_offset) {
    return chunkForChunkIdx(elem2ChunkIdx(row_offset));
  }


  public Vec.VectorGroup group() {
    return isEmpty()?null:fetchVec(0).group();
  }


  public void preWriting() {
    for(Vec v:fetchVecs()) v.preWriting();
  }

  public boolean isHomedLocally(int i) {
    return fetchVec(0).isHomedLocally(i);
  }

  public int rowLayout() {return fetchVec(0)._rowLayout;}

  public void set(long rownum, int col, double at) {
    if (_colFilter != null) col = _colFilter[col];
    int vecId = getBlockId(col);
    int off = col - _blockOffset[vecId];
    fetchVec(vecId).set(rownum, off,at);
  }

  public void set(long rownum, String str) {
    set(rownum,0,str);
  }
  public void set(long rownum, int col, String str) {
    if (_colFilter != null) col = _colFilter[col];
    int vecId = getBlockId(col);
    int off = col - _blockOffset[vecId];
    fetchVec(vecId).set(rownum, off,str);
  }

  public void checkAllVecsExist() {
    if(!isEmpty()) {
      RollupsAry rsa = rollupStats();
      for (int i = 0; i < _vecIds.length; ++i)
        if (rsa.isRemoved(i))
          throw new IllegalArgumentException("Missing vec " + vecKey(i));
    }
  }

  /** A more efficient way to read randomly to a Vec - still single-threaded,
   *  but much faster than Vec.at(i).  Limited to single-threaded
   *  single-machine reads.
   *
   * Usage:
   * Vec.Reader vr = vec.new Reader();
   * x = vr.at(0);
   * y = vr.at(1);
   * z = vr.at(2);
   */
  public final class Reader {
    private ChunkAry _cache;
    private ChunkAry chk(long i) {
      ChunkAry c = _cache;
      return (c != null && c.start() <= i && i < c.start()+ c._len) ? c : (_cache = chunkForRow(i));
    }
    public final long    at8( long i) { return at8(i,0); }
    public final long    at8( long i, int j ) {
      ChunkAry ck = chk(i);
      return ck.at8(ck.chunkRelativeOffset(i),j);
    }
    public final double   at( long i) { return at(i,0);}
    public final double   at( long i, int j ) {
      ChunkAry ck = chk(i);
      return ck.atd(ck.chunkRelativeOffset(i),j);
    }
    public final boolean isNA(long i) {return isNA(i,0);}
    public final boolean isNA(long i, int j) {
      ChunkAry ck = chk(i);
      return ck.isNA(ck.chunkRelativeOffset(i),j);
    }
    public final long length() { return VecAry.this.length(); }
  }

  /** A more efficient way to write randomly to a Vec - still single-threaded,
   *  still slow, but much faster than Vec.set().  Limited to single-threaded
   *  single-machine writes.
   *
   * Usage:
   * try( Vec.Writer vw = vec.open() ) {
   *   vw.set(0, 3.32);
   *   vw.set(1, 4.32);
   *   vw.set(2, 5.32);
   * }
   */
  public final class Writer implements java.io.Closeable {
    private ChunkAry _cache;
    Futures _fs = new Futures();
    int _j;
    private ChunkAry chk(long i) {
      ChunkAry c = _cache;
      if(c == null || c.start() > i || c.start() + c._len <= i) {
        if(_cache != null) _cache.close(_fs);
        _cache = chunkForRow(i);

      }
      return _cache;
    }
    private Writer() { this(0);}
    private Writer(int j) { preWriting(); }

    public final void set( long i, long   l) { set(i,_j,l);}
    public final void set( long i, int j, long   l) {
      ChunkAry ck = chk(i);
      ck.set(ck.chunkRelativeOffset(i),j, l);
    }
    public final void set( long i, double   d) { set(i,_j,d);}
    public final void set( long i, int j, double d) {
      ChunkAry ck = chk(i);
      ck.set(ck.chunkRelativeOffset(i),j, d);
    }
    public final void set( long i, float  f) { set(i,f);}
    public final void set( long i, int j, float  f) {
      ChunkAry ck = chk(i);
      ck.set(ck.chunkRelativeOffset(i),j, f);
    }
    public final void setNA( long i        ) { setNA(i,_j); }
    public final void setNA( long i, int j) {
      ChunkAry ck = chk(i);
      ck.setNA(ck.chunkRelativeOffset(i),j);
    }
    public final void set( long i,String str){ set(i,_j,str); }
    public final void set( long i,int j, String str){
      ChunkAry ck = chk(i);
      ck.set(ck.chunkRelativeOffset(i),j,str);
    }
    public Futures close(Futures fs) {
      fs.add(_fs);
      if(_cache != null)_cache.close(_fs);
      return postWrite(fs);
    }
    public void close() { close(new Futures()).blockForPending(); }
  }

  /** Create a writer for bulk serial writes into this Vec.
   *  @return A Writer for bulk serial writes */
  public final Writer open() { return new Writer(); }

  public VecAry rebalance(int nchunks, boolean replaceSelf){
    VecAry rb = rebalance(nchunks);
    if(replaceSelf){
      VecAry toDelete = new VecAry(this);
      replaceWith(rb);
      rb = this;
      toDelete.remove();
    }
    return rb;
  }
  public VecAry rebalance(int nchunks){
    Frame fr = new Frame(this);
    Key<Frame> k = Key.make();
    RebalanceDataSet rb = new RebalanceDataSet(fr, k, 3);
    H2O.submitTask(rb).join();
    Frame rebalanced_fr = k.get();
    DKV.remove(k);
    return rebalanced_fr.vecs();
  }

  public VecAry align(VecAry v, boolean replaceSelf){
    VecAry rb = align(v);
    if(replaceSelf){
      VecAry toDelete = new VecAry(this);
      replaceWith(rb);
      rb = this;
      toDelete.remove();
    }
    return rb;
  }
  public VecAry align(VecAry v){
    if(length() != v.length())
      throw new IllegalArgumentException("Can not align to a vector with different lenght, mylen = " + length() + ", vec.len() = " + v.length());
    Frame fr = new Frame(this);
    Frame src = new Frame(v);
    Key<Frame> k = Key.make();
    RebalanceDataSet rb = new RebalanceDataSet(src,fr, k,null,null);
    H2O.submitTask(rb).join();
    Frame rebalanced_fr = DKV.get(k).get();
    VecAry vecs = rebalanced_fr.vecs();
    DKV.remove(k);
    return vecs;
  }

  public boolean equals(VecAry ary){
    return Arrays.equals(_vecIds,ary._vecIds) && Arrays.equals(_colFilter,ary._colFilter);
  }


}

package water.fvec;

import water.*;
import water.parser.BufferedString;
import water.util.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * Created by tomas on 7/7/16.
 *
 * Abstraction over an array of Vecs (columns) and primary interface for working with Vecs.
 *
 * Provides access to individual vectors, to their chunks and values and supports basic operations such as adding and subsetting.
 *
 * Vecs can be stored in simple 1D vectors or 2D vector blocks. VecArray asbtracts from that by storing a pointer
 *
 *
 */
public class VecAry extends Iced {
  // AVecs stored as a group key + vecIds

  private final class VecSet {
    final int _min;
    final int _max;
    final AVec [] _vs;

    public VecSet(AVec... vs) {
      if(vs != null && vs.length != 0)
        _vgTemplate = vs[0].groupKey();
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for(AVec a:vs) {
        int id = a.vecId();
        if(id < min) min = id;
        if(id > max) max = id;
      }
      _vs = vs;
      _min = min;
      _max = max;
    }

    public VecSet(int min, int max, AVec [] vs) {
      _min = min;
      _max = max;
      _vs = vs;
      if(vs != null && vs.length != 0)
        _vgTemplate = vs[0].groupKey();
    }


    public boolean contains(VecSet s) {
      return _min <= s._min && s._max <= _max;
    }

    public VecSet add(VecSet s){
      if(contains(s)) return this;
      if(s.contains(this)) return s;
      if(s._min < _min) {
        assert s._max < _max;
        AVec [] vs = new AVec[_max - s._min + 1];
        // TODO
        return new VecSet(s._min,_max,vs);
      } else {
        assert s._max > _max;
        assert s._min > _min;
        AVec [] vs = new AVec[s._max - _min + 1];
        // TODO
        return new VecSet(_min,s._max,vs);
      }
    }
    public VecSet set(AVec v){
      int id = v.vecId();
      if(_vs == null) {
        _vgTemplate = v.groupKey();
        return new VecSet(id, id, new AVec[]{v});
      }
      if(id < _min) {
        AVec [] vs = new AVec[_max - id];
        int off = _min - id;
        for(int i = 0; i < _vs.length; ++i)
          vs[off+i] = _vs[i];
        vs[0] = v;
        return new VecSet(id,_max,vs);
      } else if(id > _max) {
        AVec [] vs = new AVec[id - _min];
        for(int i = 0; i < _vs.length; ++i)
          vs[i] = _vs[i];
        vs[vs.length-1] = v;
        return new VecSet(_min,id,vs);
      }
      _vs[id - _min] = v;
      return this;
    }

    public AVec anyVec(){return _vs[0];}
    public AVec get(int vecId) {
      if(_max < vecId || vecId < _min)
        return null;
      int i = vecId - _min;
      AVec res = _vs[i];
      if(res == null)
        res = _vs[i] = fetchVec(vecId);
     return res;
    }
    public VecSet subset(int min, int max) {
      return new VecSet(min,max,Arrays.copyOfRange(_vs,min - _min,_max - max + 1));
    }

    public VecSet subset(int[] vids) {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for(int i = 0; i <vids.length; i += 2){
        int vid = vids[i];
        if(vid < min) min = vid;
        if(vid < max) max = vid;
      }
      return subset(min,max);
    }

    public int size() {
      return _max - _min + 1;
    }
  }
  private VecSet _vecs = new VecSet(-1,-1,null);
  private Key _vgTemplate;
  private transient ThreadLocal<Key> _vg = new ThreadLocal<Key>();

  private Key vg(){
    Key res = _vg.get();
    if(res == null)
      _vg.set(res = Key.make(_vgTemplate._kb.clone()));
    return res;
  }
  private AVec fetchVec(int i) {
    Key k = vg();
    AVec.VectorGroup.setVecId(k._kb,i);
    return DKV.getGet(k);
  }

  private static final long _vecSetOffset;

  static {
    try {
      _vecSetOffset = UnsafeUtils.getObjectFieldOffset(VecAry.class.getField("_vecs"));
    } catch (NoSuchFieldException e) {
      throw H2O.fail();
    }
  }


  int [] _vids; // each element is 2 ints, vector (block) id withint the VG, vec id within the block

  private static class VecsPerBlock {
    AVec  [] _blocks;
    int [][] _vecs;
  }
  private VecsPerBlock vecsPerBlock() {
    int [] blocks = new int[_vids.length >> 1];
    blocks[0] = _vids[0];
    int j = 0;
    for(int i = 2; i < _vids.length; i+= 2){
      int b = _vids[i];
      if(b != blocks[j]) blocks[++j] = b;
    }
    blocks = Arrays.copyOf(blocks,j);
    Arrays.sort(blocks);
    throw H2O.unimpl();
  }

  public VecAry(){}
  private VecAry(VecSet vs, int [] vids){
    _vecs = vs;
    _vids = vids;
  }

  private void _addAVec(AVec v) {
    VecSet vs = _vecs.set(v);
    while(vs != _vecs && !UnsafeUtils.compareAndSet(this,_vecSetOffset,_vecs,vs))
      vs = _vecs.set(v);
  }

  private void _addAVecs(VecSet vs) {
    VecSet vecs = _vecs;
    while(!vecs.contains(vs) && !UnsafeUtils.compareAndSet(this,_vecSetOffset,vecs,vecs.add(vs)))
      vecs = _vecs;
  }

  public VecAry(AVec... vs){
    _vecs = new VecSet(vs);
    _vids = new int[vs.length*2];
    int k = 0;
    for(AVec v:vs) {
      int i = AVec.VectorGroup.getVecId(v._key._kb);
      for(int j = 0; j < v.numCols(); ++j) {
        _vids[k++] = i;
        _vids[k++] = j;
      }
    }
  }

  public VecAry(VecAry... vs){
    this();
    for(VecAry v:vs) addVecs(v);
  }

  public VecAry(AVec v, int [] ids){
    _vecs = new VecSet(v);
    int i = AVec.VectorGroup.getVecId(v._key._kb);
    _vids = new int[ids.length*2];
    int k = 0;
    for(int j:ids) {
      _vids[k++] = i;
      _vids[k++] = j;
    }
  }

  public VecAry(VecAry v) {
    _vids = v._vids.clone();}

  public VecAry addVec(AVec... vs) {
    for(AVec v:vs) {
      _addAVec(v);
      int k = _vids.length;
      _vids = Arrays.copyOf(_vids, _vids.length + v.numCols() * 2);
      int id = v.vecId();
      for (int i = 0; i < v.numCols(); ++i) {
        _vids[k++] = id;
        _vids[k++] = i;
      }
    }
    return this;
  }

  public VecAry addVecs(VecAry vs) {
    _addAVecs(vs._vecs);
    _vids = ArrayUtils.add(_vids,vs._vids);
    return this;
  }

  public AVec getAVecRaw(int i) {return _vecs.get(i);}


  public AVec getAVecForCol(int i) {return _vecs.get(_vids[i<<1]);}

  public boolean isInt(int col) {
    return _vecs.get(_vids[2*col]).isInt(_vids[2*col + 1]);
  }

  public boolean isTime(int col) {
    return _vecs.get(_vids[2*col]).isTime(_vids[2*col + 1]);
  }

  public long[] espc() {
    VecSet vs = _vecs;
    return vs._vs == null?null:vs._vs[0].espc();
  }

  public String[][] domains() {
    String [][] res = new String[len()][];
    int i = 0;
    int k = 0;
    while(i < _vids.length) {
      int b = _vids[i];
      AVec av = _vecs.get(_vids[i]);
      while(i < _vids.length && _vids[i] == b) {
        res[k++] = av.domain(_vids[i+1]);
        i += 2;
      }
    }
    return res;
  }

  public String[] domain(int i) {
    return _vecs.get(_vids[2*i]).domain(_vids[2*i+1]);
  }

  public byte type(int i){
    return _vecs.get(_vids[2*i]).type(_vids[2*i+1]);
  }

  public byte[] types() {
    byte [] res = new byte[len()];
    int i = 0;
    int k = 0;
    while(i < _vids.length) {
      int b = _vids[i];
      AVec av = _vecs.get(_vids[i]);
      while(i < _vids.length && _vids[i] == b) {
        res[k++] = av.type(_vids[i+1]);
        i += 2;
      }
    }
    return res;
  }

  public double min(int i) {
    return _vecs.get(_vids[2*i]).min(_vids[2*i+1]);
  }

  public double max(int i) {
    return _vecs.get(_vids[2*i]).max(_vids[2*i+1]);
  }

  public double mean(int i) {
    return _vecs.get(_vids[2*i]).mean(_vids[2*i+1]);
  }

  public int mode(int i) {
    return _vecs.get(_vids[2*i]).mode(_vids[2*i+1]);
  }

  public double sigma(int i) {
    return _vecs.get(_vids[2*i]).sigma(_vids[2*i+1]);
  }

  public boolean isCategorical(int i) {
    return _vecs.get(_vids[2*i]).isCategorical(_vids[2*i+1]);
  }

  public boolean isUUID(int i) {
    return _vecs.get(_vids[2*i]).isCategorical(_vids[2*i+1]);
  }

  public boolean isString(int i) {
    return _vecs.get(_vids[2*i]).isCategorical(_vids[2*i+1]);
  }

  public long nzCnt(int i) {
    return _vecs.get(_vids[2*i]).nzCnt(_vids[2*i+1]);
  }



  public long byteSize() {
    long res = 0;
    int i = 0;
    while(i < _vids.length) {
      int b = _vids[i];
      AVec av = _vecs.get(_vids[i]);
      res += av.byteSize();
      while(i < _vids.length && _vids[i] == b)
        i += 2;
    }
    return res;
  }

  public boolean isRawBytes() {
    return _vecs._min == _vecs._max && _vecs.get(0) instanceof ByteVec;
  }

  public void reload() {Arrays.fill(_vecs._vs,null);}

  public VecAry makeCompatible(VecAry vecAry, boolean b) {
    if(!b  && vecAry.isCompatible(this))
      return this;
    return H2O.submitTask(new RebalanceDataSet(vecAry,this)).getResult();
  }

  public boolean isHomedLocally(int lo) {
    Key k = vg();
    AVec.setChunkId(k,lo);
    return k.home();
  }

  public void close(){close(new Futures()).blockForPending();}

  public Futures close(Futures fs) {
    AVec [] vecs = _vecs._vs;
    for(int i = 0; i < vecs.length; ++i)
      if(vecs[i] != null)
        vecs[i].postWrite(fs);
    return fs;

  }

  public int elem2ChunkIdx(long l) {
    int res = Arrays.binarySearch(espc(),l);
    return res < 0?(-res-2):res;
  }

  public void remove() {
    Futures fs = new Futures();
    remove(fs);
    fs.blockForPending();
  }

  public VecAry deepCopy() {
    final VecBlock vb = new VecBlock(group().addVec(),rowLayout(),len(),domains(),types());
    new MRTask(){
      @Override public void map(Chunk [] chks) {
        chks = chks.clone();
        for(int i = 0; i < chks.length; ++i)
          chks[i] = chks[i].deepCopy();
        new ChunkBlock(vb,chks[0].cidx(),chks).close(_fs);
      }
    }.doAll(this);
    DKV.put(vb._key,vb);
    return new VecAry(vb);
  }


  public void setBad(int i) {
    _vecs.get(_vids[2*i]).setBad(_vids[2*i+1]);
  }

  public void setDomain(int i, String[] domain) {
    _vecs.get(_vids[2*i]).setDomain(_vids[2*i+1],domain);
  }

  public VecAry adaptTo(String [] domain){
    if(len() != 1) throw new IllegalArgumentException("can only be applied to a singel vec");
    return new VecAry(new CategoricalWrappedVec(group().addVec(),rowLayout(),domain,this));
  }

  public boolean isConst(int i) {
    RollupStats rs = getRollups(i);
    return rs._mins[0] == rs._maxs[0];
  }

  public long naCnt(int i) {return getRollups(i)._naCnt;}

  public boolean isNumeric(int i) {return type(i) == Vec.T_NUM;}

  public Key<AVec>[] keys() {
    TreeSet<Integer> ks = new TreeSet<>();
    for(int i = 0; i < _vids.length; i += 2)
      ks.add(_vids[i]);
    Key<AVec> [] res = new Key[ks.size()];
    int j = 0;
    Key k = vg();
    for(int i:ks) {
      byte [] kb = k._kb.clone();
      AVec.VectorGroup.setVecId(kb,i);
      res[j++] = Key.make(kb);
    }
    return res;
  }

  public boolean isBad(int i) {return type(i) == Vec.T_BAD;}

  public boolean isBinary(int i) {
    RollupStats rs = getRollups(i);
    return rs._isInt && rs._mins[0] >= 0 && rs._maxs[0] <= 1;
  }

  public VecAry setVec(int id, VecAry vec) {
    if(vec.len() != 1) throw new IllegalArgumentException();
    if(len() == id) return addVecs(vec);
    VecAry res = new VecAry(_vecs.get(_vids[2*id]),new int[]{_vids[2*id+1]});
    _addAVec(vec.getAVecRaw(0));
    _vids[2*id+0] = vec._vids[0];
    _vids[2*id+1] = vec._vids[1];
    return res;
  }

  public VecAry replaceVecs(VecAry vecs, int... cols) {
    VecAry res = getVecs(cols);
    _addAVecs(vecs._vecs);
    for(int i = 0; i < cols.length; i++) {
      int x = 2*cols[i];
      _vids[x+0] = vecs._vids[i*2+0];
      _vids[x+1] = vecs._vids[i*2+1];
    }
    reload();
    return res;
  }

  public double[] means() {
    RollupStats [] rs = getRollups();
    double [] res = new double[rs.length];
    for(int i = 0; i < res.length; ++i)
      res[i] = rs[i].mean();
    return res;
  }

  public int rowLayout() {return _vecs.get(_vecs._min)._rowLayout;}

  public VecAry makeCopy(String[] domains){
    if(len() != 1) throw new IllegalArgumentException();
    return makeCopy(new String[][]{domains});
  }
  public VecAry makeCopy(String[][] domains){
    return makeCopy(domains,types());
  }
  public VecAry makeCopy(String[][] domains, byte... types) {
    if(domains.length != types.length)
      throw new IllegalArgumentException();
    VecAry res = deepCopy();
    res.setMeta(types,domains);
    return res;
  }

  public void setMeta(byte [] types, String[][] domains) {
    if(types.length != len()) throw new IllegalArgumentException();
    if(domains != null && domains.length != len()) throw new IllegalArgumentException();
    int i = 0;
    int k = 0;
    while(i < _vids.length) {
      int b = _vids[i];
      AVec av = _vecs.get(_vids[i]);
      while(i < _vids.length && _vids[i] == b) {
        av.setDomain(_vids[i+1],domains[k]);
        av.setType(_vids[i+1],types[k]);
        k++;
        i += 2;
      }
    }
  }

  public String[] typesStr() {  // typesStr not strTypes since shows up in intelliJ next to types
    String s[] = new String[len()];
    for(int i=0;i<len();++i)
      s[i] = Vec.TYPE_STR[type(i)];
    return s;
  }

  public int cardinality(int i) {
    return _vecs.get(_vids[2*i]).cardinality(_vids[2*i+1]);
  }

  public double[] pctiles(int i) {
    return _vecs.get(_vids[2*i]).pctiles(_vids[2*i+1]);
  }

  public long[] bins(int i) {
    return _vecs.get(_vids[2*i]).bins(_vids[2*i+1]);
  }

  /**
   * Copy out all vecs in this ary which are supported by the given AVec.
   * Used for reference counting on AVecs.
   * @param k
   */
  public void replaceWithCopy(Key<AVec> k) {
    int id = AVec.VectorGroup.getVecId(k._kb);
    ArrayList<Integer> idList = new ArrayList<>();
    for(int i = 0; i < _vids.length; i += 2)
      if(_vids[i] == id) idList.add(_vids[i+1]);
    if(idList.isEmpty()) return;
    int [] ids = new int[idList.size()];
    for(int i = 0; i < idList.size(); ++i)
      ids[i] = idList.get(i);
    Arrays.sort(ids);
    VecAry v = new VecAry(_vecs.get(id),ids).deepCopy();
    int newId = v.getAVecRaw(0).vecId();
    for(int i = 0; i < _vids.length; i += 2) {
      if(_vids[i] == id) {
        _vids[i] = newId;
        _vids[i+1] = Arrays.binarySearch(ids,_vids[i+1]);
      }
    }
  }

  public int homeNode(int c) {
    Key k = vg();
    AVec.setChunkId(k,c);
    return k.home_node().index();
  }

  public int [] categoricals() {
    int [] res = new int[len()];
    int j = 0;
    for(int i = 0; i < res.length; ++i)
      if(isCategorical(i))
        res[j++] = i;
    return j == res.length?res:Arrays.copyOf(res,j);
  }



  public double[] sigmas() {
    double [] res = new double[len()];
    RollupStats[] rolls = getRollups();
    for(int i = 0; i < res.length; ++i)
      res[i] = rolls[i].sigma();
    return res;
  }

  public double sparseRatio(int i) {
    return (double)nzCnt(i)/numRows();
  }

  public VecAry makeCons(int totcols, double value) {
    byte [] types = new byte[totcols];
    Arrays.fill(types,Vec.T_NUM);
    return makeCons(totcols,value,null,types);
  }

  public boolean isNA(long i, int j) {
    return getChunk(elem2ChunkIdx(i),j).isNA_abs(i);
  }



  public double at(long i, int j) {
    return getChunk(elem2ChunkIdx(i),j).at_abs(i);
  }

  public void set(long i, int j, double d) {
    Chunk c = getChunk(elem2ChunkIdx(i),j);
    c.set_abs(i,d);
    c.close(new Futures()).blockForPending();
    postWrite(new Futures()).blockForPending();
  }

  public BufferedString atStr(BufferedString str, long row, int vecId) {
    int b = _vids[2*vecId];
    int v = _vids[2*vecId+1];
    return _vecs.get(b).chunkForRow(row).getChunk(v).atStr_abs(str,row);
  }

  public long at8(long row, int vecId) {
    int b = _vids[2*vecId];
    int v = _vids[2*vecId+1];
    return _vecs.get(b).chunkForRow(row).getChunk(v).at8_abs(row);
  }

  public int at4(long rowId, int vecId) {
    long l = at8(rowId,vecId);
    int i = (int)l;
    if(i != l) throw new IllegalArgumentException("can not fit in integer, l = " + l);
    return i;
  }

  public long at16l(int row, int vecId) {
    int b = _vids[2*vecId];
    int v = _vids[2*vecId+1];
    return _vecs.get(b).chunkForRow(row).getChunk(v).at16l_abs(row);
  }

  public long at16h(int row, int vecId) {
    int b = _vids[2*vecId];
    int v = _vids[2*vecId+1];
    return _vecs.get(b).chunkForRow(row).getChunk(v).at16h_abs(row);
  }

  public VecAry makeCopy() {return makeCopy((String[][])null);}



  public int find(VecAry vec) {
    if(vec.len() != 1) throw new IllegalArgumentException();
    int x0 = vec._vids[0];
    int x1 = vec._vids[1];
    for(int i = 0; i < _vids.length; i += 2) {
      if(_vids[i] == x0 && _vids[i+1] == x1)
        return i >> 1;
    }
    return -1;
  }

  public void startRollupStats(Futures fs, boolean b) {
    VecsPerBlock vpb = vecsPerBlock();
    for(int i = 0; i < vpb._blocks.length; ++i)
      vpb._blocks[i].startRollupStats(fs,b,vpb._vecs[i]);
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
  public class Reader implements Closeable {

    private final boolean _dontCache;
    public Reader(boolean dontChache) {
      _dontCache = dontChache;}
    protected ChunkAry _cache = null;

    long _start = Long.MAX_VALUE;
    long _end = Long.MIN_VALUE;

    private void nukeCache(){
      _cache.close(new Futures()).blockForPending();
      if(_dontCache && !isHomedLocally(_cache._cidx))
        for (int i = 0; i < _cache._chks.length; ++i)
          if (_cache._chks[i] != null) H2O.raw_remove(_cache._chks[i]._vec.chunkKey(_cache._cidx));
      _cache = null;
    }
    protected Chunk chk(long rowId, int vecId) {
      if(_cache == null ||  _end <= rowId || rowId < _start) {
        if(_cache != null ) nukeCache();
        int cidx = Arrays.binarySearch(espc(),rowId);
        if(cidx < 0) cidx = (-cidx-2);
        _cache = getChunks(elem2ChunkIdx(rowId));
        _start = espc()[cidx];
        _end = espc()[cidx+1];
      }
      return _cache.getChnunk(vecId);
    }

    public final long at8(long rowId, int vecId) {return chk(rowId, vecId).at8((int) (rowId - _start));}
    public final double at(long rowId, int vecId) {
      return chk(rowId, vecId).atd((int) (rowId - _start));
    }
    public long at16l(long rowId, int vecId) {
      return chk(rowId, vecId).at16l((int) (rowId - _start));
    }
    public long at16h(long rowId, int vecId) {
      return chk(rowId, vecId).at16h((int) (rowId - _start));
    }
    public final boolean isNA(long rowId, int vecId) {
      return chk(rowId, vecId).isNA((int) (rowId - _start));
    }

    public final long length() {
      return numRows();
    }

    public BufferedString atStr(BufferedString tmpStr, long rowId, int vecId) {
      return chk(rowId, vecId).atStr(tmpStr, (int) (rowId - _start));
    }
    public String factor(long rowId, int vecId) {
      return domain(vecId)[chk(rowId, vecId).at4((int) (rowId - _start))];
    }

    public Futures close(Futures fs){
      nukeCache();
      return fs;
    }
    @Override
    public final void close() {close(new Futures()).blockForPending();}
  }

  public final class Writer extends Reader implements Closeable {
    public Writer(boolean dontCache) {super(dontCache);}
    public void set  (long rowId, int vecId, long val)   { chk(rowId,vecId).set((int)(rowId-_start), val); }
    public void set  ( long rowId, int vecId, double val) { chk(rowId,vecId).set((int)(rowId-_start), val); }
    public void set(long rowId, int vecId, String s) {
      chk(rowId,vecId).set((int)(rowId-_start), s);
    }
    public void setNA( long rowId, int vecId) { chk(rowId,vecId).setNA((int)(rowId-_start)); }
    public Futures close(Futures fs){
      super.close(fs);
      return postWrite(fs);
    }
  }


  public Reader reader() { return reader(false);}
  public Reader reader(boolean dontCache) {
    return new Reader(dontCache);
  }
  public Writer open() { return open(false);}
  public Writer open(boolean dontCache) {
     return new Writer(dontCache);
  }

  public Futures remove(Futures fs) {
    int i = 0;
    while(i < _vids.length) {
      int b = _vids[i];
      AVec av = _vecs.get(_vids[i]);
      int [] ids = new int[av.numCols()];
      int k = 0;
      while(i < _vids.length && _vids[i] == b) {
        ids[k++] = _vids[i+1];
        i += 2;
      }
      av.removeVecs(Arrays.copyOf(ids,k),fs);
    }
    return fs;
  }

  public VecAry getVecs(int... ids) {
    int [] vids = new int[ids.length*2];
    int j = 0;
    for(int i:ids) {
      vids[j++] = _vids[2*i];
      vids[j++] = _vids[2*i+1];
    }
    return new VecAry(_vecs.subset(vids),vids);
  }

  public int len(){
    return _vids.length >> 1;
  }

  public AVec.VectorGroup group() {
    return _vecs.anyVec().group();
  }

  public int nChunks() {
    return _vecs.anyVec().nChunks();
  }

  public boolean isCompatible(VecAry vecs) {
    return Arrays.equals(espc(),vecs.espc()) && (numRows() < 1e3 || group().equals(vecs.group()));
  }

  public long numRows() {
    long [] espc = espc();
    return espc[espc.length-1];
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

  public void swap(int lo, int hi) {
    lo *= 2;
    hi *=2;
    int x0 = _vids[lo];
    int x1 = _vids[lo+1];
    _vids[lo] = _vids[hi];
    _vids[lo+1] = _vids[hi+1];
    _vids[hi] = x0;
    _vids[hi+1] = x1;
  }

  public VecAry subRange(int startIdx, int endIdx) {
    int [] vids = Arrays.copyOfRange(_vids,2*startIdx,2*endIdx+2);
    return new VecAry(_vecs.subset(vids),vids);
  }

  public VecAry removeVecs(int... id) {
    throw H2O.unimpl(); // TODO
  }

  public VecAry removeRange(int startIdx, int endIdx) {
    throw H2O.unimpl(); // TODO
  }

  public static class SparseChunks {
    public int [] ids;
    public Chunk [] chks;
  }

  public RollupStats[] getRollups() {
    RollupStats [] rs = new RollupStats[len()];
    throw H2O.unimpl();
  }
  public RollupStats getRollups(int vecId) {
    if(vecId < 0 || vecId > len()) throw new ArrayIndexOutOfBoundsException(vecId);
    return _vecs.get(_vids[2*vecId]).getRollups(_vids[2*vecId+1]);
  }

  private transient int _chunkId = -1;
  private transient int _blockId = 0;
  private transient AVec.AChunk _chk = null;

  public class ChunkAry {
    private final AVec.AChunk [] _chks;
    public final int _cidx;

    public ChunkAry(int cidx) {
      _cidx = cidx;
      _chks = new AVec.AChunk[_vecs._max-_vecs._min+1];
    }

    private AVec.AChunk achunk(int j) {
      if(_chks[j] == null) {
        _chks[j] = _vecs.get(j).chunkForChunkIdx(_cidx);
      }
      return _chks[j];
    }

    public Chunk getChnunk(int vecId) {
      int idx = vecId << 1;
      int b = _vids[idx+0];
      int v = _vids[idx+1];
      return achunk(b).getChunk(v);
    }


    public Chunk [] chks(){
      Chunk [] res = new Chunk [len()];
      int i = 0;
      int k = 0;
      while(i < _vids.length) {
        int b = _vids[i];
        AVec.AChunk ac = achunk(i);
        if(ac instanceof SingleChunk) {
          res[k++] = ((SingleChunk)ac)._c;
          i+= 2;
        } else  {
          Chunk [] chks = ac.getChunks();
          while(i < _vids.length && _vids[i] == b) {
            res[k++] = chks[_vids[i+1]];
            i += 2;
          }
        }
      }
      return res;
    }

    public Futures close(Futures fs){
      for(int i = 0; i < _chks.length; ++i)
        if(_chks[i] != null) _chks[i].close(fs);
      return fs;
    }
  }

  private transient ChunkAry _chunkCache;
  public Chunk getChunk(int chunkId, int vecId) {
    ChunkAry cary = _chunkCache;
    if(_chunkCache == null || _chunkCache._cidx != chunkId)
      _chunkCache = cary = getChunks(chunkId);
    return cary.getChnunk(vecId);
  }

  public ChunkAry getChunks(int cidx) {return getChunks(cidx,true);}
  public ChunkAry getChunks(int cidx, boolean cache) {
    return new ChunkAry(cidx);
  }


  /** Begin writing into this Vec.  Immediately clears all the rollup stats
   *  ({@link #min}, {@link #max}, {@link #mean}, etc) since such values are
   *  not meaningful while the Vec is being actively modified.  Can be called
   *  repeatedly.  Per-chunk row-counts will not be changing, just row
   *  contents. */
  public void preWriting(){
    for(int i:_vids) {
      if(!_vecs.get(_vids[2*i]).writable())
        throw new IllegalArgumentException("Vector not writable");
    }
    VecsPerBlock vpb = vecsPerBlock();
    for(int i = 0; i < vpb._blocks.length; ++i)
      vpb._blocks[i].preWriting(vpb._vecs[i]);
  }

  /** Stop writing into thiposs Vec.  Rollup stats will again (lazily) be
   *  computed. */
  public Futures postWrite( Futures fs ) {
    for(int i:_vids) {
      if(!_vecs.get(_vids[2*i]).writable())
        throw new IllegalArgumentException("Vector not writable");
    }
    VecsPerBlock vpb = vecsPerBlock();
    for(int i = 0; i < vpb._blocks.length; ++i)
      vpb._blocks[i].postWrite(fs);
    return fs;
  }

  public VecAry makeCons(final double... cons) {
    final int n = cons.length;
    final AVec av = n == 1
        ?new Vec(group().addVec(),rowLayout(),null, Vec.T_NUM)
        :new VecBlock(group().addVec(),rowLayout(), n, null,ArrayUtils.expandByteAry(Vec.T_NUM,n));
    new MRTask() {
      public void setupLocal() {
        for(int i = 0; i < av.nChunks(); ++i) {
          final long [] espc = espc();
          if(av.chunkKey(i).home()) {
            // int numCols, int len, int [] nzChunks, Chunk [] chunks, double sparseElem
            int len = (int) (espc[i + 1] - espc[i]);
            AVec.AChunk aChunk = n == 1?new SingleChunk(av,i,new C0DChunk(cons[0], len)):new ChunkBlock(av,i,n, len, cons);
            aChunk.close(_fs);
          }
        }
      }
    }.doAllNodes();
    DKV.put(av._key,av);
    return new VecAry(av);
  }
  // Make a bunch of compatible zero Vectors
  public VecAry makeCons(final int n, final double con, String[][] domains, byte[] types) {
    final AVec av = n == 1
        ?new Vec(group().addVec(),rowLayout(),domains == null?null:domains[0], types[0])
        :new VecBlock(group().addVec(),rowLayout(), n, domains,types);
    new MRTask() {
      public void setupLocal() {
        for(int i = 0; i < av.nChunks(); ++i) {
          final long [] espc = espc();
          if(av.chunkKey(i).home()) {
            // int numCols, int len, int [] nzChunks, Chunk [] chunks, double sparseElem
            int len = (int) (espc[i + 1] - espc[i]);
            AVec.AChunk aChunk = n == 1?new SingleChunk(av,i,new C0DChunk(con, len)):new ChunkBlock(av,i,n, len, con);
            aChunk.close(_fs);
          }
        }
      }
    }.doAllNodes();
    DKV.put(av._key,av);
    return new VecAry(av);
  }

  /** Make a new vector with the same size and data layout as the current one,
   *  and initialized to zero.
   *  @return A new vector with the same size and data layout as the current one,
   *  and initialized to zero.  */
  public VecAry makeZero() { return new VecAry(Vec.makeCon(0, null, group(), rowLayout())); }
  public VecAry makeCon(long con) { return new VecAry(Vec.makeCon(con, null, group(), rowLayout())); }
  public VecAry makeCon(double con) { return new VecAry(Vec.makeCon(con, null, group(), rowLayout())); }

  /** A new vector with the same size and data layout as the current one, and
   *  initialized to zero, with the given categorical domain.
   *  @return A new vector with the same size and data layout as the current
   *  one, and initialized to zero, with the given categorical domain. */
  public VecAry makeZero(String[] domain) { return new VecAry(Vec.makeCon(0, domain, group(), rowLayout())); }
  public VecAry makeZeros(int n){return makeZeros(n,null,null);}
  public VecAry makeZeros(int n, String [][] domain, byte[] types){ return makeCons(n, 0, domain, types);}

  /** Make a new vector initialized to random numbers with the given seed */
  public VecAry makeRand( final long seed ) {
    VecAry randVec = makeZero();
    new MRTask() {
      @Override public void map(Chunk c){
        Random rng = new RandomUtils.PCGRNG(c.start(),1);
        for(int i = 0; i < c._len; ++i) {
          rng.setSeed(seed+c.start()+i); // Determinstic per-row
          c.set(i, rng.nextFloat());
        }
      }
    }.doAll(randVec);
    return randVec;
  }

//  public static class SingleVecAry extends VecAry {
//    private Key _blockKey;
//    private int _vecId; // id within the block
//    private transient AVec _vec;
//
//    protected AVec vec() {
//      if(_vec != null)return _vec;
//      return _vec = DKV.getGet(_blockKey);
//    }
//  }
//
//  public static class SingleVecAry extends VecAry {
//    private Key _blockKey;
//    private int _vecId; // id within the block
//    private transient AVec _vec;
//
//    protected AVec vec() {
//      if(_vec != null)return _vec;
//      return _vec = DKV.getGet(_blockKey);
//    }
//  }




}

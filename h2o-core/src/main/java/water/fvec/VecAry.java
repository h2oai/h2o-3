package water.fvec;

import water.*;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.RandomUtils;
import water.util.VecUtils;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

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
  private transient AVec[] _vblocks;
  //
  private Key _vg;
  private int [] _blockIds;

  protected AVec[] avecs(){
    if(_vblocks != null) return _vblocks;
    synchronized(_vg) {
      if(_vblocks != null) return _vblocks;
      AVec[] vblocks = new AVec[_blockIds.length];
      for (int i = 0; i < vblocks.length; ++i) {
        AVec.VectorGroup.setVecId(_vg._kb, _blockIds[i]);
        vblocks[i] = DKV.getGet(_vg);
      }
    }
    return _vblocks;
  }

  int [][] _vecIds;
  int [] _vecOffsets;


  public VecAry(){}
  private void setAVecs(AVec... v) {
    assert _vg == null;
    _vg = v[0].groupKey();
    _vblocks = v.clone();
    _blockIds = new int[v.length];
    for(int i = 0; i < v.length; ++i)
      _blockIds[i] = AVec.VectorGroup.getVecId(v[i]._key._kb);

  }

  private void calculateVecOffsets(AVec... v){
    _vecOffsets = new int[v.length+1];
    int sum = 0;
    for(int i = 0; i < v.length; ++i) {
      _vecOffsets[i] = sum;
      sum += (_vecIds == null || _vecIds[i] == null)?v[i].numCols():_vecIds[i].length;
    }
    _vecOffsets[_vecOffsets.length-1] = sum;
  }
  public VecAry(AVec... v){
    setAVecs(v);
    calculateVecOffsets(v);
  }

  public VecAry(VecAry... vs){
    this();
    for(VecAry v:vs) addVecs(v);
  }

  public VecAry(AVec v, int [] ids){
    setAVecs(v);
    _vecIds = new int[][]{ids};
    _vecOffsets = new int[]{0, ids.length};
  }

  public VecAry(VecAry v) {
    setAVecs(v._vblocks);
    if(v._vecIds != null)
      _vecIds = ArrayUtils.deepClone(v._vecIds);
    if(_vecOffsets != null)
      _vecOffsets = v._vecOffsets.clone();
  }

  public VecAry addVecs(AVec... vs) {
    _vblocks = ArrayUtils.join(_vblocks,vs);
    if(_vecIds != null)
      _vecIds = Arrays.copyOf(_vecIds,_vecIds.length+vs.length);
    return this;
  }

  public VecAry addVecs(VecAry vs) {
    _vblocks = ArrayUtils.join(_vblocks,vs._vblocks);
    _vecIds = ArrayUtils.add(_vecIds,vs._vecIds);
    int n = _vecOffsets.length -1 ;
    int len = _vecOffsets[n];
    _vecOffsets = ArrayUtils.join(_vecOffsets,vs._vecOffsets);
    for(int j = n; j < _vecOffsets.length; ++j)
      _vecOffsets[j] += len;
    return this;
  }

  public AVec getAVecRaw(int i) {return _vblocks[i];}

  private int block(int i) {
    if(_vblocks.length == 1) return 0;
    int j = Arrays.binarySearch(_vecOffsets,i);
    if(j < 0) j = -j;
    return j;
  }

  private int vec(int blockId, int col) {
    int res = col - _vecOffsets[blockId];
    if(_vecIds != null && _vecIds[blockId] != null)
      return _vecIds[blockId][res];
    return res;
  }

  public AVec getAVecForCol(int i) {return getAVecRaw(block(i));}

  public boolean isInt(int col) {
    int blockId = block(col);
    return avecs()[blockId].isInt(vec(blockId,col));
  }

  public long[] espc() {return avecs()[0].espc();}

  public String[][] domains() {
    String [][] res = new String[len()][];
    int col = 0;
    for(AVec v:avecs())
      for(int i = 0; i < v.numCols(); ++i)
        res[col++] = v.domain(i);
    return res;
  }

  public String[] domain(int i) {
    int blockId = block(i);
    return avecs()[blockId].domain(vec(blockId,i));
  }

  public byte type(int i){
    int blockId = block(i);
    return avecs()[blockId].type(vec(blockId,i));
  }

  public byte[] types() {
    byte [] res = new byte[len()];
    int col = 0;
    for(AVec v:avecs())
      for(int i = 0; i < v.numCols(); ++i)
        res[col++] = v.type(i);
    return res;
  }

  public double min(int i) {
    int blockId = block(i);
    return avecs()[blockId].min(vec(blockId,i));
  }

  public double max(int i) {
    int blockId = block(i);
    return avecs()[blockId].max(vec(blockId,i));
  }

  public double mean(int i) {
    int blockId = block(i);
    return avecs()[blockId].mean(vec(blockId,i));
  }

  public double sigma(int i) {
    int blockId = block(i);
    return avecs()[blockId].sigma(vec(blockId,i));
  }

  public boolean isCategorical(int i) {
    int blockId = block(i);
    return avecs()[blockId].isCategorical(vec(blockId,i));
  }

  public boolean isUUID(int i) {
    int blockId = block(i);
    return avecs()[blockId].isUUID(vec(blockId,i));
  }

  public boolean isString(int i) {
    int blockId = block(i);
    return avecs()[blockId].isString(vec(blockId,i));
  }

  public long byteSize() {
    long res = 0;
    for(AVec a:avecs())
      res += a.byteSize();
    return res;
  }

  public boolean isRawBytes() {
    AVec [] vs = avecs();
    return vs.length == 1 && vs[0] instanceof ByteVec;
  }

  public void reload() {
    _vblocks = null;
    avecs();
  }

  public VecAry makeCompatible(VecAry vecAry, boolean b) {
    if(!b  && vecAry.isCompatible(this))
      return this;
    return H2O.submitTask(new RebalanceDataSet(vecAry,this)).getResult();
  }

  public boolean isHomedLocally(int lo) {
    return avecs()[0].chunkKey(lo).home();
  }

  public void close(){close(new Futures()).blockForPending();}

  public Futures close(Futures fs) {
    for(AVec a: avecs())
      a.postWrite(fs);
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


  public void setBad(int ecol) {
    int blockId = block(ecol);
    _vblocks[blockId].setBad(vec(blockId,ecol));
  }

  public void setDomain(int ecol, String[] domain) {
    int blockId = block(ecol);
    _vblocks[blockId].setDomain(vec(blockId,ecol),domain);
  }

  public VecAry adaptTo(String [] domain){
    if(len() != 1) throw new IllegalArgumentException("can only be applied to a singel vec");
    return new VecAry(new CategoricalWrappedVec(group().addVec(),_vblocks[0]._rowLayout,domain,this));
  }

  public boolean isConst(int i) {
    RollupStats rs = getRollups(i);
    return rs._mins[0] == rs._maxs[0];
  }

  public long naCnt(int i) {return getRollups(i)._naCnt;}

  public boolean isNumeric(int i) {return type(i) == Vec.T_NUM;}

  public Key<AVec>[] keys() {
    Key<AVec> [] res = new Key[_vblocks.length];
    for(int i = 0; i < res.length; ++i)
      res[i] = _vblocks[i]._key;
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
    int blockId = block(id);
    if(_vblocks[blockId].numCols() == 1) {
      AVec v = _vblocks[blockId];
      VecAry res = new VecAry(new AVec[]{v});
      _vblocks[blockId] = vec._vblocks[0];
      if(_vecIds != null) _vecIds[blockId] = null;
      return res;
    }
    throw H2O.unimpl();
  }

  /**
   * Helper method. Go over all other vecs and remove those which are not present int this array
   */
  public void removeTemps(VecAry vecs) {
    throw H2O.unimpl();
  }

  public double[] means() {
    RollupStats [] rs = getRollups();
    double [] res = new double[rs.length];
    for(int i = 0; i < res.length; ++i)
      res[i] = rs[i].mean();
    return res;
  }

  public int rowLayout() {return _vblocks[0]._rowLayout;}

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
    throw H2O.unimpl();
  }


  public String[] typesStr() {  // typesStr not strTypes since shows up in intelliJ next to types
    String s[] = new String[len()];
    for(int i=0;i<len();++i)
      s[i] = Vec.TYPE_STR[type(i)];
    return s;
  }

  public int cardinality(int i) {
    throw H2O.unimpl();
  }

  public double[] pctiles(int i) {
    throw H2O.unimpl();
  }

  public long[] bins(int i) {
    throw H2O.unimpl();
  }

  /**
   * Copy out all vecs in this ary which are supported by the given AVec.
   * Used for reference counting on AVecs.
   * @param k
   */
  public void replaceWithCopy(Key<AVec> k) {
    throw H2O.unimpl();
  }

  public int homeNode(int c) {return _vblocks[0].chunkKey(c).home_node().index();}

  public int [] categoricals() {
    int [] res = new int[len()];
    int j = 0;
    for(int i = 0; i < res.length; ++i)
      if(isCategorical(i))
        res[j++] = i;
    return j == res.length?res:Arrays.copyOf(res,j);
  }

  public int nzCnt(int i) {
    throw H2O.unimpl();
  }

  public double[] sigmas() {
    throw H2O.unimpl();
  }

  public double sparseRatio(int i) {
    return (double)nzCnt(i)/numRows();
  }

  public VecAry makeCons(int totcols, long value) {
    throw H2O.unimpl();
  }


  public VecAry replaceVecs(VecAry vecs, int... cols) {
    throw H2O.unimpl();
  }
  public boolean isTime(int n) {
    throw H2O.unimpl();
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

  public boolean isNA(long i, int j) {
    throw H2O.unimpl();
  }

  public BufferedString atStr(BufferedString str, long row, int vecId) {
    throw H2O.unimpl();
  }

  public long at8(long rowId, int vecId) {
    throw H2O.unimpl();
  }

  public int at4(long rowId, int vecId) {
    long l = at8(rowId,vecId);
    int i = (int)l;
    if(i != l) throw new IllegalArgumentException("can not fit in integer, l = " + l);
    return i;
  }

  public long at16l(int row, int col) {
    throw H2O.unimpl();
  }

  public long at16h(int row, int col) {
    throw H2O.unimpl();
  }

  public int mode(int i) {throw H2O.unimpl();}

  public AVec[] getAVecs(Class<? extends AVec> c) {
    ArrayList<AVec> res = new ArrayList<>();
    for(AVec a:_vblocks)
      if(c.isInstance(a))
        res.add(a);
    return res.toArray(new AVec[res.size()]);
  }

  public VecAry makeCopy() {return makeCopy((String[][])null);}

  public VecAry makeCons(double... cons) {throw H2O.unimpl();}

  public int find(VecAry vec) {throw H2O.unimpl();}

  public void startRollupStats(Futures fs, boolean b, int i) {
    throw H2O.unimpl();
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
  public class VecAryReader {

    private ChunkBlock _cache;
    long _start;

    protected Chunk chk(long rowId, int vecId) {
      throw H2O.unimpl(); // TODO
    }

    public final long at8(long rowId, int vecId) {
      return chk(rowId, vecId).at8((int) (rowId - _start));
    }

    public final double at(long rowId, int vecId) {
      return chk(rowId, vecId).at8((int) (rowId - _start));
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
      throw H2O.unimpl();
    }

    public long at16l(long row, int i) {
      throw H2O.unimpl();
    }

    public long at16h(long row, int i) {
      throw H2O.unimpl();
    }

    public int getDoubles(long rowId, int vecId, double[] dvals) {
      throw H2O.unimpl();
    }

    public int getStrings(long rowId, int vecId, String[] svals) {
      throw H2O.unimpl();
    }
  }

  public final class Writer extends VecAryReader implements Closeable {
    public final void set  ( long rowId, int vecId, long val)   { chk(rowId,vecId).set((int)(rowId-_start), val); }
    public final void set  ( long rowId, int vecId, double val) { chk(rowId,vecId).set((int)(rowId-_start), val); }
    public final void setNA( long rowId, int vecId) { chk(rowId,vecId).setNA((int)(rowId-_start)); }
    public Futures close(Futures fs){
      throw H2O.unimpl(); // TODO
    }

    public void close() {
      Futures fs = new Futures();
      close(fs);
      fs.blockForPending();
    }

    public void set(long rowId, int vecId, String s) {
      throw H2O.unimpl();
    }
  }


  public VecAryReader reader(boolean keepCache) {
    throw H2O.unimpl(); // TODO
  }

  public Writer open() {
    throw H2O.unimpl(); // TODO
  }


  public Futures remove(Futures fs) {
    for(AVec v:_vblocks)
      v.remove(fs);
    return fs;
  }

  public VecAry getVecs(int... ids) {
    throw H2O.unimpl(); // TODO
  }

  public int len(){return _vecOffsets[_vecOffsets.length-1];}

  public AVec.VectorGroup group() {
    return _vblocks[0].group();
  }

  public int nChunks() {
    throw H2O.unimpl(); // TODO
  }

  public boolean isCompatible(VecAry vecs) {
    throw H2O.unimpl(); // TODO
  }

  public long numRows() {
    throw H2O.unimpl(); // TODO
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
    throw H2O.unimpl(); // TODO
  }

  public VecAry subRange(int startIdx, int endIdx) {
    throw H2O.unimpl(); // TODO
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
    int blockId = Arrays.binarySearch(_vecOffsets,vecId);
    if(blockId < 0) blockId = -blockId - 1;
    return _vblocks[blockId].getRollups(vecId-_vecOffsets[blockId]);
  }

  private transient int _chunkId = -1;
  private transient int _blockId = 0;
  private transient AVec.AChunk _chk = null;

  public class ChunkAry {
    private final AVec.AChunk [] _chks;
    public ChunkAry(AVec.AChunk [] chks) {_chks = chks;}

    public Chunk [] chks(){
      AVec [] avs = avecs();
      Chunk [] res = new Chunk [len()];
      int k = 0;
      for(int i = 0; i < _chks.length; ++i) {
        if(_vecIds != null && _vecIds[i] != null) {
          for(int j:_vecIds[i])
            res[k++] = _chks[i].getChunk(j);
        } else for(int j = 0; j < avs[i].numCols(); ++j) {
          res[k++] = _chks[i].getChunk(j);
        }
      }
      return res;
    }

    public Futures close(Futures fs){
      for(AVec.AChunk c:_chks) c.close(fs);
      return fs;
    }
  }

  public Chunk getChunk(int chunkId, int vecId){
    if(_chk != null && _chunkId == chunkId && _vecOffsets[_blockId] <= vecId && vecId < _vecOffsets[_blockId+1])
      return _chk.getChunk(vecId - _vecOffsets[_blockId]);
    if(vecId < 0 || vecId > len()) throw new ArrayIndexOutOfBoundsException(vecId);
    if(_vecIds == null) // simple vecs
      return _vblocks[vecId].chunkForChunkIdx(chunkId).getChunk(0);
    int blockId = Arrays.binarySearch(_vecOffsets,vecId);
    if(blockId < 0) blockId = -blockId - 1;
    _chunkId = chunkId;
    _blockId = blockId;
    return (_chk = _vblocks[blockId].chunkForChunkIdx(chunkId)).getChunk(vecId - _vecOffsets[_blockId]);
  }

  public ChunkAry getChunks(int cidx) {return getChunks(cidx,true);}

  public ChunkAry getChunks(int cidx, boolean cache) {
      AVec.AChunk [] chks = new AVec.AChunk[_vblocks.length];
      int k = 0;
      for(int i = 0; i < _vblocks.length; ++i) {
        chks[i] = _vblocks[i].chunkForChunkIdx(cidx);
      }
      return new ChunkAry(chks);
  }

  public boolean isSparse(int cidx){return false;}

  public SparseChunks getSparseChunks(int cidx){
    return null; // TODO
  }


  /** Begin writing into this Vec.  Immediately clears all the rollup stats
   *  ({@link #min}, {@link #max}, {@link #mean}, etc) since such values are
   *  not meaningful while the Vec is being actively modified.  Can be called
   *  repeatedly.  Per-chunk row-counts will not be changing, just row
   *  contents. */
  public void preWriting(int... colIds){
    throw H2O.unimpl();
  }

  /** Stop writing into thiposs Vec.  Rollup stats will again (lazily) be
   *  computed. */
  public Futures postWrite( Futures fs ) {
    throw H2O.unimpl();
  }

  // Make a bunch of compatible zero Vectors
  public VecAry makeCons(final int n, final double con, String[][] domains, byte[] types) {
    final AVec av = n == 1
        ?new Vec(group().addVec(),_vblocks[0]._rowLayout,domains == null?null:domains[0], types[0])
        :new VecBlock(group().addVec(),_vblocks[0]._rowLayout, n, domains,types);
    new MRTask() {
      public void setupLocal() {
        for(int i = 0; i < av.nChunks(); ++i) {
          Key k;
          final long [] espc = espc();
          if((k = av.chunkKey(i)).home()) {
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
  public VecAry makeZero() { return new VecAry(Vec.makeCon(0, null, group(), _vblocks[0]._rowLayout)); }
  public VecAry makeCon(long con) { return new VecAry(Vec.makeCon(con, null, group(), _vblocks[0]._rowLayout)); }
  public VecAry makeCon(double con) { return new VecAry(Vec.makeCon(con, null, group(), _vblocks[0]._rowLayout)); }

  /** A new vector with the same size and data layout as the current one, and
   *  initialized to zero, with the given categorical domain.
   *  @return A new vector with the same size and data layout as the current
   *  one, and initialized to zero, with the given categorical domain. */
  public VecAry makeZero(String[] domain) { return new VecAry(Vec.makeCon(0, domain, group(), _vblocks[0]._rowLayout)); }
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

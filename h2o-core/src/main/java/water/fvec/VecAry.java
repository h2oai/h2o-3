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
  AVec[] _vblocks;
  int [][] _vecIds;
  int [] _vecOffsets;

  public VecAry(){}
  public VecAry(AVec... v){this(v,null);}
  public VecAry(VecAry... vs){
    this();
    for(VecAry v:vs) addVecs(v);
  }
  public VecAry(AVec v, int []ids){
    this(new AVec[]{v}, ids == null?null:new int[][]{ids});
  }
  public VecAry(AVec [] vs, int [][] ids){
    _vblocks = vs;
    _vecIds = ids;
    _vecOffsets = new int[vs.length+1];
    int len = 0;
    for(int i = 0; i < _vblocks.length; ++i) {
      len += ids != null && ids[i] != null?ids[i].length:_vblocks[i].numCols();
      _vecOffsets[i+1] = len;
    }
  }

  public VecAry(VecAry clone) {
    throw H2O.unimpl();
  }

  public AVec getAVecRaw(int i) {return _vblocks[i];}
  public AVec getAVecForCol(int i) {throw H2O.unimpl();}

  public boolean isInt(int col) {
    throw H2O.unimpl(); // TODO
  }


  public long[] espc() {
    throw H2O.unimpl();
  }

  public String[][] domains() {
    throw H2O.unimpl(); // TODO
  }

  public byte type(int i){
    throw H2O.unimpl();
    // TODO
  }
  public byte[] types() {
    throw H2O.unimpl(); // TODO
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
    throw H2O.unimpl();
  }

  public double min(int i) {
    throw H2O.unimpl();
  }

  public double max(int i) {
    throw H2O.unimpl();
  }

  public double mean(int i) {
    throw H2O.unimpl();
  }


  public double sigma(int i) {
    return 0;
  }

  public boolean isCategorical(int i) {
    throw H2O.unimpl();
  }

  public boolean isUUID(int i) {
    throw H2O.unimpl();
  }

  public boolean isString(int i) {
    throw H2O.unimpl();
  }

  public long byteSize() {
    throw H2O.unimpl();
  }

  public String[] domain(int i) {
    throw H2O.unimpl();
  }

  public boolean isRawBytes() {
    throw H2O.unimpl();
  }

  public VecAry makeCompatible(VecAry vecAry, boolean b) {
    throw H2O.unimpl();
  }

  public boolean isHomedLocally(int lo) {
    return false;
  }

  public void close() {
    throw H2O.unimpl();
  }

  public void reload() {

  }

  public void setBad(int ecol) {
    throw H2O.unimpl();
  }

  public void setDomain(int ecol, String[] domain) {
    throw H2O.unimpl();
  }

  public VecAry adaptTo(String [] domain){
    return new VecAry(new CategoricalWrappedVec(group().addVec(),_vblocks[0]._rowLayout,domain,this));
  }

  public boolean isConst(int i) {
    throw H2O.unimpl();
  }

  public int naCnt(int i) {
    throw H2O.unimpl();
  }

  public boolean isNumeric(int i) {
    throw H2O.unimpl();
  }

  public Key<AVec>[] keys() {throw H2O.unimpl();}

  public boolean isBad(int i) {
    throw H2O.unimpl();
  }

  public boolean isBinary(int i) {
    throw H2O.unimpl();
  }

  public VecAry setVec(int id, VecAry vec) {
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
    throw H2O.unimpl();
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

  public final class VecAryWriter extends VecAryReader implements Closeable {
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


  public VecAryReader vecReader(boolean keepCache) {
    throw H2O.unimpl(); // TODO
  }

  public VecAryWriter vecWriter() {
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
  public VecAry addVecs(AVec... vs) {
    throw H2O.unimpl();
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

  public Chunk getChunk(int chunkId, int vecId){
    if(_chk != null && _chunkId == chunkId && _vecOffsets[_blockId] <= vecId && vecId < _vecOffsets[_blockId+1])
      return _chk.getChunk(vecId - _vecOffsets[_blockId]);
    if(vecId < 0 || vecId > len()) throw new ArrayIndexOutOfBoundsException(vecId);
    if(_vecIds == null) // simple vecs
      return (Chunk)_vblocks[vecId].chunkForChunkIdx(chunkId);
    int blockId = Arrays.binarySearch(_vecOffsets,vecId);
    if(blockId < 0) blockId = -blockId - 1;
    _chunkId = chunkId;
    _blockId = blockId;
    return (_chk = _vblocks[blockId].chunkForChunkIdx(chunkId)).getChunk(vecId - _vecOffsets[_blockId]);
  }

  public Chunk[] getChunks(int cidx) {return getChunks(cidx,true);}
  public Chunk[] getChunks(int cidx, boolean cache) {
      Chunk [] chks = new Chunk[len()];
      int k = 0;
      for(int i = 0; i < _vblocks.length; ++i) {
        AVec.AChunk c = _vblocks[i].chunkForChunkIdx(cidx);
        if(_vecIds == null || _vecIds[i] == null) { // take all
          for (int j = 0; j < _vblocks[i].numCols(); ++j)
            chks[k++] = c.getChunk(j);
        } else  for (int j = 0; j < _vecIds[i].length; ++j)
          chks[k++] = c.getChunk(_vecIds[i][j]);
      }
      return chks;
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
    final VecBlock vb = new VecBlock(group().addVec(),_vblocks[0]._rowLayout, n, domains,types);
    new MRTask() {
      public void setupLocal() {
        for(int i = 0; i < vb.nChunks(); ++i) {
          Key k;
          final long [] espc = espc();
          if((k = vb.chunkKey(i)).home()) {
            // int numCols, int len, int [] nzChunks, Chunk [] chunks, double sparseElem
            int len = (int) (espc[i + 1] - espc[i]);
            if (n > 1)
              DKV.put(k, new ChunkBlock(n, len , new int[0], new Chunk[0], con), _fs);
            else
              DKV.put(k, new C0DChunk(con, len),_fs);
          }
        }
      }
    }.doAllNodes();
    DKV.put(vb._key,vb);
    return new VecAry(vb);
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
        Random rng = new RandomUtils.PCGRNG(c._start,1);
        for(int i = 0; i < c._len; ++i) {
          rng.setSeed(seed+c._start+i); // Determinstic per-row
          c.set(i, rng.nextFloat());
        }
      }
    }.doAll(randVec);
    return randVec;
  }
}

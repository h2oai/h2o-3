package water.fvec;

import water.*;
import water.util.UnsafeUtils;

import java.util.Arrays;

/**
 * Created by tomas on 7/8/16.
 * Generalized Vec. Can hold multiple columns.
 */
public class VecBlock extends Keyed<VecBlock> {
  int _vecStart;
  int _vecEnd;
  int [] _removedVecs;

  String [][] _domains;

  public VecBlock(Key<VecBlock> key, int rowLayout, String[][] domains, byte[] types) {
    super(key);
    _rowLayout = rowLayout;
    _domains = domains;
    _types = types;
  }



  /** Default read/write behavior for Vecs.  File-backed Vecs are read-only. */
  boolean readable() { return true ; }
  /** Default read/write behavior for Vecs.  AppendableVecs are write-only. */
  boolean writable() { return true; }

  public String[][] domains(){return _domains;}

  public ChunkBlock getChunkBlock(int chunkId) {
    return getChunkBlock(chunkId,true);
  }
  public ChunkBlock getChunkBlock(int chunkId, boolean cache) {

    return null;
  }


  public boolean hasVec(int id) {return _removedVecs == null || Arrays.binarySearch(_removedVecs,id) < 0;}

  public int nVecs(){return _vecEnd - _vecStart - (_removedVecs == null?0:_removedVecs.length);}

  public RollupStats getRollups(int vecId) {
    if(nVecs() < vecId || vecId < 0)
      throw new ArrayIndexOutOfBoundsException();
    if(!hasVec(vecId))
      throw new NullPointerException("vec has been removed");
    return null;
  }

  // Vec internal type: one of T_BAD, T_UUID, T_STR, T_NUM, T_CAT, T_TIME
  byte [] _types;                   // Vec Type

  /** Element-start per chunk, i.e. the row layout.  Defined in the
   *  VectorGroup.  This field is dead/ignored in subclasses that are
   *  guaranteed to have fixed-sized chunks such as file-backed VecAry. */
  public int _rowLayout;
  // Carefully set in the constructor and read_impl to be pointer-equals to a
  // common copy one-per-node.  These arrays can get both *very* common
  // (one-per-Vec at least, sometimes one-per-Chunk), and very large (one per
  // Chunk, could run into the millions).
  private transient long [] _espc;

  private transient Key _rollupStatsKey;

  long chunk2StartElem( int cidx ) { return espc()[cidx]; }
  public long[] espc() { if( _espc==null ) _espc = VecBlock.ESPC.espc(this); return _espc; }

  /** The Chunk for a chunk#.  Warning: this pulls the data locally; using this
   *  call on every Chunk index on the same node will probably trigger an OOM!
   *  @return Chunk for a chunk# */
  public ChunkBlock chunkForChunkIdx(int cidx) {
    long start = chunk2StartElem(cidx); // Chunk# to chunk starting element#
    Value dvec = chunkIdx(cidx);        // Chunk# to chunk data
    ChunkBlock c = dvec.get();               // Chunk data to compression wrapper
    long cstart = c._start;             // Read once, since racily filled in
    VecBlock vb = c._vb;
    int tcidx = c._cidx;
    if( cstart == start && vb != null && tcidx == cidx)
      return c;                       // Already filled-in
    assert cstart == -1 || vb == null || tcidx == -1; // Was not filled in (everybody racily writes the same start value)
    c._vb = this;             // Fields not filled in by unpacking from Value
    c._start = start;          // Fields not filled in by unpacking from Value
    c._cidx = cidx;
    return c;
  }

  /** Get a Chunk's Value by index.  Basically the index-to-key map, plus the
   *  {@code DKV.get()}.  Warning: this pulls the data locally; using this call
   *  on every Chunk index on the same node will probably trigger an OOM!  */
  public Value chunkIdx( int cidx ) {
    Value val = DKV.get(chunkKey(cidx));
//    assert checkMissing(cidx,val) : "Missing chunk " + chunkKey(cidx);
    return val;
  }
  /** Get a Chunk Key from a chunk-index and a Vec Key, without needing the
   *  actual Vec object.  Basically the index-to-key map.
   *  @return Chunk Key from a chunk-index and Vec Key */
  public Key chunkKey(int cidx ) {
    byte [] bits = _key._kb.clone();
    bits[0] = Key.CHK;
    UnsafeUtils.set4(bits, 6, cidx); // chunk#
    return Key.make(bits);
  }

  public Key rollupStatsKey() {
    if( _rollupStatsKey==null ) _rollupStatsKey=chunkKey(-2);
    return _rollupStatsKey;
  }

  /** The Chunk for a row#.  Warning: this pulls the data locally; using this
   *  call on every Chunk index on the same node will probably trigger an OOM!
   *  @return Chunk for a row# */
  public final ChunkBlock chunkForRow(long i) { return chunkForChunkIdx(elem2ChunkIdx(i)); }

  public long length(){long [] espc = espc(); return espc[espc.length-1];}
  public int nChunks() { return espc().length-1; }

  public long byteSize() { return 0; }

  /** Convert a row# to a chunk#.  For constant-sized chunks this is a little
   *  shift-and-add math.  For variable-sized chunks this is a binary search,
   *  with a sane API (JDK has an insane API).  Overridden by subclasses that
   *  compute chunks in an alternative way, such as file-backed VecAry. */
  public int elem2ChunkIdx( long i ) {
    if( !(0 <= i && i < length()) ) throw new ArrayIndexOutOfBoundsException("0 <= "+i+" < "+length());
    long[] espc = espc();       // Preload
    int lo=0, hi = nChunks();
    while( lo < hi-1 ) {
      int mid = (hi+lo)>>>1;
      if( i < espc[mid] ) hi = mid;
      else                lo = mid;
    }
    while( espc[lo+1] == i ) lo++;
    return lo;
  }

  public Vec.VectorGroup group() {
    return null; // TODO
  }

  public void close() {
    throw H2O.unimpl(); // TODO
  }

  public void setDomains(String[][] domains) {
    /** Set the categorical/factor names.  No range-checking on the actual
     *  underlying numeric domain; user is responsible for maintaining a mapping
     *  which is coherent with the Vec contents. */
    _domains = domains;
    for(int i = 0; i < domains.length; ++i)
      if( domains[i] != null ) assert _types[i] == Vec.T_CAT;
  }
}

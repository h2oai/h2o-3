package water.fvec;

import water.*;
import water.util.UnsafeUtils;
import water.util.MathUtils;

public abstract class FileVec extends ByteVec {
  long _len;                    // File length
  final byte _be;

  /** Log-2 of Chunk size. */
  public static final int DFLT_LOG2_CHUNK_SIZE = 20/*1Meg*/+2/*4Meg*/;
  /** Default Chunk size in bytes, useful when breaking up large arrays into
   *  "bite-sized" chunks.  Bigger increases batch sizes, lowers overhead
   *  costs, lower increases fine-grained parallelism. */
  public static final int DFLT_CHUNK_SIZE = 1 << DFLT_LOG2_CHUNK_SIZE;
  public int _log2ChkSize = DFLT_LOG2_CHUNK_SIZE;
  public int _chunkSize = DFLT_CHUNK_SIZE;

  protected FileVec(Key key, long len, byte be) {
    super(key,-1/*no rowLayout*/);
    _len = len;
    _be = be;
  }

  /**
   * Chunk size must be positive, 1G or less, and a power of two.
   * Any values that aren't a power of two will be reduced to the
   * first power of two lower than the provided chunkSize.
   * <p>
   * Since, optimal chunk size is not known during FileVec instantiation,
   * setter is required to both set it, and keep it in sync with
   * _log2ChkSize.
   * </p>
   * @param chunkSize requested chunk size to be used when parsing
   * @return actual _chunkSize setting
   */
  public int setChunkSize(int chunkSize) { return setChunkSize(null, chunkSize); }
  public int setChunkSize(Frame fr, int chunkSize) {
    if (chunkSize <= 0) throw new IllegalArgumentException("Chunk sizes must be > 0.");
    if (chunkSize > (1<<30) ) throw new IllegalArgumentException("Chunk sizes must be < 1G.");
    _log2ChkSize = water.util.MathUtils.log2(chunkSize);
    _chunkSize = 1 << _log2ChkSize;

    //Now reset the chunk size on each node
    Futures fs = new Futures();
    DKV.put(_key, this, fs);
    // also update Frame to invalidate local caches
    if (fr != null ) {
      fr.reloadVecs();
      DKV.put(fr._key, fr, fs);
    }
    fs.blockForPending();

    return _chunkSize;
  }

  @Override public long length() { return _len; }
  @Override public int nChunks() { return (int)Math.max(1,_len>>_log2ChkSize); }
  @Override public boolean writable() { return false; }

  /** Size of vector data. */
  @Override public long byteSize(){return length(); }

  // Convert a row# to a chunk#.  For constant-sized chunks this is a little
  // shift-and-add math.  For variable-sized chunks this is a binary search,
  // with a sane API (JDK has an insane API).
  @Override
  public int elem2ChunkIdx(long i) {
    assert 0 <= i && i <= _len : " "+i+" < "+_len;
    int cidx = (int)(i>>_log2ChkSize);
    int nc = nChunks();
    if( i >= _len ) return nc;
    if( cidx >= nc ) cidx=nc-1; // Last chunk is larger
    assert 0 <= cidx && cidx < nc;
    return cidx;
  }
  // Convert a chunk-index into a starting row #. Constant sized chunks
  // (except for the last, which might be a little larger), and size-1 rows so
  // this is a little shift-n-add math.
  @Override long chunk2StartElem( int cidx ) { return (long)cidx << _log2ChkSize; }

  /** Convert a chunk-key to a file offset. Size 1-byte "rows", so this is a
   *  direct conversion.
   *  @return The file offset corresponding to this Chunk index */
  public static long chunkOffset ( Key ckey ) { return (long)chunkIdx(ckey)<< ((FileVec)Vec.getVecKey(ckey).get())._log2ChkSize; }
  // Reverse: convert a chunk-key into a cidx
  static int chunkIdx(Key ckey) { assert ckey._kb[0]==Key.CHK; return UnsafeUtils.get4(ckey._kb, 1 + 1 + 4); }

  // Convert a chunk# into a chunk - does lazy-chunk creation. As chunks are
  // asked-for the first time, we make the Key and an empty backing DVec.
  // Touching the DVec will force the file load.
  @Override public Value chunkIdx( int cidx ) {
    final long nchk = nChunks();
    assert 0 <= cidx && cidx < nchk;
    Key dkey = chunkKey(cidx);
    Value val1 = DKV.get(dkey);// Check for an existing one... will fetch data as needed
    if( val1 != null ) return val1; // Found an existing one?
    // Lazily create a DVec for this chunk
    int len = (int)(cidx < nchk-1 ? _chunkSize : (_len-chunk2StartElem(cidx)));
    // DVec is just the raw file data with a null-compression scheme
    Value val2 = new Value(dkey,len,null,TypeMap.C1NCHUNK,_be);
    val2.setdsk(); // It is already on disk.
    // If not-home, then block till the Key is everywhere.  Most calls here are
    // from the parser loading a text file, and the parser splits the work such
    // that most puts here are on home - so this is a simple speed optimization: 
    // do not make a Futures nor block on it on home.
    Futures fs = dkey.home() ? null : new Futures();
    // Atomically insert: fails on a race, but then return the old version
    Value val3 = DKV.DputIfMatch(dkey,val2,null,fs);
    if( !dkey.home() && fs != null ) fs.blockForPending();
    return val3 == null ? val2 : val3;
  }

  /**
   * Calculates safe and hopefully optimal chunk sizes.  Four cases
   * exist.
   * <p>
   * very small data < 256K per proc - uses default chunk size and
   * all data will be in one chunk
   * <p>
   * small data - data is partitioned into chunks that at least
   * 4 chunks per core to help keep all cores loaded
   * <p>
   * default - chunks are {@value #DFLT_CHUNK_SIZE}
   * <p>
   * large data - if the data would create more than 4M keys per
   * node, then chunk sizes larger than DFLT_CHUNK_SIZE are issued.
   * <p>
   * Too many keys can create enough overhead to blow out memory in
   * large data parsing. # keys = (parseSize / chunkSize) * numCols.
   * Key limit of 2M is a guessed "reasonable" number.
   *
   * @param totalSize - parse size in bytes (across all files to be parsed)
   * @param numCols - number of columns expected in dataset
   * @return - optimal chunk size in bytes (always a power of 2).
   */
  public static int calcOptimalChunkSize(long totalSize, int numCols) {
    long localParseSize =  totalSize / H2O.getCloudSize();
    int chunkSize = (int) (localParseSize /
            (Runtime.getRuntime().availableProcessors() * 4));

    // Super small data check - less than 64K/thread
    if (chunkSize <= (1 << 16)) {
      return DFLT_CHUNK_SIZE;
    }

    // Small data check
    chunkSize = 1 << MathUtils.log2(chunkSize); //closest power of 2
    if (chunkSize < DFLT_CHUNK_SIZE
            && (localParseSize/chunkSize)*numCols < (1 << 21)) { // ignore if col cnt is high
      return chunkSize;
    }

    // Big data check
    long tmp = (localParseSize * numCols / (1 << 21)); // ~ 2M keys per node
    if (tmp > (1 << 30)) return (1 << 30); // Max limit is 1G
    if (tmp > DFLT_CHUNK_SIZE) {
      chunkSize = 1 << MathUtils.log2((int) tmp); //closest power of 2
      return chunkSize;
    } else return DFLT_CHUNK_SIZE;
  }
}

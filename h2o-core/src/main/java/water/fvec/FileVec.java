package water.fvec;

import water.*;
import water.util.UnsafeUtils;

abstract class FileVec extends ByteVec {
  long _len;                    // File length
  final byte _be;
  public static final int CHUNK_SZ = 1 << LOG_CHK;
  

  protected FileVec(Key key, long len, byte be) {
    super(key,null);
    _len = len;
    _be = be;
  }

  @Override public long length() { return _len; }
  @Override public int nChunks() { return (int)Math.max(1,_len>>LOG_CHK); }
  @Override public boolean writable() { return false; }

  /** Size of vector data. */
  @Override public long byteSize(){return length(); }

  // Convert a row# to a chunk#.  For constant-sized chunks this is a little
  // shift-and-add math.  For variable-sized chunks this is a binary search,
  // with a sane API (JDK has an insane API).
  @Override int elem2ChunkIdx( long i ) {
    assert 0 <= i && i <= _len : " "+i+" < "+_len;
    int cidx = (int)(i>>LOG_CHK);
    int nc = nChunks();
    if( i >= _len ) return nc;
    if( cidx >= nc ) cidx=nc-1; // Last chunk is larger
    assert 0 <= cidx && cidx < nc;
    return cidx;
  }
  // Convert a chunk-index into a starting row #. Constant sized chunks
  // (except for the last, which might be a little larger), and size-1 rows so
  // this is a little shift-n-add math.
  @Override long chunk2StartElem( int cidx ) { return (long)cidx <<LOG_CHK; }

  /** Convert a chunk-key to a file offset. Size 1-byte "rows", so this is a
   *  direct conversion.
   *  @return The file offset corresponding to this Chunk index */
  public static long chunkOffset ( Key ckey ) { return (long)chunkIdx(ckey)<<LOG_CHK; }
  // Reverse: convert a chunk-key into a cidx
  static int chunkIdx(Key ckey) { assert ckey._kb[0]==Key.CHK; return UnsafeUtils.get4(ckey._kb, 1 + 1 + 4); }

  // Convert a chunk# into a chunk - does lazy-chunk creation. As chunks are
  // asked-for the first time, we make the Key and an empty backing DVec.
  // Touching the DVec will force the file load.
  @Override protected Value chunkIdx( int cidx ) {
    final long nchk = nChunks();
    assert 0 <= cidx && cidx < nchk;
    Key dkey = chunkKey(cidx);
    Value val1 = DKV.get(dkey);// Check for an existing one... will fetch data as needed
    if( val1 != null ) return val1; // Found an existing one?
    // Lazily create a DVec for this chunk
    int len = (int)(cidx < nchk-1 ? CHUNK_SZ : (_len-chunk2StartElem(cidx)));
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
}

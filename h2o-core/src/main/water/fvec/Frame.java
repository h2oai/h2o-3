package water.fvec;

import water.*;

/**
 * A collection of named Vecs.  Essentially an R-like data-frame.  Multiple
 * Frames can reference the same Vecs.  A Frame is a lightweight object, it is
 * meant to be cheaply created and discarded for data munging purposes.
 * E.g. to exclude a Vec from a computation on a Frame, create a new Frame that
 * references all the Vecs but this one.
 */
public class Frame extends Lockable {
  public String[] _names;
  private Key[] _keys;           // Keys for the vectors
  private transient Vec[] _vecs; // The Vectors (transient to avoid network traffic)
  private transient Vec _col0; // First readable vec; fast access to the VectorGroup's Chunk layout

  public Frame( Vec vecs[] ) { this(null,vecs); }
  public Frame( String names[], Vec vecs[] ) { this(null,names,vecs); }
  public Frame( Key key, String names[], Vec vecs[] ) { 
    super(key);
    assert checkCompatible(vecs);

    // Require all Vecs already be installed in the K/V store
    Key keys[] = new Key[vecs.length];
    for( int i=0; i<vecs.length; i++ ) {
      Key k = keys[i] = vecs[i]._key;
      assert DKV.get(k)!=null;
    }

    // Always require names
    if( names==null ) {
      names = new String[vecs.length];
      for( int i=0; i<vecs.length; i++ ) names[i] = "C"+(i+1);
    } 
    assert names.length == vecs.length;

    // Set final fields
    _names= names;
    _vecs = vecs;
    _keys = keys;
  }

  /** Check that the vectors are all compatible.  All Vecs have their content
   *  sharded using same number of rows per chunk.  */
  private static boolean checkCompatible( Vec vecs[] ) {
    Vec v0 = null;
    for( Vec v : vecs )
      if( v.readable() ) {v0 = v; break; }
    if( v0 == null ) return true;
    int nchunks = v0.nChunks();
    for( Vec vec : vecs ) {
      if( vec instanceof AppendableVec ) continue; // New Vectors are endlessly compatible
      if( vec.nChunks() != nchunks )
        throw new IllegalArgumentException("Vectors different numbers of chunks, "+nchunks+" and "+vec.nChunks());
    }
    // Also check each chunk has same rows
    for( int i=0; i<nchunks; i++ ) {
      long es = v0.chunk2StartElem(i);
      for( Vec vec : vecs )
        if( !(vec instanceof AppendableVec) && vec.chunk2StartElem(i) != es )
          throw new IllegalArgumentException("Vector chunks different numbers of rows, "+es+" and "+vec.chunk2StartElem(i));
    }
    // For larger Frames, verify that the layout is compatible - else we'll be
    // endlessly cache-missing the data around the cluster, pulling copies
    // local everywhere.
    if( v0.length() > 1e4 ) {
      Vec.VectorGroup grp = v0.group();
      for( Vec vec : vecs )
        assert grp.equals(vec.group()) : "Vector " + vec + " has different vector group!";
    }
    return true;
  }

  /** Returns the first readable vector. */
  public final Vec anyVec() {
    Vec c0 = _col0; // single read
    if( c0 != null ) return c0;
    for( Vec v : vecs() )
      if( v.readable() )
        return (_col0 = v);
    return null;
  }

  public final Vec[] vecs() { 
    Vec[] tvecs = _vecs; // read the content
    return tvecs == null ? (_vecs=vecs_impl()) : tvecs;
  }
  // Compute vectors for caching
  private Vec[] vecs_impl() {
    // Load all Vec headers; load them all in parallel by starting prefetches
    for( Key key : _keys ) DKV.prefetch(key);
    Vec [] vecs = new Vec[_keys.length];
    for( int i=0; i<_keys.length; i++ ) vecs[i] = DKV.get(_keys[i]).get();
    return vecs;
  }

  public Futures postWrite(Futures fs) {
    for( Vec v : _vecs ) v.postWrite(fs);
    return fs;
  }

  public int numCols() { throw H2O.unimpl(); }
  public final long byteSize() { throw H2O.unimpl(); }
}

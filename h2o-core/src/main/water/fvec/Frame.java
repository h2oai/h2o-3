package water.fvec;

import java.util.Arrays;
import water.*;
import water.util.ArrayUtils;

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
    assert checkCompatible(vecs) : "Vectors different numbers of chunks";

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

  // Deep copy of Vecs & Keys & Names (but not data!) to a new named Key.  The
  // resulting Frame does not share with the original, so the set of Vecs can
  // be freely hacked without disturbing the original Frame.
  public Frame( Frame fr ) {
    super( Key.make() );
    _names= fr._names.clone();
    _keys = fr._keys .clone();
    _vecs = fr.vecs().clone();
  }

  // Append a Vec
  public Vec add( Vec vec ) { return add("C"+(numCols()+1),vec); }
  public Vec add( String name, Vec vec ) {
    assert checkCompatible(vec);
    int ncols = _keys.length;
    _names = Arrays.copyOf(_names,ncols+1);  _names[ncols] = uniquify(name);
    _keys  = Arrays.copyOf(_keys ,ncols+1);  _keys [ncols] = vec._key;
    _vecs  = Arrays.copyOf(_vecs ,ncols+1);  _vecs [ncols] = vec;
    return vec;
  }

  private String uniquify( String name ) {
    String n = name;
    int cnt=0, again;
    do {
      again = cnt;
      for( String s : _names )
        if( n.equals(s) )
          n = name+(cnt++);
    } while( again != cnt );
    return n;
  }

  // Append a Frame
  public Frame add( Frame fr ) {
    Vec[] vecs = fr.vecs();
    for( int i=0; i<vecs.length; i++ )
      add(fr._names[i],vecs[i]);
    return this;
  }

  // Pull out a subset frame, by column name
  public Frame subframe( String[] names ) {
    Vec[] vecs = new Vec[names.length];
    for( int i=0; i<names.length; i++ ) {
      int idx = ArrayUtils.find(_names,names[i]);
      if( idx== -1 ) throw new IllegalArgumentException("Column "+names[i]+" not found");
      vecs[i] = vecs()[idx];
    }
    return new Frame( names, vecs );
  }


  public boolean checkCompatible( Frame fr ) {
    return checkCompatible( new Vec[]{anyVec(),fr.anyVec()} );
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
        return false;
    }
    // Also check each chunk has same rows
    for( int i=0; i<nchunks+1; i++ ) {
      long es = v0.chunk2StartElem(i);
      for( Vec vec : vecs )
        if( !(vec instanceof AppendableVec) && vec.chunk2StartElem(i) != es )
          return false;
    }
    // For larger Frames, verify that the layout is compatible - else we'll be
    // endlessly cache-missing the data around the cluster, pulling copies
    // local everywhere.
    if( v0.length() > 1e4 ) {
      Vec.VectorGroup grp = v0.group();
      for( Vec vec : vecs )
        if( !grp.equals(vec.group()) ) return false;
    }
    return true;
  }

  private boolean checkCompatible( Vec vec ) {
    if( vec instanceof AppendableVec ) return true; // New Vectors are endlessly compatible
    Vec v0 = anyVec();
    int nchunks = v0.nChunks();
    if( vec.nChunks() != nchunks )
      throw new IllegalArgumentException("Vectors different numbers of chunks, "+nchunks+" and "+vec.nChunks());
    // Also check each chunk has same rows
    for( int i=0; i<nchunks+1; i++ ) {
      long es = v0.chunk2StartElem(i), xs = vec.chunk2StartElem(i);
      if( xs != es ) throw new IllegalArgumentException("Vector chunks different numbers of rows, "+es+" and "+xs);
    }
    // For larger Frames, verify that the layout is compatible - else we'll be
    // endlessly cache-missing the data around the cluster, pulling copies
    // local everywhere.
    if( v0.length() > 1e4 )
      assert v0.group().equals(vec.group()) : "Vector " + vec + " has different vector group!";
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

  /** All the domains for enum columns; null for non-enum columns.  */
  public String[][] domains() {
    String ds[][] = new String[vecs().length][];
    for( int i=0; i<vecs().length; i++ )
      ds[i] = vecs()[i].domain();
    return ds;
  }

  public String[] names() { return _names; }

  // For MRTask: allow rollups for all written-into vecs
  public Futures postWrite(Futures fs) {
    for( Vec v : vecs() ) v.postWrite(fs);
    return fs;
  }

  /** Actually remove/delete all Vecs from memory, not just from the Frame. */
  @Override public Futures remove(Futures fs) {
    for( Vec v : vecs() ) v.remove(fs);
    _names = new String[0];
    _vecs = new Vec[0];
    _keys = new Key[0];
    super.remove(fs);
    return fs;
  }
  @Override public String errStr() { return "Dataset"; }

  public int  numCols() { return _keys.length; }
  public long numRows() { return anyVec().length(); }
  public final long byteSize() { throw H2O.unimpl(); }
}

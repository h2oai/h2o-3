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

    // Require all Vecs already be installed in the K/V store
    for( int i=0; i<vecs.length; i++ ) DKV.prefetch(vecs[i]._key);
    for( int i=0; i<vecs.length; i++ ) assert DKV.get(vecs[i]._key) != null;

    // Always require names
    if( names==null ) {
      names = new String[vecs.length];
      for( int i=0; i<vecs.length; i++ ) names[i] = "C"+(i+1);
    } 
    assert names.length == vecs.length;

    _names = new String[0];
    _keys  = new Key   [0];
    _vecs  = new Vec   [0];
    add(names,vecs);
  }
  // Add a bunch of vecs
  private void add( String[] names, Vec[] vecs ) {
    for( int i=0; i<vecs.length; i++ )
      add(names[i],vecs[i]);
  }
  // Append a default-named Vec
  public Vec add( Vec vec ) { return add("C"+(numCols()+1),vec); }
  // Append a named Vec
  public Vec add( String name, Vec vec ) {
    checkCompatible(name=uniquify(name),vec);  // Throw IAE is mismatch
    int ncols = _keys.length;
    _names = Arrays.copyOf(_names,ncols+1);  _names[ncols] = name;
    _keys  = Arrays.copyOf(_keys ,ncols+1);  _keys [ncols] = vec._key;
    _vecs  = Arrays.copyOf(_vecs ,ncols+1);  _vecs [ncols] = vec;
    return vec;
  }
  // Append a Frame
  public Frame add( Frame fr ) { add(fr._names,fr.vecs()); return this; }

  /** Check that the vectors are all compatible.  All Vecs have their content
   *  sharded using same number of rows per chunk, and all names are unique.
   *  Throw an IAE if something does not match.  */
  private void checkCompatible( String name, Vec vec ) {
    if( ArrayUtils.find(_names,name) != -1 ) throw new IllegalArgumentException("Duplicate name '"+name+"' in Frame");
    if( vec instanceof AppendableVec ) return; // New Vectors are endlessly compatible
    Vec v0 = anyVec();
    if( v0 == null ) return; // No fixed-size Vecs in the Frame
    // Vector group has to be the same, or else the layout has to be the same,
    // or else the total length has to be small.
    if( !v0.checkCompatible(vec) )
      throw new IllegalArgumentException("Vector groups differs - adding vec '"+name+"' into the frame " + Arrays.toString(_names));
    if( v0.length() != vec.length() )
      throw new IllegalArgumentException("Vector lengths differ - adding vec '"+name+"' into the frame " + Arrays.toString(_names));
  }

  // Used by tests to "go slow" when comparing mis-aligned Frames
  public boolean checkCompatible( Frame fr ) {
    if( numCols() != fr.numCols() ) return false;
    if( numRows() != fr.numRows() ) return false;
    for( int i=0; i<vecs().length; i++ )
      if( !vecs()[i].checkCompatible(fr.vecs()[i]) )
        return false;
    return true;
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

  // Deep copy of Vecs & Keys & Names (but not data!) to a new named Key.  The
  // resulting Frame does not share with the original, so the set of Vecs can
  // be freely hacked without disturbing the original Frame.
  public Frame( Frame fr ) {
    super( Key.make() );
    _names= fr._names.clone();
    _keys = fr._keys .clone();
    _vecs = fr.vecs().clone();
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

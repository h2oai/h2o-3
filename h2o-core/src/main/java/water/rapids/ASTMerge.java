package water.rapids;

import water.*;
import water.fvec.*;
import water.nbhm.*;
import java.util.Arrays;


/** plyr's merge: Join by any other name.
 *  Sample AST: (merge $leftFrame $rightFrame allLeftFlag allRightFlag)
 *
 *  Joins two frames; all columns with the same names will be the join key.  If
 *  you want to join on a subset of identical names, rename the columns first
 *  (otherwise the same column name would appear twice in the result).
 *
 *  If allLeftFlag is true, all rows in the leftFrame will be included, even if
 *  there is no matching row in the rightFrame, and vice-versa for
 *  allRightFlag.  Missing data will appear as NAs.  Both flags can be true.
 */
public class ASTMerge extends ASTOp {
  static final String VARS[] = new String[]{ "ary", "leftary", "rightary", "allleft", "allright"};

  boolean _allLeft, _allRite;
  public ASTMerge( ) { super(VARS); }
  @Override String opStr(){ return "merge";}
  @Override ASTOp make() {return new ASTMerge();}

  @Override ASTMerge parse_impl(Exec E) {
    // get the frames to work with
    AST left = E.parse();  if (left instanceof ASTId) left = Env.staticLookup((ASTId)left);
    AST rite = E.parse();  if (rite instanceof ASTId) rite = Env.staticLookup((ASTId)rite);

    AST a = E.skipWS().parse();
    if( a instanceof ASTId ) a = E._env.lookup((ASTId)a);
    try {
      _allLeft = ((ASTNum) a).dbl() == 1;
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Argument `allLeft` expected to be a boolean.");
    }

    a = E.skipWS().parse();
    if( a instanceof ASTId ) a = E._env.lookup((ASTId)a);
    try {
      _allRite = ((ASTNum) a).dbl() == 1;
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Argument `allRite` expected to be a boolean.");
    }
    // Finish the rest
    ASTMerge res = (ASTMerge) clone();
    res._asts = new AST[]{left,rite};
    return res;
  }

  @Override void exec(Env e, AST arg1, AST[] args) {
    throw H2O.fail();
  }

  @Override void apply(Env env) {
    Frame l = env.popAry();
    System.out.println(l);
    Frame r = env.popAry();
    System.out.println(r);
    System.out.println(_allLeft+" "+_allRite);

    // Look for the set of columns in common; resort left & right to make the
    // leading prefix of column names match.  Bail out if we find any weird
    // column types.
    int ncols=0;                // Number of columns in common
    for( int i=0; i<l._names.length; i++ ) {
      int idx = r.find(l._names[i]);
      if( idx != -1 ) {
        l.swap(i  ,ncols);
        r.swap(idx,ncols);
        Vec lv = l.vecs()[ncols];
        Vec rv = r.vecs()[ncols];
        if( lv.get_type() != rv.get_type() )
          throw new IllegalArgumentException("Merging columns must be the same type, column "+l._names[ncols]+
                                             " found types "+lv.get_type_str()+" and "+rv.get_type_str());
        if( lv.isString() )
          throw new IllegalArgumentException("Cannot merge Strings; flip toEnum first");
        ncols++;
      }
    }
    if( ncols == 0 ) 
      throw new IllegalArgumentException("Frames must have at least one column in common to merge them");

    // Pick the frame to replicate & hash; smallest bytesize of the non-key
    // columns.  Hashed dataframe is completely replicated per-node
    long lsize = 0, rsize = 0;
    for( int i=ncols; i<l.numCols(); i++ ) lsize += l.vecs()[i].byteSize();
    for( int i=ncols; i<r.numCols(); i++ ) rsize += r.vecs()[i].byteSize();
    Frame small = lsize < rsize ? l : r;
    Frame large = lsize < rsize ? r : l;

    // Build enum mappings, to rapidly convert enums from the larger
    // distributed set to the smaller hashed & replicated set.
    int[][] enum_maps = new int[ncols][];
    int[][]   id_maps = new int[ncols][];
    for( int i=0; i<ncols; i++ ) {
      Vec lv = large.vecs()[i];
      if( lv.isEnum() ) {
        EnumWrappedVec ewv = new EnumWrappedVec(lv.domain(),small.vecs()[i].domain());
        int[] ids = enum_maps[i] = ewv.enum_map();
        DKV.remove(ewv._key);
        // Build an Identity map for the smaller hash
        id_maps[i] = new int[ids.length];
        for( int j=0; j<ids.length; j++ )  id_maps[i][j] = j;
      }
    }

    // MergeSet is from local (non-replicated) chunks/row to other-chunks/row.
    // Row object in table has e.g. chunks and a row number; passed-in Row
    // object can also have chunks & a row number.  Hash based on contents of
    // chunks.  Returns matched Row object (which has replicated chunk ptrs & row).
    Key uniq = new MergeSet(ncols,id_maps,small).doAllNodes()._uniq;

    // run a global parallel work: lookup non-hashed rows in hashSet; find
    // matching row; append matching column data
    String[]   names  = Arrays.copyOfRange(small._names   ,ncols,small._names.length-ncols+1);
    String[][] domains= Arrays.copyOfRange(small.domains(),ncols,small._names.length-ncols+1);
    Frame res = new DoJoin(ncols,uniq,enum_maps,_allLeft).doAll(small.numCols()-ncols,large).outputFrame(names,domains);
    Frame res2 = large.add(res);
    env.push(new ValFrame(res2));
  }

  // One Row object per row of the smaller dataset, so kept as small as
  // possible.  The _chks[] array is shared across many Rows.
  private static class Row {
    public final Chunk _chks[]; // Chunks
    public int[][] _enum_maps;
    public int _row;    // Row in chunk
    public int _hash;
    Row( Chunk chks[] ) { _chks = chks; }
    Row fill( int row, int ncols, int[][] enum_maps ) {
      _row = row; 
      _enum_maps = enum_maps;
      long hash = 0;
      for( int i=0; i<ncols; i++ )
        hash += enum_maps[i]==null ? Double.doubleToLongBits(_chks[i].atd(row)) : enum_maps[i][(int)(_chks[i].at8(row))];
      _hash = (int)(hash^(hash>>32));
      return this;
    }
    @Override public int hashCode() { return _hash; }
    @Override public boolean equals( Object o ) {
      assert o instanceof Row;
      Row r = (Row)o;
      if( _hash != r._hash ) return false;
      if( _chks == r._chks && _row == r._row ) return true;
      // Now must check field contents
      int len = _enum_maps.length;
      for( int i=0; i<len; i++ ) {
        if( _enum_maps[i] == null ) {
          if( _chks[i].atd(_row) != r._chks[i].atd(_row) ) return false;
        } else {
          if( _enum_maps[i][(int) _chks[i].at8(_row)] != r._enum_maps[i][(int) r._chks[i].at8(r._row)] ) return false;
        }
      }
      return true;
    }
  }

  // Build a HashSet of one entire Frame, where the Key is the contents of the
  // first few columns.  One entry-per-row.
  private static class MergeSet extends MRTask<MergeSet> {
    // All active Merges have a per-Node hashset of one of the datasets
    static NonBlockingHashMap<Key,MergeSet> MERGE_SETS = new NonBlockingHashMap<>();
    final Key _uniq;      // Key to allow sharing of this MergeSet on each Node
    final int _ncols;     // Number of leading columns for the Hash Key
    final int[][] _id_maps;
    final Frame _fr;      // Frame to hash-all-rows locally per-node
    transient NonBlockingHashSet<Row> _rows;

    MergeSet( int ncols, int[][] id_maps, Frame fr ) { 
      _uniq=Key.make();  _ncols = ncols;  _id_maps = id_maps; _fr = fr; 
    }
    // Per-node, hash the entire _fr dataset
    @Override public void setupLocal() {
      MERGE_SETS.put(_uniq,this);
      _rows = new NonBlockingHashSet<>();
      new MakeHash(this).doAll(_fr,true/*run locally*/);
    }

    // Executed locally only, build a local HashSet over the entire given dataset
    private static class MakeHash extends MRTask<MakeHash> {
      transient final MergeSet _ms;
      MakeHash( MergeSet ms ) { _ms = ms; }
      @Override public void map( Chunk chks[] ) {
        int len = chks[0]._len;
        for( int i=0; i<len; i++ ) {
          boolean added = _ms._rows.add(new Row(chks).fill(i,_ms._ncols,_ms._id_maps));
          if( !added ) throw H2O.unimpl(); // dup handling?  Need to gather absolute rows in Row
        }
      }
    }
  }

  // Build the join-set by iterating over all the local Chunks of the larger
  // dataset, doing a hash-lookup on the smaller replicated dataset, and adding
  // in the matching columns.
  private static class DoJoin extends MRTask<DoJoin> {
    private final int _ncols;     // Number of merge columns
    private final Key _uniq;      // Which mergeset being merged
    private final int[][] _enum_maps; // Mapping enum domains
    private final boolean _allLeft;
    DoJoin( int ncols, Key uniq, int[][] enum_maps, boolean allLeft ) {
      _ncols = ncols; _uniq = uniq; _enum_maps = enum_maps;_allLeft = allLeft;
    }
    @Override public void map( Chunk chks[], NewChunk nchks[] ) {
      // Shared common hash map
      NonBlockingHashSet<Row> rows = MergeSet.MERGE_SETS.get(_uniq)._rows;
      int len = chks[0]._len;
      Row row = new Row(chks);  // Recycled Row object on the bigger dataset
      for( int i=0; i<len; i++ ) {
        Row smaller = rows.get(row.fill(i,_ncols,_enum_maps));
        if( smaller == null ) { // Smaller is missing
          if( _allLeft )        // But need all of larger, so force a NA row
            for( NewChunk nc : nchks ) nc.addNA();
          else
            throw H2O.unimpl(); // Need to remove larger row
        } else {
          // Copy fields from matching smaller set into larger set
          assert smaller._chks.length == _ncols + nchks.length;
          for( int c = 0; c < nchks.length; c++ )
            nchks[c].addNum(smaller._chks[_ncols + c].atd(smaller._row));
        }
      }
    }
    // Cleanup after last pass
    @Override public void closeLocal() { MergeSet.MERGE_SETS.remove(_uniq);  }
  }

}

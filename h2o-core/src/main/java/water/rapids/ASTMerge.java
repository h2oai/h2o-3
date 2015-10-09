package water.rapids;

import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.IcedHashMap;
import java.util.Arrays;


/** plyr's merge: Join by any other name.
 *  Sample AST: (merge $leftFrame $rightFrame allLeftFlag allRightFlag)
 *
 *  Joins two frames; all columns with the same names will be the join key.  If
 *  you want to join on a subset of identical names, rename the columns first
 *  (otherwise the same column name would appear twice in the result).
 *
 *  If the client side wants to allow named columns to be merged, the client
 *  side is reponsible for renaming columns as needed to bring the names into
 *  alignment as above.  This can be as simple as renaming the RHS to match the
 *  LHS column names.  Duplicate columns NOT part of the merge are still not
 *  allowed - because the resulting Frame will end up with duplicate column
 *  names which blows a Frame invariant (uniqueness of column names).
 *
 *  If allLeftFlag is true, all rows in the leftFrame will be included, even if
 *  there is no matching row in the rightFrame, and vice-versa for
 *  allRightFlag.  Missing data will appear as NAs.  Both flags can be true.
 */
public class ASTMerge extends ASTPrim {
  @Override public String[] args() { return new String[]{"left","rite", "all_left", "all_rite"}; }
  @Override public String str(){ return "merge";}
  @Override int nargs() { return 1+4; } // (merge left rite all.left all.rite)

  // Size cutoff before switching between a hashed-join vs a sorting join.
  // Hash tables beyond this count are assumed to be inefficient, and we're
  // better served by sorting all the join columns and doing a global
  // merge-join.
  static final int MAX_HASH_SIZE = 100000000;

  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame l = stk.track(asts[1].exec(env)).getFrame();
    Frame r = stk.track(asts[2].exec(env)).getFrame();
    boolean allLeft = asts[3].exec(env).getNum() == 1;
    boolean allRite = asts[4].exec(env).getNum() == 1;

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
          throw new IllegalArgumentException("Cannot merge Strings; flip toCategoricalVec first");
        if( lv.isNumeric() && !lv.isInt())  
          throw new IllegalArgumentException("Equality tests on doubles rarely work, please round to integers only before merging");
        ncols++;
      }
    }
    if( ncols == 0 ) 
      throw new IllegalArgumentException("Frames must have at least one column in common to merge them");

    // Pick the frame to replicate & hash.  If one set is "all" and the other
    // is not, the "all" set must be walked, so the "other" is hashed.  If both
    // or neither are "all", then pick the smallest bytesize of the non-key
    // columns.  The hashed dataframe is completely replicated per-node
    boolean walkLeft;
    if( allLeft == allRite ) {
      long lsize = 0, rsize = 0;
      for( int i=ncols; i<l.numCols(); i++ ) lsize += l.vecs()[i].byteSize();
      for( int i=ncols; i<r.numCols(); i++ ) rsize += r.vecs()[i].byteSize();
      walkLeft = lsize > rsize;
    } else {
      walkLeft = allLeft;
    }
    Frame walked = walkLeft ? l : r;
    Frame hashed = walkLeft ? r : l;
    if( !walkLeft ) { boolean tmp = allLeft;  allLeft = allRite;  allRite = tmp; }

    // Build categorical mappings, to rapidly convert categoricals from the
    // distributed set to the hashed & replicated set.
    int[][] id_maps = new int[ncols][];
    for( int i=0; i<ncols; i++ ) {
      Vec lv = walked.vecs()[i];
      if( lv.isCategorical() )
        id_maps[i] = CategoricalWrappedVec.computeMap(hashed.vecs()[i].domain(),lv.domain());
    }

    // Build the hashed version of the hashed frame.  Hash and equality are
    // based on the known-integer key columns.  Duplicates are either ignored
    // (!allRite) or accumulated, and can force replication of the walked set.
    //
    // Count size of this hash table as-we-go.  Bail out if the size exceeds
    // a known threshold, and switch a sorting join instead of a hashed join.
    final MergeSet ms = new MergeSet(ncols,id_maps,allRite).doAll(hashed);
    final Key uniq = ms._uniq;
    IcedHashMap<Row,String> rows = MergeSet.MERGE_SETS.get(uniq)._rows;
    new MRTask() { @Override public void setupLocal() { MergeSet.MERGE_SETS.remove(uniq);  } }.doAllNodes();
    if( rows == null )          // Blew out hash size; switch to a sorting join
      return sortingMerge(walked,hashed,allLeft,allRite,ncols,id_maps);

    // All of the walked set, and no dup handling on the right - which means no
    // need to replicate rows of the walked dataset.  Simple 1-pass over the
    // walked set adding in columns (or NAs) from the right.
    if( allLeft && !(allRite && ms._dup) ) {
      // The lifetime of the distributed dataset is independent of the original
      // dataset, so it needs to be a deep copy.
      // TODO: COW Optimization
      walked = walked.deepCopy(null);

      // run a global parallel work: lookup non-hashed rows in hashSet; find
      // matching row; append matching column data
      String[]   names  = Arrays.copyOfRange(hashed._names,   ncols,hashed._names   .length);
      String[][] domains= Arrays.copyOfRange(hashed.domains(),ncols,hashed.domains().length);
      byte[] types = Arrays.copyOfRange(hashed.types(),ncols,hashed.numCols());
      Frame res = new AllLeftNoDupe(ncols,rows,hashed,allRite).doAll(types,walked).outputFrame(names,domains);
      return new ValFrame(walked.add(res));
    }

    // Can be full or partial on the left, but won't nessecarily do all of the
    // right.  Dups on right are OK (left will be replicated or dropped as needed).
    if( !allRite ) {
      String[] names = Arrays.copyOf(walked.names(),walked.numCols() + hashed.numCols()-ncols);
      System.arraycopy(hashed.names(),ncols,names,walked.numCols(),hashed.numCols()-ncols);
      String[][] domains = Arrays.copyOf(walked.domains(),walked.numCols() + hashed.numCols()-ncols);
      System.arraycopy(hashed.domains(),ncols,domains,walked.numCols(),hashed.numCols()-ncols);
      byte[] types = walked.types();
      types = Arrays.copyOf(types,types.length+hashed.numCols()-ncols);
      System.arraycopy(hashed.types(),ncols,types,walked.numCols(),hashed.numCols()-ncols);
      return new ValFrame(new AllRiteWithDupJoin(ncols,rows,hashed,allLeft).doAll(types,walked).outputFrame(names,domains));
    } 

    throw H2O.unimpl();
  }

  /** Use a sorting merge/join, probably because the hash table size exceeded
   *  MAX_HASH_SIZE; i.e. the number of unique keys in the hashed Frame exceeds
   *  MAX_HASH_SIZE.  Join is done on the first ncol columns in both frames,
   *  which are already known to be not-null and have matching names and types.
   *  The walked and hashed frames are sorted according to allLeft; if allRite
   *  is set then allLeft will also be set (but not vice-versa).
   *
   *  @param walked is the LHS frame; not-null.
   *  @param hashed is the RHS frame; not-null.
   *  @param allLeft all rows in the LHS frame will appear in the result frame.
   *  @param allRite all rows in the RHS frame will appear in the result frame.
   *  @param ncols is the number of columns to join on, and these are ordered
   *  as the first ncols of both the left and right frames.  
   *  @param id_maps if not-null denote simple integer mappings from one
   *  categorical column to another; the width is ncols
   */
  private ValFrame sortingMerge( Frame walked, Frame hashed, boolean allLeft, boolean allRite, int ncols, int[][] id_maps) {
    throw H2O.unimpl();
  }

  // One Row object per row of the hashed dataset, so kept as small as
  // possible.
  private static class Row extends Iced {
    final long[] _keys;   // Key: first ncols of longs
    int _hash;            // Hash of keys; not final as Row objects are reused
    long _row;            // Row in Vec; the payload is vecs[].atd(_row)
    long[] _dups;         // dup rows stored here (includes _row); updated atomically.
    int _dupIdx;          // pointer into _dups array; updated atomically
    Row( int ncols ) { _keys = new long[ncols]; }
    Row fill( final Chunk[] chks, final int[][] cat_maps, final int row ) {
      // Precompute hash: columns are integer only (checked before we started
      // here).  NAs count as a zero for hashing.
      long l,hash = 0;
      for( int i=0; i<_keys.length; i++ ) {
        if( chks[i].isNA(row) ) l = 0;
        else {
          l = chks[i].at8(row);
          l = (cat_maps == null || cat_maps[i]==null) ? l : cat_maps[i][(int)l];
          hash += l;
        }
        _keys[i] = l;
      }
      _hash = (int)(hash^(hash>>32));
      _row = chks[0].start()+row; // Payload: actual absolute row number
      return this;
    }
    @Override public int hashCode() { return _hash; }
    @Override public boolean equals( Object o ) {
      if( !(o instanceof Row) ) return false;
      Row r = (Row)o;
      return _hash == r._hash && Arrays.equals(_keys,r._keys);
    }

    private void atomicAddDup(long row) {
      synchronized (this) {
        if( _dups==null ) {
          _dups = new long[]{_row,row};
          _dupIdx = 2;
        } else if( _dupIdx==_dups.length )
          _dups = Arrays.copyOf(_dups, _dupIdx>>1);
        _dups[_dupIdx++]=row;
      }
    }
  }

  // Build a HashSet of one entire Frame, where the Key is the contents of the
  // first few columns.  One entry-per-row.
  private static class MergeSet extends MRTask<MergeSet> {
    // All active Merges have a per-Node hashset of one of the datasets.  If
    // this is missing, it means the HashMap exceeded the size bounds and the
    // whole MergeSet is being aborted (gracefully) - and the Merge is
    // switching to a sorting merge instead of a hashed merge.
    static IcedHashMap<Key,MergeSet> MERGE_SETS = new IcedHashMap<>();
    final Key _uniq;      // Key to allow sharing of this MergeSet on each Node
    final int _ncols;     // Number of leading columns for the Hash Key
    final int[][] _id_maps; // Rapid mapping between matching enums
    final boolean _allRite; // Collect all rows with the same matching Key, or just the first
    boolean _dup;           // Dups are present at all
    IcedHashMap<Row,String> _rows;

    MergeSet( int ncols, int[][] id_maps, boolean allRite ) { 
      _uniq=Key.make();  _ncols = ncols;  _id_maps = id_maps;  _allRite = allRite;
    }
    // Per-node, make the empty hashset for later reduction
    @Override public void setupLocal() {
      _rows = new IcedHashMap<>();
      MERGE_SETS.put(_uniq,this);
    }

    @Override public void map( Chunk chks[] ) {
      final IcedHashMap<Row,String> rows = MERGE_SETS.get(_uniq)._rows; // Shared per-node HashMap
      if( rows == null ) return; // Missing: Aborted due to exceeding size
      final int len = chks[0]._len;
      Row row = new Row(_ncols);
      for( int i=0; i<len; i++ )                    // For all rows
        if( add(rows,row.fill(chks,_id_maps,i)) ) { // Fill & attempt add row
          if( rows.size() > MAX_HASH_SIZE ) { abort(); return; }
          row = new Row(_ncols); // If added, need a new row to fill
        }
    }
    private boolean add( IcedHashMap<Row,String> rows, Row row ) {
      if( rows.putIfAbsent(row,"")==null )
        return true;            // Added!
      // dup handling: keys are identical
      if( _allRite ) {          // Collect the dups?
        _dup = true;            // MergeSet has dups.
        rows.getk(row).atomicAddDup(row._row);
      }
      return false;
    }
    private void abort( ) { MERGE_SETS.get(_uniq)._rows = _rows = null; }
    @Override public void reduce( MergeSet ms ) {
      final IcedHashMap<Row,String> rows = _rows; // Shared per-node hashset
      if( rows == ms._rows ) return;
      if( rows == null || ms._rows == null ) { abort(); return; } // Missing: aborted due to size
      for( Row row : ms._rows.keySet() ) 
        add(rows,row);          // Merge RHS into LHS, collecting dups as we go
    }
  }

  private static abstract class JoinTask extends MRTask<JoinTask> {
    protected final IcedHashMap<Row,String> _rows;
    protected final int _ncols;     // Number of merge columns
    protected final Frame _hashed;
    protected final boolean _allLeft, _allRite;
    JoinTask( int ncols, IcedHashMap<Row,String> rows, Frame hashed, boolean allLeft, boolean allRite ) {
      _rows = rows; _ncols = ncols; _hashed = hashed; _allLeft = allLeft; _allRite = allRite;
    }
    protected static void addElem(NewChunk nc, Chunk c, int row) {
      if( c.isNA(row) )                 nc.addNA();
      else if( c instanceof CStrChunk ) nc.addStr(c,row);
      else if( c instanceof C16Chunk )  nc.addUUID(c,row);
      else if( c.hasFloat() )           nc.addNum(c.atd(row));
      else                              nc.addNum(c.at8(row),0);
    }
    protected static void addElem(NewChunk nc, Vec v, long absRow, BufferedString bStr) {
      switch( v.get_type() ) {
      case Vec.T_NUM : nc.addNum(v.at(absRow)); break;
      case Vec.T_CAT :
      case Vec.T_TIME: if( v.isNA(absRow) ) nc.addNA(); else nc.addNum(v.at8(absRow)); break;
      case Vec.T_STR : nc.addStr(v.atStr(bStr, absRow)); break;
      default: throw H2O.unimpl();
      }
    }
  }

  // Build the join-set by iterating over all the local Chunks of the walked
  // dataset, doing a hash-lookup on the hashed replicated dataset, and adding
  // in the matching columns.
  private static class AllLeftNoDupe extends JoinTask {
    AllLeftNoDupe(int ncols, IcedHashMap<Row,String> rows, Frame hashed, boolean allRite) {
      super(ncols, rows, hashed, true, allRite);
    }

    @Override public void map( Chunk chks[], NewChunk nchks[] ) {
      // Shared common hash map
      final IcedHashMap<Row,String> rows = _rows;
      Vec[] vecs = _hashed.vecs(); // Data source from hashed set
      assert vecs.length == _ncols + nchks.length;
      Row row = new Row(_ncols);  // Recycled Row object on the bigger dataset
      BufferedString bStr = new BufferedString(); // Recycled BufferedString
      int len = chks[0]._len;
      for( int i=0; i<len; i++ ) {
        Row hashed = rows.getk(row.fill(chks,null,i));
        if( hashed == null ) {  // Hashed is missing
          for( NewChunk nc : nchks ) nc.addNA(); // All Left: keep row, use missing data
        } else {
          // Copy fields from matching hashed set into walked set
          final long absrow = hashed._row;
          for( int c = 0; c < nchks.length; c++ )
            addElem(nchks[c], vecs[_ncols+c],absrow,bStr);
        }
      }
    }
  }

  // Build the join-set by iterating over all the local Chunks of the walked
  // dataset, doing a hash-lookup on the hashed replicated dataset, and adding
  // in BOTH the walked and the matching columns.
  private static class AllRiteWithDupJoin extends JoinTask {
    AllRiteWithDupJoin(int ncols, IcedHashMap<Row,String> rows, Frame hashed, boolean allLeft) {
      super(ncols, rows, hashed, allLeft, true);
    }

    @Override public void map(Chunk[] chks, NewChunk[] nchks) {
      // Shared common hash map
      final IcedHashMap<Row,String> rows = _rows;
      Vec[] vecs = _hashed.vecs(); // Data source from hashed set
      assert vecs.length == _ncols + nchks.length;
      Row row = new Row(_ncols);   // Recycled Row object on the bigger dataset
      BufferedString bStr = new BufferedString(); // Recycled BufferedString
      int len = chks[0]._len;
      for( int i=0; i<len; i++ ) {
        Row hashed = rows.getk(row.fill(chks, null, i));
        if( hashed == null ) {    // no rows, fill in chks, and pad NAs as needed...
          if( _allLeft ) {        // pad NAs to the right...
            int c=0;
            for(; c< chks.length;++c) addElem(nchks[c],chks[c],i);
            for(; c<nchks.length;++c) nchks[c].addNA();
          } // else no hashed and no _allLeft... skip (row is dropped)
        } else {
          if( hashed._dups!=null ) for(long absrow : hashed._dups ) addRow(nchks,chks,vecs,i,  absrow   ,bStr);
          else                                                      addRow(nchks,chks,vecs,i,hashed._row,bStr);
        }
      }
    }
    void addRow(NewChunk[] nchks, Chunk[] chks, Vec[] vecs, int relRow, long absRow, BufferedString bStr) {
      int c=0;
      for( ;c< chks.length;++c) addElem(nchks[c],chks[c],relRow);
      for( ;c<nchks.length;++c) addElem(nchks[c],vecs[c - (chks.length + _ncols)],absRow,bStr);
    }
  }
}

package water.rapids;

import water.*;
import water.fvec.*;
import water.parser.ValueString;
import water.util.IcedHashMap;
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
public class ASTMerge extends ASTPrim {
  @Override public String[] args() { return new String[]{"left","rite", "all_left", "all_rite"}; }
  @Override public String str(){ return "merge";}
  @Override int nargs() { return 1+4; } // (merge left rite all.left all.rite)

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
          throw new IllegalArgumentException("Cannot merge Strings; flip toEnum first");
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

    // Build enum mappings, to rapidly convert enums from the distributed set
    // to the hashed & replicated set.
    int[][]   id_maps = new int[ncols][];
    for( int i=0; i<ncols; i++ ) {
      Vec lv = walked.vecs()[i];
      if( lv.isEnum() ) {
        EnumWrappedVec ewv = new EnumWrappedVec(lv.domain(),hashed.vecs()[i].domain());
        int[] ids = ewv.enum_map();
        DKV.remove(ewv._key);
        // Build an Identity map for the hashed set
        id_maps[i] = new int[ids.length];
        for( int j=0; j<ids.length; j++ )  id_maps[i][j] = j;
      }
    }

    // Build the hashed version of the hashed frame.  Hash and equality are
    // based on the known-integer key columns.  Duplicates are either ignored
    // (!allRite) or accumulated, and can force replication of the walked set.
    final MergeSet ms = new MergeSet(ncols,id_maps,allRite).doAll(hashed);
    final Key uniq = ms._uniq;
    IcedHashMap<Row,String> rows = MergeSet.MERGE_SETS.get(uniq)._rows;
    new MRTask() { @Override public void setupLocal() { MergeSet.MERGE_SETS.remove(uniq);  } }.doAllNodes();


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
      Frame res = new AllLeftNoDupe(ncols,rows,hashed,allRite).doAll(hashed.numCols()-ncols,walked).outputFrame(names,domains);
      return new ValFrame(walked.add(res));
    }

    // Can be full or partial on the left, but won't nessecarily do all of the
    // right.  Dups on right are OK (left will be replicated or dropped as needed).
    if( !allRite ) {
      String[] names = Arrays.copyOf(walked.names(),walked.numCols() + hashed.numCols()-ncols);
      System.arraycopy(hashed.names(),ncols,names,walked.numCols(),hashed.numCols()-ncols);
      String[][] domains = Arrays.copyOf(walked.domains(),walked.numCols() + hashed.numCols()-ncols);
      System.arraycopy(hashed.domains(),ncols,domains,walked.numCols(),hashed.numCols()-ncols);
      return new ValFrame(new AllRiteWithDupJoin(ncols,rows,hashed,allLeft).doAll(walked.numCols()+hashed.numCols()-ncols,walked).outputFrame(names,domains));
    } 

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
    Row fill( final Chunk[] chks, final int[][] enum_maps, final int row ) {
      // Precompute hash: columns are integer only (checked before we started
      // here).  NAs count as a zero for hashing.
      long l,hash = 0;
      for( int i=0; i<_keys.length; i++ ) {
        if( chks[i].isNA(row) ) l = 0;
        else {
          l = chks[i].at8(row);
          hash += (enum_maps == null || enum_maps[i]==null) ? l : enum_maps[i][(int)l];
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
      if( _hash != r._hash ) return false; // Shortcut
      if( _row == r._row ) return true; // Another shortcut: same absolute row
      // Now must check key contents
      return Arrays.equals(_keys,r._keys); 
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
    // All active Merges have a per-Node hashset of one of the datasets
    static IcedHashMap<Key,MergeSet> MERGE_SETS = new IcedHashMap<>();
    final Key _uniq;      // Key to allow sharing of this MergeSet on each Node
    final int _ncols;     // Number of leading columns for the Hash Key
    final int[][] _id_maps;
    final boolean _allRite;
    boolean _dup;
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
      final IcedHashMap<Row,String> rows = _rows; // Shared per-node hashset
      final int len = chks[0]._len;
      Row row = new Row(_ncols);
      for( int i=0; i<len; i++ )                  // For all rows
        if( add(rows,row.fill(chks,_id_maps,i)) ) // Fill & attempt add row
          row = new Row(_ncols); // If added, need a new row to fill
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
    @Override public void reduce( MergeSet ms ) {
      final IcedHashMap<Row,String> rows = _rows; // Shared per-node hashset
      if( rows == ms._rows ) return;
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
    protected static void addElem(NewChunk nc, Vec v, long absRow, ValueString vstr) {
      switch( v.get_type() ) {
      case Vec.T_NUM : nc.addNum(v.at(absRow)); break;
      case Vec.T_ENUM:
      case Vec.T_TIME: if( v.isNA(absRow) ) nc.addNA(); else nc.addNum(v.at8(absRow)); break;
      case Vec.T_STR : nc.addStr(v.atStr(vstr, absRow)); break;
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
      water.parser.ValueString vstr = new water.parser.ValueString(); // Recycled value string
      int len = chks[0]._len;
      for( int i=0; i<len; i++ ) {
        Row hashed = rows.getk(row.fill(chks,null,i));
        if( hashed == null ) {  // Hashed is missing
          for( NewChunk nc : nchks ) nc.addNA(); // All Left: keep row, use missing data
        } else {
          // Copy fields from matching hashed set into walked set
          final long absrow = hashed._row;
          for( int c = 0; c < nchks.length; c++ )
            addElem(nchks[c], vecs[_ncols+c],absrow,vstr);
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
      water.parser.ValueString vstr = new water.parser.ValueString(); // Recycled value string
      int len = chks[0]._len;
      for( int i=0; i<len; i++ ) {
        Row hashed = rows.getk(row.fill(chks,null,i));
        if( hashed == null ) {    // no rows, fill in chks, and pad NAs as needed...
          if( _allLeft ) {        // pad NAs to the right...
            int c=0;
            for(; c< chks.length;++c) addElem(nchks[c],chks[c],i);
            for(; c<nchks.length;++c) nchks[c].addNA();
          } // else no hashed and no _allLeft... skip (row is dropped)
        } else {
          if( hashed._dups!=null ) for(long absrow : hashed._dups ) addRow(nchks,chks,vecs,i,  absrow   ,vstr);
          else                                                      addRow(nchks,chks,vecs,i,hashed._row,vstr);
        }
      }
    }
    void addRow(NewChunk[] nchks, Chunk[] chks, Vec[] vecs, int relRow, long absRow, ValueString vstr) {
      int c=0;
      for( ;c< chks.length;++c) addElem(nchks[c],chks[c],relRow);
      for( ;c<nchks.length;++c) addElem(nchks[c],vecs[c - (chks.length + _ncols)],absRow,vstr);
    }
  }
}

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
    int[][] enum_maps = new int[ncols][];
    int[][]   id_maps = new int[ncols][];
    for( int i=0; i<ncols; i++ ) {
      Vec lv = walked.vecs()[i];
      if( lv.isEnum() ) {
        EnumWrappedVec ewv = new EnumWrappedVec(lv.domain(),hashed.vecs()[i].domain());
        int[] ids = enum_maps[i] = ewv.enum_map();
        DKV.remove(ewv._key);
        // Build an Identity map for the hashed set
        id_maps[i] = new int[ids.length];
        for( int j=0; j<ids.length; j++ )  id_maps[i][j] = j;
      }
    }
    
    // The lifetime of the distributed dataset is independent of the original
    // dataset, so it needs to be a deep copy.
    // TODO: COW Optimization
    walked = walked.deepCopy(null);

    // Build the hashed version of the hashed frame.  Hash and equality are
    // based on the known-integer key columns.  Duplicates are either ignored
    // (!allRite) or accumulated, and can force replication of the walked set.
    Key uniq = new MergeSet(ncols,id_maps,allRite).doAll(hashed)._uniq;

    // run a global parallel work: lookup non-hashed rows in hashSet; find
    // matching row; append matching column data
    String[]   names  = Arrays.copyOfRange(hashed._names,   ncols,hashed._names   .length);
    String[][] domains= Arrays.copyOfRange(hashed.domains(),ncols,hashed.domains().length);
    Frame res = new DoJoin(ncols,uniq,hashed,allLeft,allRite).doAll(hashed.numCols()-ncols,walked).outputFrame(names,domains);
    return new ValFrame(walked.add(res));
  }

  // One Row object per row of the smaller dataset, so kept as small as
  // possible.
  private static class Row {
    final long[] _keys;   // Key: first ncols of longs
    int _hash;            // Hash of keys; not final as Row objects are reused
    boolean _dup;         // True if dups (TODO: actually the list of dup rows/chks)
    long _row;            // Row in Vec; the payload is vecs[].atd(_row)
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
      _dup = false;               // No dups yet...
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
  }

  // Build a HashSet of one entire Frame, where the Key is the contents of the
  // first few columns.  One entry-per-row.
  private static class MergeSet extends MRTask<MergeSet> {
    // All active Merges have a per-Node hashset of one of the datasets
    static NonBlockingHashMap<Key,MergeSet> MERGE_SETS = new NonBlockingHashMap<>();
    final Key _uniq;      // Key to allow sharing of this MergeSet on each Node
    final int _ncols;     // Number of leading columns for the Hash Key
    final int[][] _id_maps;
    final boolean _allRite;
    transient NonBlockingHashSet<Row> _rows;

    MergeSet( int ncols, int[][] id_maps, boolean allRite ) { 
      _uniq=Key.make();  _ncols = ncols;  _id_maps = id_maps;  _allRite = allRite;
    }
    // Per-node, make the empty hashset for later reduction
    @Override public void setupLocal() {
      _rows = new NonBlockingHashSet<>();
      MERGE_SETS.put(_uniq,this);
    }

    @Override public void map( Chunk chks[] ) {
      final NonBlockingHashSet<Row> rows = _rows; // Shared per-node hashset
      final int len = chks[0]._len;
      Row row = new Row(_ncols);
      for( int i=0; i<len; i++ ) {
        boolean added = _rows.add(row.fill(chks,_id_maps,i));
        if( !added ) {                    // dup handling: keys are identical
          if( _allRite ) row._dup = true; // Flag as having dups (TODO: actually gather the dups)
        } else {                          // Else was added
          row = new Row(_ncols);          // So do not re-use, but make new
        }
      }
    }
    @Override public void reduce( MergeSet ms ) {
      if( _rows == ms._rows ) return;
      throw H2O.unimpl();
    }
  }

  // Build the join-set by iterating over all the local Chunks of the larger
  // dataset, doing a hash-lookup on the smaller replicated dataset, and adding
  // in the matching columns.
  private static class DoJoin extends MRTask<DoJoin> {
    private final Key _uniq;      // Which mergeset being merged
    private final int _ncols;     // Number of merge columns
    private final Frame _hashed;
    private final boolean _allLeft, _allRite;
    DoJoin( int ncols, Key uniq, Frame hashed, boolean allLeft, boolean allRite ) {
      _uniq = uniq; _ncols = ncols; _hashed = hashed; _allLeft = allLeft; _allRite = allRite;
    }
    @Override public void map( Chunk chks[], NewChunk nchks[] ) {
      // Shared common hash map
      NonBlockingHashSet<Row> rows = MergeSet.MERGE_SETS.get(_uniq)._rows;
      Vec[] vecs = _hashed.vecs(); // Data source from hashed set
      assert vecs.length == _ncols + nchks.length;
      Row row = new Row(_ncols);  // Recycled Row object on the bigger dataset
      water.parser.ValueString vstr = new water.parser.ValueString(); // Recycled value string
      int len = chks[0]._len;
      for( int i=0; i<len; i++ ) {
        Row hashed = rows.get(row.fill(chks,null,i));
        if( hashed == null ) {  // Hashed is missing
          if( _allLeft )        // But need all of larger, so force a NA row
            for( NewChunk nc : nchks ) nc.addNA();
          else
            throw H2O.unimpl(); // Need to remove walked row
        } else {
          if( hashed._dup && _allRite )
            throw H2O.unimpl(); // Need to dup walked row
          // Copy fields from matching smaller set into larger set
          final long absrow = hashed._row;
          for( int c = 0; c < nchks.length; c++ ) {
            Vec v = vecs[_ncols+c];
            switch( v.get_type() ) {
            case Vec.T_NUM : nchks[c].addNum(v.at(absrow)); break;
            case Vec.T_ENUM: 
            case Vec.T_TIME: if( v.isNA(absrow) ) nchks[c].addNA(); else nchks[c].addNum(v.at8(absrow)); break;
            case Vec.T_STR : nchks[c].addStr(v.atStr(vstr,absrow)); break;
            default: throw H2O.unimpl();
            }
          }
        }
      }
    }
    // Cleanup after last pass
    @Override public void closeLocal() { MergeSet.MERGE_SETS.remove(_uniq);  }
  }

}

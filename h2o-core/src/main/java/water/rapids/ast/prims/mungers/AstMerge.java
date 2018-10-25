package water.rapids.ast.prims.mungers;

import water.H2O;
import water.Iced;
import water.Key;
import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.vals.ValFrame;
import water.util.IcedHashMap;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * plyr's merge: Join by any other name.
 * Sample AstRoot: (merge $leftFrame $rightFrame allLeftFlag allRightFlag)
 * <p/>
 * Joins two frames; all columns with the same names will be the join key.  If
 * you want to join on a subset of identical names, rename the columns first
 * (otherwise the same column name would appear twice in the result).
 * <p/>
 * If the client side wants to allow named columns to be merged, the client
 * side is responsible for renaming columns as needed to bring the names into
 * alignment as above.  This can be as simple as renaming the RHS to match the
 * LHS column names.  Duplicate columns NOT part of the merge are still not
 * allowed - because the resulting Frame will end up with duplicate column
 * names which blows a Frame invariant (uniqueness of column names).
 * <p/>
 * If allLeftFlag is true, all rows in the leftFrame will be included, even if
 * there is no matching row in the rightFrame, and vice-versa for
 * allRightFlag.  Missing data will appear as NAs.  Both flags can be true.
 * </p>
 * We support merge method hash, radix and auto.  If a user chooses auto, the
 * algorithm will default to method radix which is the better algorithm.  It
 * gives accurate merge results even if there are duplicated rows in the rightFrame.
 * In addition, the radix method will allow the presences of string columns in
 * the frames.  The Hash method will not give correct merge results if there
 * are duplicated rows in the rightFrame.  The hash method cannot work with String columns,
 * they need to be casted to enums/integer columns before calling merge.
 */
public class AstMerge extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"left", "rite", "all_left", "all_rite", "by_left", "by_right", "method"};
  }

  @Override
  public String str() {
    return "merge";
  }

  @Override
  public int nargs() {
    return 1 + 7;
  } // (merge left rite all.left all.rite method)

  // Size cutoff before switching between a hashed-join vs a sorting join.
  // Hash tables beyond this count are assumed to be inefficient, and we're
  // better served by sorting all the join columns and doing a global
  // merge-join.
  static final int MAX_HASH_SIZE = 120000000;

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame l = stk.track(asts[1].exec(env)).getFrame();
    Frame r = stk.track(asts[2].exec(env)).getFrame();
    boolean allLeft = asts[3].exec(env).getNum() == 1;
    boolean allRite = asts[4].exec(env).getNum() == 1;
    int[] byLeft = check(asts[5]);
    int[] byRite = check(asts[6]);
    String method = asts[7].exec(env).getStr();

    // byLeft and byRight contains the columns to match between
    // check them
    if (byLeft.length == 0) {
      assert byRite.length==0;
      // Now find common column names here on the Java side. As for Python caller currently.
      ArrayList<Integer> leftTmp = new ArrayList<>();
      ArrayList<Integer> riteTmp = new ArrayList<>();
      for (int i=0; i<l._names.length; i++) {
        int idx = r.find(l._names[i]);
        if (idx != -1) {
          leftTmp.add(i);
          riteTmp.add(idx);
        }
      }
      if (leftTmp.size() == 0) throw new IllegalArgumentException("No join columns specified and there are no common names");
      byLeft = new int[leftTmp.size()];
      byRite = new int[riteTmp.size()];
      for (int i=0; i < byLeft.length; i++)
      {
        byLeft[i] = leftTmp.get(i).intValue();
        byRite[i] = riteTmp.get(i).intValue();
      }
    }

    if (byLeft.length != byRite.length)
      throw new IllegalArgumentException("byLeft and byRight are not the same length");
    int ncols = byLeft.length;  // Number of join columns dealt with so far
    l.moveFirst(byLeft);
    r.moveFirst(byRite);
    for (int i = 0; i < ncols; i++) {
        Vec lv = l.vecs()[i];
        Vec rv = r.vecs()[i];
        if (lv.get_type() != rv.get_type())
          throw new IllegalArgumentException("Merging columns must be the same type, column " + l._names[ncols] +
              " found types " + lv.get_type_str() + " and " + rv.get_type_str());
        if (method.equals("hash") && lv.isString())
          throw new IllegalArgumentException("Cannot merge Strings with hash method; flip toCategoricalVec first" +
                  " or set your method to auto or radix");
    }

    // GC now to sync nodes and get them to use young gen for the working memory. This helps get stable
    // repeatable timings.  Otherwise full GCs can cause blocks. Adding System.gc() here suggested by Cliff
    // during F2F pair-programming and it for sure worked.
    // TODO - would be better at the end to clean up, but there are several exit paths here.
    new MRTask() {
      @Override
      public void setupLocal() {
        System.gc();
      }
    }.doAllNodes();

    if (method.equals("radix") || method.equals("auto")) {  // default to radix as default merge metho
      // Build categorical mappings, to rapidly convert categoricals from the left to the right
      // With the sortingMerge approach there is no variance here: always map left to right
      if (allLeft && allRite)
        throw new IllegalArgumentException("all.x=TRUE and all.y=TRUE is not supported.  Choose one only.");

      boolean onlyLeftAllOff = (allLeft && !allRite) || !allRite;
      int[][] id_maps = new int[ncols][];
      for (int i = 0; i < ncols; i++) { // flip the frame orders for allRite
        Vec lv = onlyLeftAllOff ? l.vec(i) : r.vec(i);
        Vec rv = onlyLeftAllOff ? r.vec(i) : l.vec(i);

        if (onlyLeftAllOff ? lv.isCategorical() : rv.isCategorical()) {
          assert onlyLeftAllOff ? rv.isCategorical() : lv.isCategorical();  // if not, would have thrown above
          id_maps[i] = onlyLeftAllOff ? CategoricalWrappedVec.computeMap(lv.domain(), rv.domain()) : CategoricalWrappedVec.computeMap(rv.domain(), lv.domain());
        }
      }

      if (onlyLeftAllOff) {
        return sortingMerge(l, r, allLeft, ncols, id_maps);
      } else {  // implement allRite here by switching leftframe and riteframe.  However, column order is wrong, re-order before return
        ValFrame tempFrame = sortingMerge(r, l, allRite, ncols, id_maps);
        Frame mergedFrame = tempFrame.getFrame();  // need to switch order of merged frame
        int allColNum = mergedFrame.numCols();
        int[] colMapping = new int[allColNum];  // index into combined frame but with correct order
        for (int index = 0; index < ncols; index++) {
          colMapping[index] = index;    // no change to column order in the key columns
        }
        int offset = r.numCols() - ncols;
        for (int index = ncols; index < l.numCols(); index++) { // set the order for right frame
          colMapping[index] = offset + index;        // move the left columns to the front
        }
        offset = l.numCols() - ncols;
        for (int index = l.numCols(); index < allColNum; index++) {
          colMapping[index] = index - offset;
        }

        mergedFrame.reOrder(colMapping);  // reorder the frame columns for allrite = true
        return tempFrame;
      }
    }

    // Pick the frame to replicate & hash.  If one set is "all" and the other
    // is not, the "all" set must be walked, so the "other" is hashed.  If both
    // or neither are "all", then pick the smallest bytesize of the non-key
    // columns.  The hashed dataframe is completely replicated per-node
    boolean walkLeft;
    if (allLeft == allRite) {
      walkLeft = l.numRows() > r.numRows();
    } else {
      walkLeft = allLeft;
    }
    Frame walked = walkLeft ? l : r;
    Frame hashed = walkLeft ? r : l;
    if (!walkLeft) {
      boolean tmp = allLeft;
      allLeft = allRite;
      allRite = tmp;
    }

    // Build categorical mappings, to rapidly convert categoricals from the
    // distributed set to the hashed & replicated set.
    int[][] id_maps = new int[ncols][];
    for (int i = 0; i < ncols; i++) {
      Vec lv = walked.vecs()[i];
      if (lv.isCategorical())
        id_maps[i] = CategoricalWrappedVec.computeMap(hashed.vecs()[i].domain(), lv.domain());
    }

    // Build the hashed version of the hashed frame.  Hash and equality are
    // based on the known-integer key columns.  Duplicates are either ignored
    // (!allRite) or accumulated, and can force replication of the walked set.
    //
    // Count size of this hash table as-we-go.  Bail out if the size exceeds
    // a known threshold, and switch a sorting join instead of a hashed join.
    final MergeSet ms = new MergeSet(ncols, id_maps, allRite).doAll(hashed);
    final Key uniq = ms._uniq;
    IcedHashMap<Row, String> rows = MergeSet.MERGE_SETS.get(uniq)._rows;
    new MRTask() {
      @Override
      public void setupLocal() {
        MergeSet.MERGE_SETS.remove(uniq);
      }
    }.doAllNodes();
    if (method.equals("auto") && (rows == null || rows.size() > MAX_HASH_SIZE))  // Blew out hash size; switch to a sorting join.  Matt: even with 0, rows was size 3 hence added ||
      return sortingMerge(l, r, allLeft, ncols, id_maps);

    // All of the walked set, and no dup handling on the right - which means no
    // need to replicate rows of the walked dataset.  Simple 1-pass over the
    // walked set adding in columns (or NAs) from the right.
    if (allLeft && !(allRite && ms._dup)) {
      // The lifetime of the distributed dataset is independent of the original
      // dataset, so it needs to be a deep copy.
      // TODO: COW Optimization
      walked = walked.deepCopy(null);

      // run a global parallel work: lookup non-hashed rows in hashSet; find
      // matching row; append matching column data
      String[] names = Arrays.copyOfRange(hashed._names, ncols, hashed._names.length);
      String[][] domains = Arrays.copyOfRange(hashed.domains(), ncols, hashed.domains().length);
      byte[] types = Arrays.copyOfRange(hashed.types(), ncols, hashed.numCols());
      Frame res = new AllLeftNoDupe(ncols, rows, hashed, allRite).doAll(types, walked).outputFrame(names, domains);
      return new ValFrame(walked.add(res));
    }

    // Can be full or partial on the left, but won't nessecarily do all of the
    // right.  Dups on right are OK (left will be replicated or dropped as needed).
    if (!allRite) {
      String[] names = Arrays.copyOf(walked.names(), walked.numCols() + hashed.numCols() - ncols);
      System.arraycopy(hashed.names(), ncols, names, walked.numCols(), hashed.numCols() - ncols);
      String[][] domains = Arrays.copyOf(walked.domains(), walked.numCols() + hashed.numCols() - ncols);
      System.arraycopy(hashed.domains(), ncols, domains, walked.numCols(), hashed.numCols() - ncols);
      byte[] types = walked.types();
      types = Arrays.copyOf(types, types.length + hashed.numCols() - ncols);
      System.arraycopy(hashed.types(), ncols, types, walked.numCols(), hashed.numCols() - ncols);
      return new ValFrame(new AllRiteWithDupJoin(ncols, rows, hashed, allLeft).doAll(types, walked).outputFrame(names, domains));
    }

    throw H2O.unimpl();
  }

  /**
   * Use a sorting merge/join, probably because the hash table size exceeded
   * MAX_HASH_SIZE; i.e. the number of unique keys in the hashed Frame exceeds
   * MAX_HASH_SIZE.  Join is done on the first ncol columns in both frames,
   * which are already known to be not-null and have matching names and types.
   * The walked and hashed frames are sorted according to allLeft; if allRite
   * is set then allLeft will also be set (but not vice-versa).
   *
   * @param left    is the LHS frame; not-null.
   * @param right   is the RHS frame; not-null.
   * @param allLeft all rows in the LHS frame will appear in the result frame.
   * @param ncols   is the number of columns to join on, and these are ordered
   *                as the first ncols of both the left and right frames.
   * @param id_maps if not-null denote simple integer mappings from one
   *                categorical column to another; the width is ncols
   */

  private ValFrame sortingMerge(Frame left, Frame right, boolean allLeft, int ncols, int[][] id_maps) {
    int cols[] = new int[ncols];
    for (int i = 0; i < ncols; i++) cols[i] = i;
    return new ValFrame(Merge.merge(left, right, cols, cols, allLeft, id_maps));
  }

  // One Row object per row of the hashed dataset, so kept as small as
  // possible.
  private static class Row extends Iced {
    final long[] _keys;   // Key: first ncols of longs
    int _hash;            // Hash of keys; not final as Row objects are reused
    long _row;            // Row in Vec; the payload is vecs[].atd(_row)
    long[] _dups;         // dup rows stored here (includes _row); updated atomically.
    int _dupIdx;          // pointer into _dups array; updated atomically

    Row(int ncols) {
      _keys = new long[ncols];
    }

    Row fill(final Chunk[] chks, final int[][] cat_maps, final int row) {
      // Precompute hash: columns are integer only (checked before we started
      // here).  NAs count as a zero for hashing.
      long l, hash = 0;
      for (int i = 0; i < _keys.length; i++) {
        if (chks[i].isNA(row)) l = 0;
        else {
          l = chks[i].at8(row);
          l = (cat_maps == null || cat_maps[i] == null) ? l : cat_maps[i][(int) l];
          hash += l;
        }
        _keys[i] = l;
      }
      _hash = (int) (hash ^ (hash >> 32));
      _row = chks[0].start() + row; // Payload: actual absolute row number
      return this;
    }

    @Override
    public int hashCode() {
      return _hash;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Row)) return false;
      Row r = (Row) o;
      return _hash == r._hash && Arrays.equals(_keys, r._keys);
    }

    private void atomicAddDup(long row) {
      synchronized (this) {
        if (_dups == null) {
          _dups = new long[]{_row, row};
          _dupIdx = 2;
        } else {
          if (_dupIdx == _dups.length)
            _dups = Arrays.copyOf(_dups, _dups.length << 1);
          _dups[_dupIdx++] = row;
        }
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
    static IcedHashMap<Key, MergeSet> MERGE_SETS = new IcedHashMap<>();
    final Key _uniq;      // Key to allow sharing of this MergeSet on each Node
    final int _ncols;     // Number of leading columns for the Hash Key
    final int[][] _id_maps; // Rapid mapping between matching enums
    final boolean _allRite; // Collect all rows with the same matching Key, or just the first
    boolean _dup;           // Dups are present at all
    IcedHashMap<Row, String> _rows;

    MergeSet(int ncols, int[][] id_maps, boolean allRite) {
      _uniq = Key.make();
      _ncols = ncols;
      _id_maps = id_maps;
      _allRite = allRite;
    }

    // Per-node, make the empty hashset for later reduction
    @Override
    public void setupLocal() {
      _rows = new IcedHashMap<>();
      MERGE_SETS.put(_uniq, this);
    }

    @Override
    public void map(Chunk chks[]) {
      final IcedHashMap<Row, String> rows = MERGE_SETS.get(_uniq)._rows; // Shared per-node HashMap
      if (rows == null) return; // Missing: Aborted due to exceeding size
      final int len = chks[0]._len;
      Row row = new Row(_ncols);
      for (int i = 0; i < len; i++)                    // For all rows
        if (add(rows, row.fill(chks, _id_maps, i))) { // Fill & attempt add row
          if (rows.size() > MAX_HASH_SIZE) {
            abort();
            return;
          }
          row = new Row(_ncols); // If added, need a new row to fill
        }
    }

    private boolean add(IcedHashMap<Row, String> rows, Row row) {
      if (rows.putIfAbsent(row, "") == null)
        return true;            // Added!
      // dup handling: keys are identical
      if (_allRite) {          // Collect the dups?
        _dup = true;            // MergeSet has dups.
        rows.getk(row).atomicAddDup(row._row);
      }
      return false;
    }

    private void abort() {
      MERGE_SETS.get(_uniq)._rows = _rows = null;
    }

    @Override
    public void reduce(MergeSet ms) {
      final IcedHashMap<Row, String> rows = _rows; // Shared per-node hashset
      if (rows == ms._rows) return;
      if (rows == null || ms._rows == null) {
        abort();
        return;
      } // Missing: aborted due to size
      for (Row row : ms._rows.keySet())
        add(rows, row);          // Merge RHS into LHS, collecting dups as we go
    }
  }

  private static abstract class JoinTask extends MRTask<JoinTask> {
    protected final IcedHashMap<Row, String> _rows;
    protected final int _ncols;     // Number of merge columns
    protected final Frame _hashed;
    protected final boolean _allLeft, _allRite;

    JoinTask(int ncols, IcedHashMap<Row, String> rows, Frame hashed, boolean allLeft, boolean allRite) {
      _rows = rows;
      _ncols = ncols;
      _hashed = hashed;
      _allLeft = allLeft;
      _allRite = allRite;
    }

    protected static void addElem(NewChunk nc, Chunk c, int row) {
      c.extractRows(nc,row,row+1);
    }

    protected static void addElem(NewChunk nc, Vec v, long absRow, BufferedString bStr) {
      Chunk c = v.chunkForRow(absRow);
      int relRow = (int)(absRow-c.start());
      c.extractRows(nc,relRow,relRow+1);
    }
  }

  // Build the join-set by iterating over all the local Chunks of the walked
  // dataset, doing a hash-lookup on the hashed replicated dataset, and adding
  // in the matching columns.
  private static class AllLeftNoDupe extends JoinTask {
    AllLeftNoDupe(int ncols, IcedHashMap<Row, String> rows, Frame hashed, boolean allRite) {
      super(ncols, rows, hashed, true, allRite);
    }

    @Override
    public void map(Chunk chks[], NewChunk nchks[]) {
      // Shared common hash map
      final IcedHashMap<Row, String> rows = _rows;
      Vec[] vecs = _hashed.vecs(); // Data source from hashed set
      assert vecs.length == _ncols + nchks.length;
      Row row = new Row(_ncols);  // Recycled Row object on the bigger dataset
      BufferedString bStr = new BufferedString(); // Recycled BufferedString
      int len = chks[0]._len;
      for (int i = 0; i < len; i++) {
        Row hashed = rows.getk(row.fill(chks, null, i));
        if (hashed == null) {  // Hashed is missing
          for (NewChunk nc : nchks) nc.addNA(); // All Left: keep row, use missing data
        } else {
          // Copy fields from matching hashed set into walked set
          final long absrow = hashed._row;
          for (int c = 0; c < nchks.length; c++)
            addElem(nchks[c], vecs[_ncols + c], absrow, bStr);
        }
      }
    }
  }

  private int[] check(AstRoot ast) {
    double[] n;
    if (ast instanceof AstNumList) n = ((AstNumList) ast).expand();
    else if (ast instanceof AstNum)
      n = new double[]{((AstNum) ast).getNum()};  // this is the number of breaks wanted...
    else throw new IllegalArgumentException("Requires a number-list, but found a " + ast.getClass());
    int[] ni = new int[n.length];
    for (int i = 0; i < ni.length; ++i)
      ni[i] = (int) n[i];
    return ni;
  }

  // Build the join-set by iterating over all the local Chunks of the walked
  // dataset, doing a hash-lookup on the hashed replicated dataset, and adding
  // in BOTH the walked and the matching columns.
  private static class AllRiteWithDupJoin extends JoinTask {
    AllRiteWithDupJoin(int ncols, IcedHashMap<Row, String> rows, Frame hashed, boolean allLeft) {
      super(ncols, rows, hashed, allLeft, true);
    }

    @Override
    public void map(Chunk[] chks, NewChunk[] nchks) {
      // Shared common hash map
      final IcedHashMap<Row, String> rows = _rows;
      Vec[] vecs = _hashed.vecs(); // Data source from hashed set
//      assert vecs.length == _ncols + nchks.length;
      Row row = new Row(_ncols);   // Recycled Row object on the bigger dataset
      BufferedString bStr = new BufferedString(); // Recycled BufferedString
      int len = chks[0]._len;
      for (int i = 0; i < len; i++) {
        Row hashed = _rows.getk(row.fill(chks, null, i));
        if (hashed == null) {    // no rows, fill in chks, and pad NAs as needed...
          if (_allLeft) {        // pad NAs to the right...
            int c = 0;
            for (; c < chks.length; ++c) addElem(nchks[c], chks[c], i);
            for (; c < nchks.length; ++c) nchks[c].addNA();
          } // else no hashed and no _allLeft... skip (row is dropped)
        } else {
          if (hashed._dups != null) for (long absrow : hashed._dups) addRow(nchks, chks, vecs, i, absrow, bStr);
          else addRow(nchks, chks, vecs, i, hashed._row, bStr);
        }
      }
    }

    void addRow(NewChunk[] nchks, Chunk[] chks, Vec[] vecs, int relRow, long absRow, BufferedString bStr) {
      int c = 0;
      for (; c < chks.length; ++c) addElem(nchks[c], chks[c], relRow);
      for (; c < nchks.length; ++c) addElem(nchks[c], vecs[c - chks.length + _ncols], absRow, bStr);
    }
  }
}

package water.rapids.ast.prims.assign;

import water.DKV;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.*;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.ast.prims.mungers.AstColSlice;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;

import java.util.Arrays;

import static water.rapids.ast.prims.assign.AstRecAsgnHelper.*;

/**
 * Rectangular assign into a row and column slice.  The destination must
 * already exist.  The output is conceptually a new copy of the data, with a
 * fresh Frame.  Copy-On-Write optimizations lower the cost to be proportional
 * to the over-written sections.
 */
public class AstRectangleAssign extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"dst", "src", "col_expr", "row_expr"};
  }

  @Override
  public int nargs() {
    return 5;
  } // (:= dst src col_expr row_expr)

  @Override
  public String str() {
    return ":=";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    Frame dst = stk.track(asts[1].exec(env)).getFrame();
    Val vsrc = stk.track(asts[2].exec(env));
    AstParameter col_list = (AstParameter) asts[3];
    // Column selection
    AstNumList cols_numlist = new AstNumList(col_list.columns(dst.names()));
    // Special for AstAssign: "empty" really means "all"
    if (cols_numlist.isEmpty()) cols_numlist = new AstNumList(0, dst.numCols());
    // Allow R-like number list expansion: negative column numbers mean exclusion
    int[] cols = AstColSlice.col_select(dst.names(), cols_numlist);

    // Any COW optimized path changes Vecs in dst._vecs, and so needs a
    // defensive copy.  Any update-in-place path updates Chunks instead of
    // dst._vecs, and does not need a defensive copy.  To make life easier,
    // just make the copy now.
    dst = new Frame(dst._names, dst.vecs().clone());

    // Assign over the column slice
    if (asts[4] instanceof AstNum || asts[4] instanceof AstNumList) { // Explictly named row assignment
      AstNumList rows = (asts[4] instanceof AstNum)
          ? new AstNumList(((AstNum) asts[4]).getNum())
          : ((AstNumList) asts[4]);
      if (rows.isEmpty()) rows = new AstNumList(0, dst.numRows()); // Empty rows is really: all rows
      switch (vsrc.type()) {
        case Val.NUM:
          assign_frame_scalar(dst, cols, rows, nanToNull(vsrc.getNum()), env._ses);
          break;
        case Val.STR:
          assign_frame_scalar(dst, cols, rows, vsrc.getStr(), env._ses);
          break;
        case Val.FRM:
          assign_frame_frame(dst, cols, rows, vsrc.getFrame(), env._ses);
          break;
        default:
          throw new IllegalArgumentException("Source must be a Frame or Number, but found a " + vsrc.getClass());
      }
    } else {                    // Boolean assignment selection?
      Frame rows = stk.track(asts[4].exec(env)).getFrame();
      switch (vsrc.type()) {
        case Val.NUM:
          assign_frame_scalar(dst, cols, rows, nanToNull(vsrc.getNum()), env._ses);
          break;
        case Val.STR:
          assign_frame_scalar(dst, cols, rows, vsrc.getStr(), env._ses);
          break;
        case Val.FRM:
          throw H2O.unimpl();
        default:
          throw new IllegalArgumentException("Source must be a Frame or Number, but found a " + vsrc.getClass());
      }
    }
    return new ValFrame(dst);
  }

  // Rectangular array copy from src into dst
  private void assign_frame_frame(Frame dst, int[] cols, AstNumList rows, Frame src, Session ses) {
    // Sanity check
    if (cols.length != src.numCols())
      throw new IllegalArgumentException("Source and destination frames must have the same count of columns");
    long nrows = rows.cnt();
    if (src.numRows() != nrows)
      throw new IllegalArgumentException("Requires same count of rows in the number-list (" + nrows + ") as in the source (" + src.numRows() + ")");

    // Whole-column assignment?  Directly reuse columns: Copy-On-Write
    // optimization happens here on the apply() exit.
    if (dst.numRows() == nrows && rows.isDense()) {
      for (int i = 0; i < cols.length; i++)
        dst.replace(cols[i], src.vecs()[i]);
      if (dst._key != null) DKV.put(dst);
      return;
    }

    // Partial update; needs to preserve type, and may need to copy to support
    // copy-on-write
    Vec[] dvecs = dst.vecs();
    final Vec[] svecs = src.vecs();
    for (int col = 0; col < cols.length; col++) {
      int dtype = dvecs[cols[col]].get_type();
      if (dtype != svecs[col].get_type())
        throw new IllegalArgumentException("Columns must be the same type; " +
                "column " + col + ", \'" + dst._names[cols[col]] + "\', is of type " + dvecs[cols[col]].get_type_str() +
                " and the source is " + svecs[col].get_type_str());
      if ((dtype == Vec.T_CAT) && (! Arrays.equals(dvecs[cols[col]].domain(), svecs[col].domain())))
        throw new IllegalArgumentException("Cannot assign to a categorical column with a different domain; " +
                "source column " + src._names[col] + ", target column " + dst._names[cols[col]]);
    }

    // Frame fill
    // Handle fast small case
    if (nrows <= 1 || (cols.length * nrows) <= 1000) { // Go parallel for more than 1000 random updates
      // Copy dst columns as-needed to allow update-in-place
      dvecs = ses.copyOnWrite(dst, cols); // Update dst columns
      long[] rownums = rows.expand8();   // Just these rows
      for (int col = 0; col < svecs.length; col++)
        if (svecs[col].get_type() == Vec.T_STR) {
          BufferedString bStr = new BufferedString();
          for (int ridx = 0; ridx < rownums.length; ridx++) {
            BufferedString s = svecs[col].atStr(bStr, ridx);
            dvecs[cols[col]].set(rownums[ridx], s != null ? s.toString() : null);
          }
        } else {
          for (int ridx = 0; ridx < rownums.length; ridx++)
            dvecs[cols[col]].set(rownums[ridx], svecs[col].at(ridx));
        }
      return;
    }
    // Handle large case
    Vec[] vecs = ses.copyOnWrite(dst, cols);
    Vec[] vecs2 = new Vec[cols.length]; // Just the selected columns get updated
    for (int i = 0; i < cols.length; i++)
      vecs2[i] = vecs[cols[i]];
    rows.sort();                // Side-effect internal sort; needed for fast row lookup
    new AssignFrameFrameTask(rows, svecs).doAll(vecs2);
  }

  private static class AssignFrameFrameTask extends RowSliceTask {
    private Vec[] _svecs;
    private AssignFrameFrameTask(AstNumList rows, Vec[] svecs) {
      super(rows);
      _svecs = svecs;
    }
    @Override
    void mapChunkSlice(Chunk[] cs, int chkOffset) {
      long start = cs[0].start();
      Chunk[] scs = null;
      for (int i = chkOffset; i < cs[0]._len; ++i) {
        long idx = _rows.index(start + i);
        if (idx < 0) continue;
        if ((scs == null) || (scs[0].start() < idx) || (idx >= scs[0].start() + scs[0].len())) {
          int sChkIdx = _svecs[0].elem2ChunkIdx(idx);
          scs = new Chunk[_svecs.length];
          for (int j = 0; j < _svecs.length; j++) {
            scs[j] = _svecs[j].chunkForChunkIdx(sChkIdx);
          }
        }
        BufferedString bStr = new BufferedString();
        int si = (int) (idx - scs[0].start());
        for (int j = 0; j < cs.length; j++) {
          Chunk chk = cs[j];
          Chunk schk = scs[j];
          if (_svecs[j].get_type() == Vec.T_STR) {
            BufferedString s = schk.atStr(bStr, si);
            chk.set(i, s != null ? s.toString() : null);
            BufferedString bss = chk.atStr(new BufferedString(), i);
            if (s == null && bss != null) {
              chk.set(i, s != null ? s.toString() : null);
            }
          } else {
            chk.set(i, schk.atd(si));
          }
        }
      }
    }
  }

  // Assign a SCALAR over some dst rows; optimize for all rows
  private void assign_frame_scalar(Frame dst, int[] cols, AstNumList rows, Object src, Session ses) {
    long nrows = rows.cnt();

    // Bulk assign a numeric constant (probably zero) over a frame.  Directly set
    // columns: Copy-On-Write optimization happens here on the apply() exit.
    // Note: this skips "scalar to Vec" compatibility check because the whole Vec is overwritten
    if (dst.numRows() == nrows && rows.isDense() && (src instanceof Number)) {
      Vec anyVec = dst.anyVec();
      assert anyVec != null;  // if anyVec was null, then dst.numRows() would have been 0
      Vec vsrc = anyVec.makeCon((double) src);
      for (int col : cols)
        dst.replace(col, vsrc);
      if (dst._key != null) DKV.put(dst);
      return;
    }

    // Make sure the scalar value is compatible with the target vector
    for (int col: cols) {
      if (! isScalarCompatible(src, dst.vec(col))) {
        throw new IllegalArgumentException("Cannot assign value " + src + " into a vector of type " + dst.vec(col).get_type_str() + ".");
      }
    }

    // Handle fast small case
    if (nrows == 1) {
      Vec[] vecs = ses.copyOnWrite(dst, cols);
      long drow = (long) rows._bases[0];
      for (int col : cols)
        createValueSetter(vecs[col], src).setValue(vecs[col], drow);
      return;
    }

    // Handle large case
    Vec[] vecs = ses.copyOnWrite(dst, cols);
    Vec[] vecs2 = new Vec[cols.length]; // Just the selected columns get updated
    for (int i = 0; i < cols.length; i++)
      vecs2[i] = vecs[cols[i]];
    rows.sort();                // Side-effect internal sort; needed for fast row lookup
    AssignFrameScalarTask.doAssign(rows, vecs2, src);
  }

  private static class AssignFrameScalarTask extends RowSliceTask {

    final ValueSetter[] _setters;

    AssignFrameScalarTask(AstNumList rows, Vec[] vecs, Object value) {
      super(rows);
      _setters = new ValueSetter[vecs.length];
      for (int i = 0; i < _setters.length; i++)
        _setters[i] = createValueSetter(vecs[i], value);
    }

    @Override
    void mapChunkSlice(Chunk[] cs, int chkOffset) {
      long start = cs[0].start();
      for (int i = chkOffset; i < cs[0]._len; ++i)
        if (_rows.has(start + i))
          for (int col = 0; col < cs.length; col++)
            _setters[col].setValue(cs[col], i);
    }

    /**
     * Assigns a given value to a specified rows of given Vecs.
     * @param rows row specification
     * @param dst target Vecs
     * @param src source Value
     */
    static void doAssign(AstNumList rows, Vec[] dst, Object src) {
      new AssignFrameScalarTask(rows, dst, src).doAll(dst);
    }
  }

  private boolean isScalarCompatible(Object scalar, Vec v) {
    if (scalar == null)
      return true;
    else if (scalar instanceof Number)
      return v.get_type() == Vec.T_NUM || v.get_type() == Vec.T_TIME;
    else if (scalar instanceof String) {
      if (v.get_type() == Vec.T_CAT) {
        return ArrayUtils.contains(v.domain(), (String) scalar);
      } else
        return v.get_type() == Vec.T_STR || (v.get_type() == Vec.T_UUID);
    } else
      return false;
  }

  private static Double nanToNull(double value) {
    return Double.isNaN(value) ? null : value;
  }

  // Boolean assignment with a scalar
  private void assign_frame_scalar(Frame dst, int[] cols, Frame rows, Object src, Session ses) {
    Vec bool = rows.vec(0);
    if (dst.numRows() != rows.numRows()) {
      throw new IllegalArgumentException("Frame " + dst._key + " has different number of rows than frame " + rows._key +
              " (" + dst.numRows() + " vs " + rows.numRows() + ").");
    }
    // Bulk assign a numeric constant over a frame. Directly set columns without checking target type
    // assuming the user just wants to overwrite everything: Copy-On-Write optimization happens here on the apply() exit.
    // Note: this skips "scalar to Vec" compatibility check because the whole Vec is overwritten
    if (bool.isConst() && ((int) bool.min() == 1) && (src instanceof Number)) {
      Vec anyVec = dst.anyVec();
      assert anyVec != null;
      Vec vsrc = anyVec.makeCon((double) src);
      for (int col : cols)
        dst.replace(col, vsrc);
      if (dst._key != null) DKV.put(dst);
      return;
    }

    // Make sure the scalar value is compatible with the target vector
    for (int col: cols) {
      if (! isScalarCompatible(src, dst.vec(col))) {
        throw new IllegalArgumentException("Cannot assign value " + src + " into a vector of type " + dst.vec(col).get_type_str() + ".");
      }
    }

    Vec[] vecs = ses.copyOnWrite(dst, cols);
    Vec[] vecs2 = new Vec[cols.length]; // Just the selected columns get updated
    for (int i = 0; i < cols.length; i++)
      vecs2[i] = vecs[cols[i]];

    ConditionalAssignTask.doAssign(vecs2, src, rows.vec(0));
  }

  private static class ConditionalAssignTask extends MRTask<ConditionalAssignTask> {

    final ValueSetter[] _setters;

    ConditionalAssignTask(Vec[] vecs, Object value) {
      _setters = new ValueSetter[vecs.length];
      for (int i = 0; i < _setters.length; i++) _setters[i] = AstRecAsgnHelper.createValueSetter(vecs[i], value);
    }

    @Override
    public void map(Chunk[] cs) {
      Chunk bool = cs[cs.length - 1];
      for (int row = 0; row < cs[0]._len; row++) {
        if (bool.at8(row) == 1)
          for (int col = 0; col < cs.length - 1; col++) _setters[col].setValue(cs[col], row);
      }
    }

    /**
     * Sets a given value to all cells where given predicateVec is true.
     * @param dst target Vecs
     * @param src source Value
     * @param predicateVec predicate Vec
     */
    static void doAssign(Vec[] dst, Object src, Vec predicateVec) {
      Vec[] vecs = new Vec[dst.length + 1];
      System.arraycopy(dst, 0, vecs, 0, dst.length);
      vecs[vecs.length - 1] = predicateVec;
      new ConditionalAssignTask(dst, src).doAll(vecs);
    }

  }

  private static abstract class RowSliceTask extends MRTask<RowSliceTask> {

    final AstNumList _rows;

    RowSliceTask(AstNumList rows) { _rows = rows; }

    @Override
    public void map(Chunk[] cs) {
      long start = cs[0].start();
      long end = start + cs[0]._len;
      long min = (long) _rows.min(), max = (long) _rows.max() - 1; // exclusive max to inclusive max when stride == 1
      //     [ start, ...,  end ]     the chunk
      //1 []                          rows out left:  rows.max() < start
      //2                         []  rows out rite:  rows.min() > end
      //3 [ rows ]                    rows run left:  rows.min() < start && rows.max() <= end
      //4          [ rows ]           rows run in  :  start <= rows.min() && rows.max() <= end
      //5                   [ rows ]  rows run rite:  start <= rows.min() && end < rows.max()
      if (!(max < start || min > end)) {   // not situation 1 or 2 above
        long startOffset = min > start ? min : start;  // situation 4 and 5 => min > start;
        int chkOffset = (int) (startOffset - start);
        mapChunkSlice(cs, chkOffset);
      }
    }

    abstract void mapChunkSlice(Chunk[] cs, int chkOffset);
  }

}

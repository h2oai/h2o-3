package water.rapids.ast.prims.assign;

import water.DKV;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.*;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.ast.prims.mungers.AstColSlice;
import water.rapids.vals.ValFrame;

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
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame dst = stk.track(asts[1].exec(env)).getFrame();
    Val vsrc = stk.track(asts[2].exec(env));
    // Column selection
    AstNumList cols_numlist = new AstNumList(asts[3].columns(dst.names()));
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
          assign_frame_scalar(dst, cols, rows, vsrc.getNum(), env._ses);
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
          assign_frame_scalar(dst, cols, rows, vsrc.getNum(), env._ses);
          break;
        case Val.STR:
          throw H2O.unimpl();
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
    for (int col = 0; col < cols.length; col++)
      if (dvecs[cols[col]].get_type() != svecs[col].get_type())
        throw new IllegalArgumentException("Columns must be the same type; column " + col + ", \'" + dst._names[cols[col]] + "\', is of type " + dvecs[cols[col]].get_type_str() + " and the source is " + svecs[col].get_type_str());

    // Frame fill
    // Handle fast small case
    if (nrows <= 1 || (cols.length * nrows) <= 1000) { // Go parallel for more than 1000 random updates
      // Copy dst columns as-needed to allow update-in-place
      dvecs = ses.copyOnWrite(dst, cols); // Update dst columns
      long[] rownums = rows.expand8();   // Just these rows
      for (int col = 0; col < svecs.length; col++)
        for (int ridx = 0; ridx < rownums.length; ridx++)
          dvecs[cols[col]].set(rownums[ridx], svecs[col].at(ridx));
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
        int si = (int) (idx - scs[0].start());
        for (int j = 0; j < cs.length; j++) {
          Chunk chk = cs[j];
          Chunk schk = scs[j];
          chk.set(i, schk.atd(si));
        }
      }
    }
  }

  // Assign a NON-STRING SCALAR over some dst rows; optimize for all rows
  private void assign_frame_scalar(Frame dst, int[] cols, AstNumList rows, double src, Session ses) {

    // Handle fast small case
    long nrows = rows.cnt();
    if (nrows == 1) {
      Vec[] vecs = ses.copyOnWrite(dst, cols);
      long drow = (long) rows._bases[0];
      for (int col : cols)
        vecs[col].set(drow, src);
      return;
    }

    // Bulk assign constant (probably zero) over a frame.  Directly set
    // columns: Copy-On-Write optimization happens here on the apply() exit.
    if (dst.numRows() == nrows && rows.isDense()) {
      Vec anyVec = dst.anyVec();
      assert anyVec != null;  // if anyVec was null, then dst.numRows() would have been 0
      Vec vsrc = anyVec.makeCon(src);
      for (int col : cols)
        dst.replace(col, vsrc);
      if (dst._key != null) DKV.put(dst);
      return;
    }

    // Handle large case
    Vec[] vecs = ses.copyOnWrite(dst, cols);
    Vec[] vecs2 = new Vec[cols.length]; // Just the selected columns get updated
    for (int i = 0; i < cols.length; i++)
      vecs2[i] = vecs[cols[i]];
    rows.sort();                // Side-effect internal sort; needed for fast row lookup
    new AssignFrameScalarTask(rows, src).doAll(vecs2);
  }

  private static class AssignFrameScalarTask extends RowSliceTask {
    private double _src;
    private AssignFrameScalarTask(AstNumList rows, double src) {
      super(rows);
      _src = src;
    }
    @Override
    void mapChunkSlice(Chunk[] cs, int chkOffset) {
      long start = cs[0].start();
      for (int i = chkOffset; i < cs[0]._len; ++i)
        if (_rows.has(start + i))
          for (Chunk chk : cs)
            chk.set(i, _src);
    }
  }

  // Assign a STRING over some dst rows; optimize for all rows
  private void assign_frame_scalar(Frame dst, int[] cols, AstNumList rows, String src, Session ses) {
    // Check for needing to copy before updating
    // Handle fast small case
    Vec[] dvecs = dst.vecs();
    long nrows = rows.cnt();
    if( nrows==1 ) {
      long drow = (long)rows.expand()[0];
      for( Vec vec : dvecs )
        vec.set(drow, src);
      return;
    }

    // Handle large case
    Vec[] vecs = ses.copyOnWrite(dst, cols);
    Vec[] vecs2 = new Vec[cols.length]; // Just the selected columns get updated
    for (int i = 0; i < cols.length; i++)
      vecs2[i] = vecs[cols[i]];
    rows.sort();                // Side-effect internal sort; needed for fast row lookup
    new AssignFrameStringScalarTask(rows, src).doAll(vecs2);
  }

  private static class AssignFrameStringScalarTask extends RowSliceTask {
    private String _src;
    private AssignFrameStringScalarTask(AstNumList rows, String src) {
      super(rows);
      _src = src;
    }
    @Override
    void mapChunkSlice(Chunk[] cs, int chkOffset) {
      long start = cs[0].start();
      for (int i = chkOffset; i < cs[0]._len; ++i)
        if (_rows.has(start + i))
          for (Chunk chk : cs)
            chk.set(i, _src);
    }
  }

  // Boolean assignment with a scalar
  private void assign_frame_scalar(Frame dst, final int[] cols, Frame rows, final double src, Session ses) {
    // TODO: COW without materializing vec and depending on assign_frame_frame
    Frame src2 = new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk bool = cs[cs.length - 1];
        for (int i = 0; i < cs[0]._len; ++i) {
          int nc = 0;
          if (bool.at8(i) == 1)
            for (int ignored : cols) ncs[nc++].addNum(src);
          else
            for (int c : cols) ncs[nc++].addNum(cs[c].atd(i));
        }
      }
    }.doAll(cols.length, Vec.T_NUM, new Frame(dst).add(rows)).outputFrame();
    assign_frame_frame(dst, cols, new AstNumList(0, dst.numRows()), src2, ses);
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

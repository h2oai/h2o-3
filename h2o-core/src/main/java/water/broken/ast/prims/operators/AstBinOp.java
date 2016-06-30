package water.rapids.ast.prims.operators;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.*;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.vals.ValRow;
import water.util.ArrayUtils;

import java.util.Arrays;

/**
 * Binary operator.
 * Subclasses auto-widen between scalars and Frames, and have exactly two arguments
 */
abstract public class AstBinOp extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"leftArg", "rightArg"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val left = stk.track(asts[1].exec(env));
    Val rite = stk.track(asts[2].exec(env));
    return prim_apply(left, rite);
  }

  public Val prim_apply(Val left, Val rite) {
    switch (left.type()) {
      case Val.NUM:
        final double dlf = left.getNum();
        switch (rite.type()) {
          case Val.NUM:
            return new ValNum(op(dlf, rite.getNum()));
          case Val.NUMS:
            return new ValNum(op(dlf, rite.getNums()[0]));
          case Val.FRM:
            return scalar_op_frame(dlf, rite.getFrame());
          case Val.ROW:
            double[] lft = new double[rite.getRow().length];
            Arrays.fill(lft, dlf);
            return row_op_row(lft, rite.getRow(), ((ValRow) rite).getNames());
          case Val.STR:
            throw H2O.unimpl();
          case Val.STRS:
            throw H2O.unimpl();
          default:
            throw H2O.unimpl();
        }

      case Val.NUMS:
        final double ddlf = left.getNums()[0];
        switch (rite.type()) {
          case Val.NUM:
            return new ValNum(op(ddlf, rite.getNum()));
          case Val.NUMS:
            return new ValNum(op(ddlf, rite.getNums()[0]));
          case Val.FRM:
            return scalar_op_frame(ddlf, rite.getFrame());
          case Val.ROW:
            double[] lft = new double[rite.getRow().length];
            Arrays.fill(lft, ddlf);
            return row_op_row(lft, rite.getRow(), ((ValRow) rite).getNames());
          case Val.STR:
            throw H2O.unimpl();
          case Val.STRS:
            throw H2O.unimpl();
          default:
            throw H2O.unimpl();
        }

      case Val.FRM:
        Frame flf = left.getFrame();
        switch (rite.type()) {
          case Val.NUM:
            return frame_op_scalar(flf, rite.getNum());
          case Val.NUMS:
            return frame_op_scalar(flf, rite.getNums()[0]);
          case Val.STR:
            return frame_op_scalar(flf, rite.getStr());
          case Val.STRS:
            return frame_op_scalar(flf, rite.getStrs()[0]);
          case Val.FRM:
            return frame_op_frame(flf, rite.getFrame());
          default:
            throw H2O.unimpl();
        }

      case Val.STR:
        String slf = left.getStr();
        switch (rite.type()) {
          case Val.NUM:
            throw H2O.unimpl();
          case Val.NUMS:
            throw H2O.unimpl();
          case Val.STR:
            throw H2O.unimpl();
          case Val.STRS:
            throw H2O.unimpl();
          case Val.FRM:
            return scalar_op_frame(slf, rite.getFrame());
          default:
            throw H2O.unimpl();
        }

      case Val.STRS:
        String sslf = left.getStrs()[0];
        switch (rite.type()) {
          case Val.NUM:
            throw H2O.unimpl();
          case Val.NUMS:
            throw H2O.unimpl();
          case Val.STR:
            throw H2O.unimpl();
          case Val.STRS:
            throw H2O.unimpl();
          case Val.FRM:
            return scalar_op_frame(sslf, rite.getFrame());
          default:
            throw H2O.unimpl();
        }

      case Val.ROW:
        double dslf[] = left.getRow();
        switch (rite.type()) {
          case Val.NUM:
            double[] right = new double[dslf.length];
            Arrays.fill(right, rite.getNum());
            return row_op_row(dslf, right, ((ValRow) left).getNames());
          case Val.ROW:
            return row_op_row(dslf, rite.getRow(), ((ValRow) rite).getNames());
          case Val.FRM:
            return row_op_row(dslf, rite.getRow(), rite.getFrame().names());
          default:
            throw H2O.unimpl();
        }

      default:
        throw H2O.unimpl();
    }
  }

  /**
   * Override to express a basic math primitive
   */
  public abstract double op(double l, double r);

  public double str_op(BufferedString l, BufferedString r) {
    throw H2O.fail();
  }

  /**
   * Auto-widen the scalar to every element of the frame
   */
  private ValFrame scalar_op_frame(final double d, Frame fr) {
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        for (int c = 0; c < chks.length; c++) {
          Chunk chk = chks[c];
          NewChunk cres = cress[c];
          for (int i = 0; i < chk._len; i++)
            cres.addNum(op(d, chk.atd(i)));
        }
      }
    }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame(fr._names, null);
    return cleanCategorical(fr, res); // Cleanup categorical misuse
  }

  /**
   * Auto-widen the scalar to every element of the frame
   */
  public ValFrame frame_op_scalar(Frame fr, final double d) {
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        for (int c = 0; c < chks.length; c++) {
          Chunk chk = chks[c];
          NewChunk cres = cress[c];
          for (int i = 0; i < chk._len; i++)
            cres.addNum(op(chk.atd(i), d));
        }
      }
    }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame(fr._names, null);
    return cleanCategorical(fr, res); // Cleanup categorical misuse
  }

  // Ops do not make sense on categoricals, except EQ/NE; flip such ops to NAs
  private ValFrame cleanCategorical(Frame oldfr, Frame newfr) {
    final boolean categoricalOK = categoricalOK();
    final Vec oldvecs[] = oldfr.vecs();
    final Vec newvecs[] = newfr.vecs();
    for (int i = 0; i < oldvecs.length; i++)
      if ((oldvecs[i].isCategorical() && !categoricalOK)) // categorical are OK (op is EQ/NE)
        newvecs[i] = newvecs[i].makeCon(Double.NaN);
    return new ValFrame(newfr);
  }

  /**
   * Auto-widen the scalar to every element of the frame
   */
  private ValFrame frame_op_scalar(Frame fr, final String str) {
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        BufferedString vstr = new BufferedString();
        for (int c = 0; c < chks.length; c++) {
          Chunk chk = chks[c];
          NewChunk cres = cress[c];
          Vec vec = chk.vec();
          // String Vectors: apply str_op as BufferedStrings to all elements
          if (vec.isString()) {
            final BufferedString conStr = new BufferedString(str);
            for (int i = 0; i < chk._len; i++)
              cres.addNum(str_op(chk.atStr(vstr, i), conStr));
          } else if (vec.isCategorical()) {
            // categorical Vectors: convert string to domain value; apply op (not
            // str_op).  Not sure what the "right" behavior here is, can
            // easily argue that should instead apply str_op to the categorical
            // string domain value - except that this whole operation only
            // makes sense for EQ/NE, and is much faster when just comparing
            // doubles vs comparing strings.  Note that if the string is not
            // part of the categorical domain, the find op returns -1 which is never
            // equal to any categorical dense integer (which are always 0+).
            final double d = (double) ArrayUtils.find(vec.domain(), str);
            for (int i = 0; i < chk._len; i++)
              cres.addNum(op(chk.atd(i), d));
          } else { // mixing string and numeric
            final double d = op(1, 2); // false or true only
            for (int i = 0; i < chk._len; i++)
              cres.addNum(d);
          }
        }
      }
    }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame(fr._names, null);
    return new ValFrame(res);
  }

  /**
   * Auto-widen the scalar to every element of the frame
   */
  private ValFrame scalar_op_frame(final String str, Frame fr) {
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        BufferedString vstr = new BufferedString();
        for (int c = 0; c < chks.length; c++) {
          Chunk chk = chks[c];
          NewChunk cres = cress[c];
          Vec vec = chk.vec();
          // String Vectors: apply str_op as BufferedStrings to all elements
          if (vec.isString()) {
            final BufferedString conStr = new BufferedString(str);
            for (int i = 0; i < chk._len; i++)
              cres.addNum(str_op(conStr, chk.atStr(vstr, i)));
          } else if (vec.isCategorical()) {
            // categorical Vectors: convert string to domain value; apply op (not
            // str_op).  Not sure what the "right" behavior here is, can
            // easily argue that should instead apply str_op to the categorical
            // string domain value - except that this whole operation only
            // makes sense for EQ/NE, and is much faster when just comparing
            // doubles vs comparing strings.
            final double d = (double) ArrayUtils.find(vec.domain(), str);
            for (int i = 0; i < chk._len; i++)
              cres.addNum(op(d, chk.atd(i)));
          } else { // mixing string and numeric
            final double d = op(1, 2); // false or true only
            for (int i = 0; i < chk._len; i++)
              cres.addNum(d);
          }
        }
      }
    }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame(fr._names, null);
    return new ValFrame(res);
  }

  /**
   * Auto-widen: If one frame has only 1 column, auto-widen that 1 column to
   * the rest.  Otherwise the frames must have the same column count, and
   * auto-widen element-by-element.  Short-cut if one frame has zero
   * columns.
   */
  private ValFrame frame_op_frame(Frame lf, Frame rt) {
    if (lf.numRows() != rt.numRows()) {
      // special case for broadcasting a single row of data across a frame
      if (lf.numRows() == 1 || rt.numRows() == 1) {
        if (lf.numCols() != rt.numCols())
          throw new IllegalArgumentException("Frames must have same columns, found " + lf.numCols() + " columns and " + rt.numCols() + " columns.");
        return frame_op_row(lf, rt);
      } else
        throw new IllegalArgumentException("Frames must have same rows, found " + lf.numRows() + " rows and " + rt.numRows() + " rows.");
    }
    if (lf.numCols() == 0) return new ValFrame(lf);
    if (rt.numCols() == 0) return new ValFrame(rt);
    if (lf.numCols() == 1 && rt.numCols() > 1) return vec_op_frame(lf.vecs()[0], rt);
    if (rt.numCols() == 1 && lf.numCols() > 1) return frame_op_vec(lf, rt.vecs()[0]);
    if (lf.numCols() != rt.numCols())
      throw new IllegalArgumentException("Frames must have same columns, found " + lf.numCols() + " columns and " + rt.numCols() + " columns.");

    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        BufferedString lfstr = new BufferedString();
        BufferedString rtstr = new BufferedString();
        assert (cress.length << 1) == chks.length;
        for (int c = 0; c < cress.length; c++) {
          Chunk clf = chks[c];
          Chunk crt = chks[c + cress.length];
          NewChunk cres = cress[c];
          if (clf.vec().isString())
            for (int i = 0; i < clf._len; i++)
              cres.addNum(str_op(clf.atStr(lfstr, i), crt.atStr(rtstr, i)));
          else
            for (int i = 0; i < clf._len; i++)
              cres.addNum(op(clf.atd(i), crt.atd(i)));
        }
      }
    }.doAll(lf.numCols(), Vec.T_NUM, new Frame(lf).add(rt)).outputFrame(lf._names, null);
    return cleanCategorical(lf, res); // Cleanup categorical misuse
  }

  private ValFrame frame_op_row(Frame lf, Frame row) {
    final double[] rawRow = new double[row.numCols()];
    for (int i = 0; i < rawRow.length; ++i)
      rawRow[i] = row.vec(i).isNumeric() ? row.vec(i).at(0) : Double.NaN; // is numeric, if not then NaN
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        for (int c = 0; c < cress.length; c++) {
          Chunk clf = chks[c];
          NewChunk cres = cress[c];
          for (int r = 0; r < clf._len; ++r) {
            if (clf.vec().isString())
              cres.addNum(Double.NaN); // TODO: improve
            else
              cres.addNum(op(clf.atd(r), rawRow[c]));
          }
        }
      }
    }.doAll(lf.numCols(), Vec.T_NUM, lf).outputFrame(lf._names, null);
    return cleanCategorical(lf, res);
  }

  private ValRow row_op_row(double[] lf, double[] rt, String[] names) {
    double[] res = new double[lf.length];
    for (int i = 0; i < lf.length; i++)
      res[i] = op(lf[i], rt[i]);
    return new ValRow(res, names);
  }

  private ValFrame vec_op_frame(Vec vec, Frame fr) {
    // Already checked for same rows, non-zero frame
    Frame rt = new Frame(fr);
    rt.add("", vec);
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        assert cress.length == chks.length - 1;
        Chunk clf = chks[cress.length];
        for (int c = 0; c < cress.length; c++) {
          Chunk crt = chks[c];
          NewChunk cres = cress[c];
          for (int i = 0; i < clf._len; i++)
            cres.addNum(op(clf.atd(i), crt.atd(i)));
        }
      }
    }.doAll(fr.numCols(), Vec.T_NUM, rt).outputFrame(fr._names, null);
    return cleanCategorical(fr, res); // Cleanup categorical misuse
  }

  private ValFrame frame_op_vec(Frame fr, Vec vec) {
    // Already checked for same rows, non-zero frame
    Frame lf = new Frame(fr);
    lf.add("", vec);
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        assert cress.length == chks.length - 1;
        Chunk crt = chks[cress.length];
        for (int c = 0; c < cress.length; c++) {
          Chunk clf = chks[c];
          NewChunk cres = cress[c];
          for (int i = 0; i < clf._len; i++)
            cres.addNum(op(clf.atd(i), crt.atd(i)));
        }
      }
    }.doAll(fr.numCols(), Vec.T_NUM, lf).outputFrame(fr._names, null);
    return cleanCategorical(fr, res); // Cleanup categorical misuse
  }

  // Make sense to run this OP on an enm?
  public boolean categoricalOK() {
    return false;
  }
}


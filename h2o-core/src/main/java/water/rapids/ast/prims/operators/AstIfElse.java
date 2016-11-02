package water.rapids.ast.prims.operators;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.*;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.vals.ValRow;
import water.util.ArrayUtils;
import water.util.VecUtils;

import java.util.Arrays;


/**
 * If-Else -- ternary conditional operator, equivalent of "?:" in C++ and Java.
 * <p/>
 * "NaNs poison".  If the test is a NaN, evaluate neither side and return a NaN
 * <p/>
 * "Frames poison".  If the test is a Frame, both sides are evaluated and selected between according to the test.
 * The result is a Frame.  All Frames must be compatible, and scalars and 1-column Frames are widened to match the
 * widest frame.  NaN test values produce NaN results.
 * <p/>
 * If the test is a scalar, then only the returned side is evaluated.  If both sides are scalars or frames, then the
 * evaluated result is returned.  The unevaluated side is not checked for being a compatible frame.  It is an error
 * if one side is typed as a scalar and the other as a Frame.
 */
public class AstIfElse extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"test", "true", "false"};
  }

  /* (ifelse test true false) */
  @Override
  public int nargs() {
    return 1 + 3;
  }

  public String str() {
    return "ifelse";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val val = stk.track(asts[1].exec(env));

    if (val.isNum()) {         // Scalar test, scalar result
      double d = val.getNum();
      if (Double.isNaN(d)) return new ValNum(Double.NaN);
      Val res = stk.track(asts[d == 0 ? 3 : 2].exec(env)); // exec only 1 of false and true
      return res.isFrame() ? new ValNum(res.getFrame().vec(0).at(0)) : res;
    }

    // Frame test.  Frame result.
    if (val.type() == Val.ROW)
      return row_ifelse((ValRow) val, asts[2].exec(env), asts[3].exec(env));
    Frame tst = val.getFrame();

    // If all zero's, return false and never execute true.
    Frame fr = new Frame(tst);
    Val tval = null;
    for (Vec vec : tst.vecs())
      if (vec.min() != 0 || vec.max() != 0) {
        tval = exec_check(env, stk, tst, asts[2], fr);
        break;
      }
    final boolean has_tfr = tval != null && tval.isFrame();
    final String ts = (tval != null && tval.isStr()) ? tval.getStr() : null;
    final double td = (tval != null && tval.isNum()) ? tval.getNum() : Double.NaN;
    final int[] tsIntMap = new int[tst.numCols()];

    // If all nonzero's (or NA's), then never execute false.
    Val fval = null;
    for (Vec vec : tst.vecs())
      if (vec.nzCnt() + vec.naCnt() < vec.length()) {
        fval = exec_check(env, stk, tst, asts[3], fr);
        break;
      }
    final boolean has_ffr = fval != null && fval.isFrame();
    final String fs = (fval != null && fval.isStr()) ? fval.getStr() : null;
    final double fd = (fval != null && fval.isNum()) ? fval.getNum() : Double.NaN;
    final int[] fsIntMap = new int[tst.numCols()];

    String[][] domains = null;
    final int[][] maps = new int[tst.numCols()][];
    if (fs != null || ts != null) { // time to build domains...
      domains = new String[tst.numCols()][];
      if (fs != null && ts != null) {
        for (int i = 0; i < tst.numCols(); ++i) {
          domains[i] = new String[]{fs, ts}; // false => 0; truth => 1
          fsIntMap[i] = 0;
          tsIntMap[i] = 1;
        }
      } else if (ts != null) {
        for (int i = 0; i < tst.numCols(); ++i) {
          if (has_ffr) {
            Vec v = fr.vec(i + tst.numCols() + (has_tfr ? tst.numCols() : 0));
            if (!v.isCategorical())
              throw H2O.unimpl("Column is not categorical.");
            String[] dom = Arrays.copyOf(v.domain(), v.domain().length + 1);
            dom[dom.length - 1] = ts;
            Arrays.sort(dom);
            maps[i] = computeMap(v.domain(), dom);
            tsIntMap[i] = ArrayUtils.find(dom, ts);
            domains[i] = dom;
          } else throw H2O.unimpl();
        }
      } else { // fs!=null
        for (int i = 0; i < tst.numCols(); ++i) {
          if (has_tfr) {
            Vec v = fr.vec(i + tst.numCols() + (has_ffr ? tst.numCols() : 0));
            if (!v.isCategorical())
              throw H2O.unimpl("Column is not categorical.");
            String[] dom = Arrays.copyOf(v.domain(), v.domain().length + 1);
            dom[dom.length - 1] = fs;
            Arrays.sort(dom);
            maps[i] = computeMap(v.domain(), dom);
            fsIntMap[i] = ArrayUtils.find(dom, fs);
            domains[i] = dom;
          } else throw H2O.unimpl();
        }
      }
    }

    // Now pick from left-or-right in the new frame
    Frame res = new MRTask() {
      @Override
      public void map(Chunk chks[], NewChunk nchks[]) {
        assert nchks.length + (has_tfr ? nchks.length : 0) + (has_ffr ? nchks.length : 0) == chks.length;
        for (int i = 0; i < nchks.length; i++) {
          Chunk ctst = chks[i];
          NewChunk res = nchks[i];
          for (int row = 0; row < ctst._len; row++) {
            double d;
            if (ctst.isNA(row)) d = Double.NaN;
            else if (ctst.atd(row) == 0) d = has_ffr
                ? domainMap(chks[i + nchks.length + (has_tfr ? nchks.length : 0)].atd(row), maps[i])
                : fs != null ? fsIntMap[i] : fd;
            else d = has_tfr
                  ? domainMap(chks[i + nchks.length].atd(row), maps[i])
                  : ts != null ? tsIntMap[i] : td;
            res.addNum(d);
          }
        }
      }
    }.doAll(tst.numCols(), Vec.T_NUM, fr).outputFrame(null, domains);

    // flatten domains since they may be larger than needed
    if (domains != null) {
      for (int i = 0; i < res.numCols(); ++i) {
        if (res.vec(i).domain() != null) {
          final long[] dom = new VecUtils.CollectDomainFast((int) res.vec(i).max()).doAll(res.vec(i)).domain();
          String[] newDomain = new String[dom.length];
          for (int l = 0; l < dom.length; ++l)
            newDomain[l] = res.vec(i).domain()[(int) dom[l]];
          new MRTask() {
            @Override
            public void map(Chunk c) {
              for (int i = 0; i < c._len; ++i) {
                if (!c.isNA(i))
                  c.set(i, ArrayUtils.find(dom, c.at8(i)));
              }
            }
          }.doAll(res.vec(i));
          res.vec(i).setDomain(newDomain); // needs a DKVput?
        }
      }
    }
    return new ValFrame(res);
  }

  private static double domainMap(double d, int[] maps) {
    if (maps != null && d == (int) d && (0 <= d && d < maps.length)) return maps[(int) d];
    return d;
  }

  private static int[] computeMap(String[] from, String[] to) {
    int[] map = new int[from.length];
    for (int i = 0; i < from.length; ++i)
      map[i] = ArrayUtils.find(to, from[i]);
    return map;
  }

  Val exec_check(Env env, Env.StackHelp stk, Frame tst, AstRoot ast, Frame xfr) {
    Val val = ast.exec(env);
    if (val.isFrame()) {
      Frame fr = stk.track(val).getFrame();
      if (tst.numCols() != fr.numCols() || tst.numRows() != fr.numRows())
        throw new IllegalArgumentException("ifelse test frame and other frames must match dimensions, found " + tst + " and " + fr);
      xfr.add(fr);
    }
    return val;
  }

  ValRow row_ifelse(ValRow tst, Val yes, Val no) {
    double[] test = tst.getRow();
    double[] True;
    double[] False;
    if (!(yes.isRow() || no.isRow())) throw H2O.unimpl();
    switch (yes.type()) {
      case Val.NUM:
        True = new double[]{yes.getNum()};
        break;
      case Val.ROW:
        True = yes.getRow();
        break;
      default:
        throw H2O.unimpl("row ifelse unimpl: " + yes.getClass());
    }
    switch (no.type()) {
      case Val.NUM:
        False = new double[]{no.getNum()};
        break;
      case Val.ROW:
        False = no.getRow();
        break;
      default:
        throw H2O.unimpl("row ifelse unimplL " + no.getClass());
    }
    double[] ds = new double[test.length];
    String[] ns = new String[test.length];
    for (int i = 0; i < test.length; ++i) {
      ns[i] = "C" + (i + 1);
      if (Double.isNaN(test[i])) ds[i] = Double.NaN;
      else ds[i] = test[i] == 0 ? False[i] : True[i];
    }
    return new ValRow(ds, ns);
  }
}


package water.rapids.ast.prims.search;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.ast.params.AstStr;
import water.rapids.ast.params.AstStrList;
import water.util.MathUtils;

import java.util.Arrays;

/**
 */
public class AstMatch extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "table", "nomatch", "incomparables"};
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // (match fr table nomatch incomps)

  @Override
  public String str() {
    return "match";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1 || !fr.anyVec().isCategorical())
      throw new IllegalArgumentException("can only match on a single categorical column.");

    String[] strsTable2 = null;
    double[] dblsTable2 = null;
    if (asts[2] instanceof AstNumList) dblsTable2 = ((AstNumList) asts[2]).sort().expand();
    else if (asts[2] instanceof AstNum) dblsTable2 = new double[]{asts[2].exec(env).getNum()};
    else if (asts[2] instanceof AstStrList) {
      strsTable2 = ((AstStrList) asts[2])._strs;
      Arrays.sort(strsTable2);
    } else if (asts[2] instanceof AstStr) strsTable2 = new String[]{asts[2].exec(env).getStr()};
    else throw new IllegalArgumentException("Expected numbers/strings. Got: " + asts[2].getClass());

    final double nomatch = asts[3].exec(env).getNum();

    final String[] strsTable = strsTable2;
    final double[] dblsTable = dblsTable2;

    Frame rez = new MRTask() {
      @Override
      public void map(Chunk c, NewChunk n) {
        String[] domain = c.vec().domain();
        double x;
        int rows = c._len;
        for (int r = 0; r < rows; ++r) {
          x = c.isNA(r) ? nomatch : (strsTable == null ? in(dblsTable, c.atd(r), nomatch) : in(strsTable, domain[(int) c.at8(r)], nomatch));
          n.addNum(x);
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, fr.anyVec()).outputFrame();
    return new ValFrame(rez);
  }

  private static double in(String[] matches, String s, double nomatch) {
    return Arrays.binarySearch(matches, s) >= 0 ? 1 : nomatch;
  }

  private static double in(double[] matches, double d, double nomatch) {
    return binarySearchDoublesUlp(matches, 0, matches.length, d) >= 0 ? 1 : nomatch;
  }

  private static int binarySearchDoublesUlp(double[] a, int from, int to, double key) {
    int lo = from;
    int hi = to - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      double midVal = a[mid];
      if (MathUtils.equalsWithinOneSmallUlp(midVal, key)) return mid;
      if (midVal < key) lo = mid + 1;
      else if (midVal > key) hi = mid - 1;
      else {
        long midBits = Double.doubleToLongBits(midVal);
        long keyBits = Double.doubleToLongBits(key);
        if (midBits == keyBits) return mid;
        else if (midBits < keyBits) lo = mid + 1;
        else hi = mid - 1;
      }
    }
    return -(lo + 1);  // key not found.
  }
}

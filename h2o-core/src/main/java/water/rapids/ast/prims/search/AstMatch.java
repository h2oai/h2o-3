package water.rapids.ast.prims.search;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
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
    return new String[]{"ary", "table", "nomatch", "incomparables", "indexes"};
  }

  @Override
  public int nargs() {
    return 1 + 5;
  } // (match fr table nomatch incomps indexes)

  @Override
  public String str() {
    return "match";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    if ((fr.numCols() != 1) || ! (fr.anyVec().isCategorical() || fr.anyVec().isString()))
      throw new IllegalArgumentException("can only match on a single categorical/string column.");

    final MRTask<?> matchTask;
    double noMatch = asts[3].exec(env).getNum();
    boolean indexes = asts[5].exec(env).getBool();
    

    if (asts[2] instanceof AstNumList) {
      matchTask = new NumMatchTask(((AstNumList) asts[2]).sort().expand(), noMatch, indexes);
    }  else if (asts[2] instanceof AstNum) {
      matchTask = new NumMatchTask(new double[]{asts[2].exec(env).getNum()}, noMatch, indexes);
    } else if (asts[2] instanceof AstStrList) {
      String[] values = ((AstStrList) asts[2])._strs;
      Arrays.sort(values);
      matchTask = fr.anyVec().isString() ? new StrMatchTask(values, noMatch, indexes) : 
              new CatMatchTask(values, noMatch, indexes);
    } else if (asts[2] instanceof AstStr) {
      String[] values = new String[]{asts[2].exec(env).getStr()};
      matchTask = fr.anyVec().isString() ? new StrMatchTask(values, noMatch, indexes) : 
              new CatMatchTask(values, noMatch, indexes);
    } else
      throw new IllegalArgumentException("Expected numbers/strings. Got: " + asts[2].getClass());

    Frame result = matchTask.doAll(Vec.T_NUM, fr.anyVec()).outputFrame();
    return new ValFrame(result);
  }

  private static class StrMatchTask extends MRTask<CatMatchTask> {
    String[] _values;
    double _noMatch;
    boolean _indexes;

    StrMatchTask(String[] values, double noMatch, boolean indexes) {
      _values = values;
      _noMatch = noMatch;
      _indexes = indexes;
    }
    @Override
    public void map(Chunk c, NewChunk nc) {
      BufferedString bs = new BufferedString();
      int rows = c._len;
      for (int r = 0; r < rows; r++) {
        double x = c.isNA(r) ? _noMatch : in(_values, c.atStr(bs, r).toString(), _noMatch, _indexes);
        nc.addNum(x);
      }
    }
  }

  private static class CatMatchTask extends MRTask<CatMatchTask> {
    String[] _values;
    int[] _firstMatchRow;
    double _noMatch;
    boolean _indexes;
 
    CatMatchTask(String[] values, double noMatch, boolean indexes) {
      _values = values;
      _noMatch = noMatch;
      _indexes = indexes;
      _firstMatchRow = new int[values.length];
    }

    @Override
    public void map(Chunk c, NewChunk nc) {
      String[] domain = c.vec().domain();
      int rows = c._len;
      for (int r = 0; r < rows; r++) {
        double x = c.isNA(r) ? _noMatch : in(_values, domain[(int) c.at8(r)], _noMatch, _indexes);
        nc.addNum(x);
      }
    }
  }

  private static class NumMatchTask extends MRTask<CatMatchTask> {
    double[] _values;
    double _noMatch;
    boolean _indexes;

    NumMatchTask(double[] values, double noMatch, boolean indexes) {
      _values = values;
      _noMatch = noMatch;
      _indexes = indexes;
    }
 
    @Override
    public void map(Chunk c, NewChunk nc) {
      int rows = c._len;
      for (int r = 0; r < rows; r++) {
        double x = c.isNA(r) ? _noMatch : in(_values, c.atd(r), _noMatch, _indexes);
        nc.addNum(x);
      }
    }
  }

  private static double in(String[] matches, String s, double nomatch, boolean indexes) {
    int match = Arrays.binarySearch(matches, s);
    return match >= 0 ? indexes ? match : 1 : nomatch;
  }

  private static double in(double[] matches, double d, double nomatch, boolean indexes) {
    int match = binarySearchDoublesUlp(matches, 0, matches.length, d);
    return match >= 0 ? indexes ? match : 1 : nomatch;
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

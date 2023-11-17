package water.rapids.ast.prims.search;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.ast.params.*;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.IcedHashMap;
import water.util.MathUtils;

import java.util.Arrays;

/**
 */
public class AstMatch extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "table", "nomatch", "start_index"};
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // (match fr table nomatch start_index)

  @Override
  public String str() {
    return "match";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if(fr.anyVec() == null){
      throw new IllegalArgumentException("Expected frame with one vector. Got empty frame.");
    }

    final MRTask<?> matchTask;
    
    // nomatch can be NaN or number, Nan is default
    double noMatch;
    if (asts[3] instanceof AstNum) {
      noMatch = asts[3].exec(env).getNum();
    } else if (asts[3] instanceof AstId && ((asts[3]).str().equals("NA") || (asts[3]).str().equals("nan"))){
      noMatch = Double.NaN;
    } else {
      throw new IllegalArgumentException("Expected number or 'NA' or 'nan'. Got: " + asts[3]);
    }
    
    // start index is 1 by default
    int startIndex;
    if (asts[4] instanceof AstNum) {
      startIndex = (int) asts[4].exec(env).getNum();
      if (startIndex < 0) {
        throw new IllegalArgumentException("Expected number >= 0. Got: " + asts[4].getClass());
      }
    } else {
      throw new IllegalArgumentException("Expected number. Got: " + asts[4].getClass());
    }
    
    if (asts[2] instanceof AstNumList) {
      if(fr.anyVec().isString()){
        throw new IllegalArgumentException("Input vector is string and has string domain. Got numeric match values.");
      }
      matchTask = new NumMatchTask(((AstNumList) asts[2]).expand(), noMatch, startIndex);
    }  else if (asts[2] instanceof AstNum) {
      if(fr.anyVec().isString()){
        throw new IllegalArgumentException("Input vector is string and has string domain. Got numeric match values.");
      }
      matchTask = new NumMatchTask(new double[]{asts[2].exec(env).getNum()}, noMatch, startIndex);
    } else if (asts[2] instanceof AstStrList) {
      if(fr.anyVec().isNumeric()){
        throw new IllegalArgumentException("Input vector is numeric and has no domain.");
      }
      String[] values = ((AstStrList) asts[2])._strs;
      matchTask = fr.anyVec().isString() ? new StrMatchTask(values, noMatch, startIndex) : 
              new CatMatchTask(values, noMatch, startIndex);
    } else if (asts[2] instanceof AstStr) {
      String[] values = new String[]{asts[2].exec(env).getStr()};
      if(fr.anyVec().isNumeric()){
        throw new IllegalArgumentException("Input vector is numeric and has no domain.");
      }
      matchTask = fr.anyVec().isString() ? new StrMatchTask(values, noMatch, startIndex) : 
              new CatMatchTask(values, noMatch, startIndex);
    } else
      throw new IllegalArgumentException("Expected numbers/strings. Got: " + asts[2].getClass());

    Frame result = matchTask.doAll(Vec.T_NUM, fr.anyVec()).outputFrame();
    return new ValFrame(result);
  }

  private static class StrMatchTask extends MRTask<CatMatchTask> {
    String[] _values;
    double _noMatch;
    int _startIndex;
    IcedHashMap<String, Integer> _matchesIndexes;

    StrMatchTask(String[] values, double noMatch, int indexes) {
      _values = values;
      _noMatch = noMatch;
      _startIndex = indexes;
      _matchesIndexes = new IcedHashMap<>();
    }
    @Override
    public void map(Chunk c, NewChunk nc) {
      BufferedString bs = new BufferedString();
      int rows = c._len;
      for (int r = 0; r < rows; r++) {
        double x = c.isNA(r) ? _noMatch : in(_matchesIndexes, _values, c.atStr(bs, r).toString(), _noMatch, _startIndex);
        nc.addNum(x);
      }
    }
  }

  private static class CatMatchTask extends MRTask<CatMatchTask> {
    String[] _values;
    int[] _firstMatchRow;
    double _noMatch;
    int _startIndex;
    IcedHashMap<String, Integer> _matchesIndexes;
    
 
    CatMatchTask(String[] values, double noMatch, int startIndex) {
      _values = values;
      _noMatch = noMatch;
      _startIndex = startIndex;
      _firstMatchRow = new int[values.length];
      _matchesIndexes = new IcedHashMap<>();
    }

    @Override
    public void map(Chunk c, NewChunk nc) {
      String[] domain = c.vec().domain();
      int rows = c._len;
      for (int r = 0; r < rows; r++) {
        double x = c.isNA(r) ? _noMatch : in(_matchesIndexes, _values, domain[(int) c.at8(r)], _noMatch, _startIndex);
        nc.addNum(x);
      }
    }
  }

  private static class NumMatchTask extends MRTask<CatMatchTask> {
    double[] _values;
    double _noMatch;
    int _startIndex;
    IcedHashMap<Double, Integer> _matchesIndexes;

    NumMatchTask(double[] values, double noMatch, int startIndex) {
      _values = values;
      _noMatch = noMatch;
      _startIndex = startIndex;
      _matchesIndexes = new IcedHashMap<>();
    }
 
    @Override
    public void map(Chunk c, NewChunk nc) {
      int rows = c._len;
      for (int r = 0; r < rows; r++) {
        double x = c.isNA(r) ? _noMatch : in(_matchesIndexes, _values, c.atd(r), _noMatch, _startIndex);
        nc.addNum(x);
      }
    }
  }

  private static double in(IcedHashMap<String, Integer> matchesIndexes, String[] matches, String s, double noMatch, int startIndex) {
    Integer mapResult = matchesIndexes.get(s);
    int match;
    if (mapResult == null){
      match = Arrays.binarySearch(matches, s);
      matchesIndexes.put(s, match);
    } else {
      match = mapResult;
    }
    return match >= 0 ? applyStartIndex(match, startIndex) : noMatch;
  }

  private static double in(IcedHashMap<Double, Integer> matchesIndexes, double[] matches, double d, double noMatch, int startIndex) {
    Integer mapResult = matchesIndexes.get(d);
    int match;
    if (mapResult == null){
      match = binarySearchDoublesUlp(matches, 0, matches.length, d);
      matchesIndexes.put(d, match);
    } else {
      match = mapResult;
    }
    return match >= 0 ? applyStartIndex(match, startIndex) : noMatch;
  }
  
  private static double applyStartIndex(double value, int startIndex) {
    assert startIndex >= 0;
    return value + startIndex;
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

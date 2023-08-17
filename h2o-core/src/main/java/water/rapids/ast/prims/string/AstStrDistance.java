package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.comparison.string.StringComparator;
import water.util.comparison.string.StringComparatorFactory;

/**
 * Calculates string distances between elements of two frames
 */
public class AstStrDistance extends AstPrimitive {

  @Override
  public String[] args() {
    return new String[]{"ary_x", "ary_y", "measure", "compare_empty"};
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // (strDistance x y measure compare_empty)

  @Override
  public String str() {
    return "strDistance";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame frX = stk.track(asts[1].exec(env)).getFrame();
    Frame frY = stk.track(asts[2].exec(env)).getFrame();
    String measure = asts[3].exec(env).getStr();
    boolean compareEmpty = asts[4].exec(env).getNum() == 1;
    if ((frX.numCols() != frY.numCols()) || (frX.numRows() != frY.numRows()))
      throw new IllegalArgumentException("strDistance() requires the frames to have the same number of columns and rows.");
    for (int i = 0; i < frX.numCols(); i++)
      if (! (isCharacterType(frX.vec(i)) && isCharacterType(frY.vec(i))))
        throw new IllegalArgumentException("Types of columns of both frames need to be String/Factor");
    // make sure that name of the comparator comparator method is correct and it can be constructed
    StringComparatorFactory.makeComparator(measure);

    byte[] outputTypes = new byte[frX.numCols()];
    Vec[] vecs = new Vec[frX.numCols() * 2];
    for (int i = 0; i < outputTypes.length; i++) {
      outputTypes[i] = Vec.T_NUM;
      vecs[i] = frX.vec(i);
      vecs[i + outputTypes.length] = frY.vec(i);
    }

    Frame distFr = new StringDistanceComparator(measure, compareEmpty).doAll(outputTypes, vecs).outputFrame();
    return new ValFrame(distFr);
  }

  private static boolean isCharacterType(Vec v) {
    return v.get_type() == Vec.T_STR || v.get_type() == Vec.T_CAT;
  }

  private static class StringDistanceComparator extends MRTask<StringDistanceComparator> {
    private final String _measure;
    private final boolean _compareEmpty;
    private StringDistanceComparator(String measure, boolean compareEmpty) {
      _measure = measure;
      _compareEmpty = compareEmpty;
    }
    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
      BufferedString tmpStr = new BufferedString();
      StringComparator cmp = StringComparatorFactory.makeComparator(_measure);
      int N = nc.length;
      assert N * 2 == cs.length;
      for (int i = 0; i < N; i++) {
        Chunk cX = cs[i];
        String[] domainX = _fr.vec(i).domain();
        Chunk cY = cs[i + N];
        String[] domainY = _fr.vec(i + N).domain();
        for (int row = 0; row < cX._len; row++) {
          if (cX.isNA(row) || cY.isNA(row))
            nc[i].addNA();
          else {
            String strX = getString(tmpStr, cX, row, domainX);
            String strY = getString(tmpStr, cY, row, domainY);
            if (!_compareEmpty && (strX.isEmpty() || strY.isEmpty())) {
              nc[i].addNA();
            } else {
              double dist = cmp.compare(strX, strY);
              nc[i].addNum(dist);
            }
          }
        }
      }
    }
    private static String getString(BufferedString tmpStr, Chunk chk, int row, String[] domain) {
      if (domain != null)
        return domain[(int) chk.at8(row)];
      else
        return chk.atStr(tmpStr, row).toString();
    }
  }
  
}

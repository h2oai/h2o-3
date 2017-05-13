package water.rapids.ast.prims.string;

import no.priv.garshol.duke.comparators.*;
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

import no.priv.garshol.duke.Comparator;

/**
 * Calculates string distances between elements of two frames
 */
public class AstStrDistance extends AstPrimitive {

  @Override
  public String[] args() {
    return new String[]{"ary_x", "ary_y", "measure"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (strDistance x y measure)

  @Override
  public String str() {
    return "strDistance";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame frX = stk.track(asts[1].exec(env)).getFrame();
    Frame frY = stk.track(asts[2].exec(env)).getFrame();
    String measure = asts[3].exec(env).getStr();

    if ((frX.numCols() != frY.numCols()) || (frX.numRows() != frY.numRows()))
      throw new IllegalArgumentException("strDistance() requires the frames to have the same number of columns and rows.");
    for (int i = 0; i < frX.numCols(); i++)
      if ((frX.vec(i).get_type() != Vec.T_STR) || (frY.vec(i).get_type() != Vec.T_STR))
        throw new IllegalArgumentException("Types of columns of both frames need to be String");
    // check that comparator name is correct
    makeComparator(measure);

    byte[] outputTypes = new byte[frX.numCols()];
    Vec[] vecs = new Vec[frX.numCols() * 2];
    for (int i = 0; i < outputTypes.length; i++) {
      outputTypes[i] = Vec.T_NUM;
      vecs[i] = frX.vec(i);
      vecs[i + outputTypes.length] = frY.vec(i);
    }

    Frame distFr = new StringDistanceComparator(measure).doAll(outputTypes, vecs).outputFrame();
    return new ValFrame(distFr);
  }

  private static class StringDistanceComparator extends MRTask<StringDistanceComparator> {
    private final String _measure;
    private StringDistanceComparator(String measure) { _measure = measure; }
    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
      BufferedString tmpStr = new BufferedString();
      Comparator cmp = makeComparator(_measure);
      int N = nc.length;
      assert N * 2 == cs.length;
      for (int i = 0; i < N; i++) {
        Chunk cX = cs[i];
        Chunk cY = cs[i + N];
        for (int row = 0; row < cX._len; row++) {
          if (cX.isNA(row) || cY.isNA(row))
            nc[i].addNA();
          else {
            String strX = cX.atStr(tmpStr, row).toString();
            String strY = cY.atStr(tmpStr, row).toString();
            double dist = cmp.compare(strX, strY);
            nc[i].addNum(dist);
          }
        }
      }
    }
  }

  private static Comparator makeComparator(String measure) {
    switch (measure) {
      case "jaccard":
      case "JaccardIndex":
        return new JaccardIndexComparator();
      case "jw":
      case "JaroWinkler":
        return new JaroWinkler();
      case "lv":
      case "Levenshtein":
        return new Levenshtein();
      case "lcs":
      case "LongestCommonSubstring":
        return new LongestCommonSubstring();
      case "qgram":
      case "QGram":
        return new QGramComparator();
      case "soundex":
      case "Soundex":
        return new SoundexComparator();
      default:
        throw new IllegalArgumentException("Unknown comparator: " + measure);
    }
  }

}

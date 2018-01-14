package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

/**
 * Accepts a frame with multiple columns with the elements of type String.
 * Returns a frame with a single column which contains the concatenation of
 * all strings in a given row
 * Note: If a value of an element is NA then empty string is appended
 *
 * Example:
 *
 * Request Frame:
 * "1", "2", "3", "4", "5", "6", "7"
 * "A", "B", "C", "D", "E", "F", "G"
 * "a", "b", "c", "d", "e", "f", "g"
 * Response:
 * "0Aa", "1Bb", "2Cc", "3Dd", "4Ee", "5Ff", "6Gg"
 *
 * Request Frame:
 * null(NA), "1", "2", "3", "4", "5", "6"
 * "A", "B", "C", null(NA), "E", "F", "G"
 * "a", "b", "c", "d",      "e", "f", null(NA)
 * Response:
 * "Aa", "1Bb", "2Cc", "3d", "4Ee", "5Ff", "6G"
 *
 */
public class AstStrConcat extends AstPrimitive {

  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public String str() {
    return "strconcat";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    // Type check
    for (Vec v : fr.vecs())
      if (! v.isString())
        throw new IllegalArgumentException("strconcat() requires a string column. "
                + "Received " + fr.anyVec().get_type_str()
                + ". Please convert column to a string first.");

    // Transform each vec
    Vec result = concat(fr);

    return new ValFrame(new Frame(result));
  }

  private Vec concat(Frame f) {
    return new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk newChk) {
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < cs[0]._len; i++) {
            // Working with rows
            StringBuilder sb = new StringBuilder();
            for  (int j = 0; j < cs.length; j++) {
              String s;
              if(cs[j].isNA(i)) {
                s = "";
              }
              else {
                BufferedString bs = cs[j].atStr(tmpStr, i);
                s = bs.toString();
              }
              sb.append(s);
            }
            String result = sb.toString();
            newChk.addStr(result);
          }
        }
    }.doAll(new byte[]{Vec.T_STR}, f).outputFrame().anyVec();
  }
}
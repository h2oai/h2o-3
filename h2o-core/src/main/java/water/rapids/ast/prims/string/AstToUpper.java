package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.Locale;

/**
 * Accepts a frame with a single string column.
 * Returns a new string column containing the results of the toUpper method on each string in the
 * target column.
 * <p/>
 * toUpper - Converts all of the characters in this String to upper case.
 */
public class AstToUpper extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } //(toupper x)

  @Override
  public String str() {
    return "toupper";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    // Type check
    for (VecAry v : fr.vecs().singleVecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("toupper() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();
    int i = 0;
    for (VecAry v : fr.vecs().singleVecs()) {
      if (v.isCategorical())
        nvs.append(toUpperCategoricalCol(v));
      else
        nvs.append(toUpperStringCol(v));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry toUpperCategoricalCol(VecAry vec) {
    String[] dom = vec.domain().clone();
    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toUpperCase(Locale.ENGLISH);
    return vec.makeCopy(new String[][]{dom});
  }

  private VecAry toUpperStringCol(VecAry vec) {
    return new MRTask() {
      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk._len; i++) {
          if (chk.isNA(i))
            newChk.addNA(0);
          else // Locale.ENGLISH to give the correct results for local insensitive strings
            newChk.addStr(0,chk.atStr(tmpStr, i).toString().toUpperCase(Locale.ENGLISH));
        }
      }

    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().vecs();
  }
}

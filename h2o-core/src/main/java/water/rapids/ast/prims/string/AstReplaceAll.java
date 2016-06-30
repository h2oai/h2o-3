package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.Locale;

/**
 * Accepts a frame with a single string column, a regex pattern string, a replacement substring,
 * and a boolean to indicate whether to ignore the case of the target string.
 * Returns a new string column containing the results of the replaceAll method on each string
 * in the target column.
 * <p/>
 * replaceAll - Replaces each substring of this string that matches the given regular expression
 * with the given replacement.
 */
public class AstReplaceAll extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "pattern", "replacement", "ignore_case"};
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // (sub x pattern replacement ignore.case)

  @Override
  public String str() {
    return "replaceall";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    final String pattern = asts[2].exec(env).getStr();
    final String replacement = asts[3].exec(env).getStr();
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final boolean ignoreCase = asts[4].exec(env).getNum() == 1;

    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("replaceall() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for (Vec v : fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = replaceAllCategoricalCol(v, pattern, replacement, ignoreCase);
      else
        nvs[i] = replaceAllStringCol(v, pattern, replacement, ignoreCase);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec replaceAllCategoricalCol(Vec vec, String pattern, String replacement, boolean ignoreCase) {
    String[] doms = vec.domain().clone();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = ignoreCase
          ? doms[i].toLowerCase(Locale.ENGLISH).replaceAll(pattern, replacement)
          : doms[i].replaceAll(pattern, replacement);

    return vec.makeCopy(doms);
  }

  private Vec replaceAllStringCol(Vec vec, String pat, String rep, boolean ic) {
    final String pattern = pat;
    final String replacement = rep;
    final boolean ignoreCase = ic;
    return new MRTask() {
      @Override
      public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else {
//        if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
//          ((CStrChunk) chk).asciiReplaceAll(newChk);
//        } else { //UTF requires Java string methods for accuracy
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else {
              if (ignoreCase)
                newChk.addStr(chk.atStr(tmpStr, i).toString().toLowerCase(Locale.ENGLISH).replaceAll(pattern, replacement));
              else
                newChk.addStr(chk.atStr(tmpStr, i).toString().replaceAll(pattern, replacement));
            }
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().anyVec();
  }
}

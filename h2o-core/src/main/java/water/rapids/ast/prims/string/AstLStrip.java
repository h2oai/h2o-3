package water.rapids.ast.prims.string;

import org.apache.commons.lang.StringUtils;
import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Accepts a frame with a single string column.
 * Returns a new string column containing the lstripped versions of the strings in the target column.
 * Stripping removes all characters in the strings for the target columns that match the user provided set
 */
public class AstLStrip extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "set"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  }

  @Override
  public String str() {
    return "lstrip";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String set = asts[2].exec(env).getStr();

    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("trim() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for (Vec v : fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = lstripCategoricalCol(v, set);
      else
        nvs[i] = lstripStringCol(v, set);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec lstripCategoricalCol(Vec vec, String set) {
    String[] doms = vec.domain().clone();

    HashMap<String, ArrayList<Integer>> strippedToOldDomainIndices = new HashMap<>();
    String stripped;

    for (int i = 0; i < doms.length; i++) {
      stripped = StringUtils.stripStart(doms[i], set);
      doms[i] = stripped;

      if (!strippedToOldDomainIndices.containsKey(stripped)) {
        ArrayList<Integer> val = new ArrayList<>();
        val.add(i);
        strippedToOldDomainIndices.put(stripped, val);
      } else {
        strippedToOldDomainIndices.get(stripped).add(i);
      }
    }
    //Check for duplicated domains
    if (strippedToOldDomainIndices.size() < doms.length)
      return VecUtils.DomainDedupe.domainDeduper(vec, strippedToOldDomainIndices);

    return vec.makeCopy(doms);
  }

  private Vec lstripStringCol(Vec vec, String set) {
    final String charSet = set;
    return new MRTask() {
      @Override
      public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (((CStrChunk) chk)._isAllASCII && StringUtils.isAsciiPrintable(charSet)) { // fast-path operations
          ((CStrChunk) chk).asciiLStrip(newChk, charSet);
        } else {
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < chk.len(); i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else
              newChk.addStr(StringUtils.stripStart(chk.atStr(tmpStr, i).toString(), charSet));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().anyVec();
  }
}

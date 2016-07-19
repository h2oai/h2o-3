package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNumList;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 */
public class AstSubstring extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "startIndex", "endIndex"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (substring x startIndex endIndex)

  @Override
  public String str() {
    return "substring";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int startIndex = (int) asts[2].exec(env).getNum();
    if (startIndex < 0) startIndex = 0;
    int endIndex = asts[3] instanceof AstNumList ? Integer.MAX_VALUE : (int) asts[3].exec(env).getNum();
    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("substring() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for (Vec v : fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = substringCategoricalCol(v, startIndex, endIndex);
      else
        nvs[i] = substringStringCol(v, startIndex, endIndex);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec substringCategoricalCol(Vec vec, int startIndex, int endIndex) {
    if (startIndex >= endIndex) {
      Vec v = Vec.makeZero(vec.length());
      v.setDomain(new String[]{""});
      return v;
    }
    String[] dom = vec.domain().clone();

    HashMap<String, ArrayList<Integer>> substringToOldDomainIndices = new HashMap<>();
    String substr;
    for (int i = 0; i < dom.length; i++) {
      substr = dom[i].substring(startIndex < dom[i].length() ? startIndex : dom[i].length(),
          endIndex < dom[i].length() ? endIndex : dom[i].length());
      dom[i] = substr;

      if (!substringToOldDomainIndices.containsKey(substr)) {
        ArrayList<Integer> val = new ArrayList<>();
        val.add(i);
        substringToOldDomainIndices.put(substr, val);
      } else {
        substringToOldDomainIndices.get(substr).add(i);
      }
    }
    //Check for duplicated domains
    if (substringToOldDomainIndices.size() < dom.length)
      return VecUtils.DomainDedupe.domainDeduper(vec, substringToOldDomainIndices);

    return vec.makeCopy(dom);
  }

  private Vec substringStringCol(Vec vec, final int startIndex, final int endIndex) {
    return new MRTask() {
      @Override
      public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (startIndex >= endIndex) {
          for (int i = 0; i < chk.len(); i++)
            newChk.addStr("");
        } else if (((CStrChunk) chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiSubstring(newChk, startIndex, endIndex);
        } else { //UTF requires Java string methods
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else {
              String str = chk.atStr(tmpStr, i).toString();
              newChk.addStr(str.substring(startIndex < str.length() ? startIndex : str.length(),
                  endIndex < str.length() ? endIndex : str.length()));
            }
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().anyVec();
  }

}

package water.rapids.ast.prims.string;

import org.apache.commons.lang.StringUtils;
import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
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
    for (VecAry v : fr.vecs().singleVecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("trim() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();//[fr.numCols()];
    int i = 0;
    for (VecAry v : fr.vecs().singleVecs()) {
      if (v.isCategorical())
        nvs.append(lstripCategoricalCol(v, set));
      else
        nvs.append(lstripStringCol(v, set));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry lstripCategoricalCol(VecAry vec, String set) {
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

    return vec.makeCopy(new String[][]{doms});
  }

  private VecAry lstripStringCol(VecAry vec, String set) {
    final String charSet = set;
    return new MRTask() {
      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk._len; i++) {
          if (chk.isNA(i))
            newChk.addNA(0);
          else
            newChk.addStr(0,StringUtils.stripStart(chk.atStr(tmpStr, i).toString(), charSet));
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().vecs();
  }
}

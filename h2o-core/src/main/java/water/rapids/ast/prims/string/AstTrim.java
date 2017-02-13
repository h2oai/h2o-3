package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Accepts a frame with a single string column.
 * Returns a new string column containing the trimmed versions of the strings in the target column.
 * Trimming removes all characters of value 0x20 or lower at the beginning and end of the
 * target string. Thus this only trims one of the 17 characters UTF considers as a space.
 */
public class AstTrim extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (trim x)

  @Override
  public String str() {
    return "trim";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    // Type check
    for (VecAry v : fr.vecs().singleVecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("trim() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry(); // new Vec[fr.numCols()];
    int i = 0;
    for (VecAry v : fr.vecs().singleVecs()) {
      if (v.isCategorical())
        nvs.append(trimCategoricalCol(v));
      else
        nvs.append(trimStringCol(v));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry trimCategoricalCol(VecAry vec) {
    String[] doms = vec.domain().clone();

    HashMap<String, ArrayList<Integer>> trimmedToOldDomainIndices = new HashMap<>();
    String trimmed;
    for (int i = 0; i < doms.length; ++i) {
      trimmed = doms[i].trim();
      doms[i] = trimmed;

      if (!trimmedToOldDomainIndices.containsKey(trimmed)) {
        ArrayList<Integer> val = new ArrayList<>();
        val.add(i);
        trimmedToOldDomainIndices.put(trimmed, val);
      } else {
        trimmedToOldDomainIndices.get(trimmed).add(i);
      }
    }
    //Check for duplicated domains
    if (trimmedToOldDomainIndices.size() < doms.length)
      return VecUtils.DomainDedupe.domainDeduper(vec, trimmedToOldDomainIndices);

    return vec.makeCopy(new String[][]{doms});
  }

  private VecAry trimStringCol(VecAry vec) {
    return new MRTask() {
      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        ((CStrChunk) chk.getChunk(0)).asciiTrim((NewChunk)newChk.getChunk(0));
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().vecs();
  }
}

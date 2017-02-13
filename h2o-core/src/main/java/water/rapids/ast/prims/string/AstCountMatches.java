package water.rapids.ast.prims.string;

import org.apache.commons.lang.StringUtils;
import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstStrList;

/**
 * Accepts a frame with a single string column, and a substring to look for in the target.
 * Returns a new integer column containing the countMatches result for each string in the
 * target column.
 * <p/>
 * countMatches - Counts how many times the substring appears in the larger string.
 * If either the target string or substring are empty (""), 0 is returned.
 */
public class AstCountMatches extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "pattern"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (countmatches x pattern)

  @Override
  public String str() {
    return "countmatches";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final String[] pattern = asts[2] instanceof AstStrList
        ? ((AstStrList) asts[2])._strs
        : new String[]{asts[2].exec(env).getStr()};

    // Type check
    for (VecAry v : fr.vecs().singleVecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("countmatches() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();//new Vec[fr.numCols()];
    int i = 0;
    for (VecAry v : fr.vecs().singleVecs()) {
      if (v.isCategorical())
        nvs.append(countMatchesCategoricalCol(v, pattern));
      else
        nvs.append(countMatchesStringCol(v, pattern));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry countMatchesCategoricalCol(VecAry vec, String[] pattern) {
    final int[] matchCounts = countDomainMatches(vec.domain(), pattern);
    return new MRTask() {
      @Override
      public void map(ChunkAry cs, NewChunkAry ncs) {

        for (int i = 0; i < cs._len; ++i) {
          if (!cs.isNA(i)) {
            int idx = cs.at4(i);
            ncs.addInteger(0,matchCounts[idx]);
          } else ncs.addNA(0);
        }
      }
    }.doAll(1, Vec.T_NUM, new Frame(vec)).outputFrame().vecs();
  }

  int[] countDomainMatches(String[] domain, String[] pattern) {
    int[] res = new int[domain.length];
    for (int i = 0; i < domain.length; i++)
      for (String aPattern : pattern)
        res[i] += StringUtils.countMatches(domain[i], aPattern);
    return res;
  }

  private VecAry countMatchesStringCol(VecAry vec, String[] pat) {
    final String[] pattern = pat;
    return new MRTask() {
      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk._len; ++i) {
          if (chk.isNA(i)) newChk.addNA(0);
          else {
            int cnt = 0;
            for (String aPattern : pattern)
              cnt += StringUtils.countMatches(chk.atStr(tmpStr, i).toString(), aPattern);
            newChk.addInteger(cnt, 0);
          }
        }
      }
    }.doAll(Vec.T_NUM, new Frame(vec)).outputFrame().vecs();
  }
}

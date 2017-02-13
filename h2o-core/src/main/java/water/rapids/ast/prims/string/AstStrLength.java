package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Accepts a frame with a single string column.
 * Returns a new integer column containing the character count for each string in the target column.
 */
public class AstStrLength extends AstPrimitive {
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
    return "strlen";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    // Type check
    for (VecAry v : fr.vecs().singleVecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("length() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();
    int i = 0;
    for (VecAry v : fr.vecs().singleVecs()) {
      if (v.isCategorical())
        nvs.append(lengthCategoricalCol(v));
      else
        nvs.append(lengthStringCol(v));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry lengthCategoricalCol(VecAry vec) {
    //String[] doms = vec.domain();
    //int[] catLengths = new int[doms.length];
    //for (int i = 0; i < doms.length; ++i) catLengths[i] = doms[i].length();
    VecAry res = new MRTask() {
      transient int[] catLengths;

      @Override
      public void setupLocal() {
        String[] doms = _fr.anyVec().domain();
        catLengths = new int[doms.length];
        for (int i = 0; i < doms.length; ++i) catLengths[i] = doms[i].length();
      }

      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        for (int i = 0; i < chk._len; i++)
          if (chk.isNA(i))
            newChk.addNA(0);
          else
            newChk.addNum(0,catLengths[(int) chk.atd(i)], 0);
      }
    }.doAll(1, Vec.T_NUM, new Frame(vec)).outputFrame().vecs();
    return res;
  }

  private VecAry lengthStringCol(VecAry vec) {
    return new MRTask() {
      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk._len; i++) {
          if (chk.isNA(i)) newChk.addNA(0);
          else newChk.addNum(0,chk.atStr(tmpStr, i).toString().length(), 0);
        }
      }

    }.doAll(new byte[]{Vec.T_NUM}, vec).outputFrame().vecs();
  }
}

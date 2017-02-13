package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.HashMap;

/**
 */
public class AstEntropy extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (entropy x)

  @Override
  public String str() {
    return "entropy";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    //Type check
    for (VecAry v : fr.vecs().singleVecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("entropy() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    //Transform each vec
    VecAry nvs = new VecAry();
    int i = 0;
    for (VecAry v : fr.vecs().singleVecs()) {
      if (v.isCategorical())
        nvs.append(entropyCategoricalCol(v));
      else
        nvs.append(entropyStringCol(v));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry entropyCategoricalCol(VecAry vec) {
    return new MRTask() {
      transient double[] catEntropies;
      @Override
      public void setupLocal() {
        String[] doms = _fr.anyVec().domain();
        catEntropies = new double[doms.length];
        for (int i = 0; i < doms.length; i++) catEntropies[i] = calcEntropy(doms[i]);
      }

      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        //pre-allocate since the size is known
        for (int i = 0; i < chk._len; i++)
          if (chk.isNA(i))
            newChk.addNA(0);
          else
            newChk.addNum(catEntropies[(int) chk.atd(i)]);
      }
    }.doAll(1, Vec.T_NUM, new Frame(vec)).outputFrame().vecs();
  }

  private VecAry entropyStringCol(VecAry vec) {
    return new MRTask() {
      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk._len; i++) {
          if (chk.isNA(i))
            newChk.addNA(0);
          else {
            String str = chk.atStr(tmpStr, i).toString();
            newChk.addNum(calcEntropy(str));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, vec).outputFrame().vecs();
  }

  //Shannon's entropy
  private double calcEntropy(String str) {

    HashMap<Character, Integer> freq = new HashMap<>();
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      Integer count = freq.get(c);
      if (count == null) freq.put(c, 1);
      else freq.put(c, count + 1);
    }
    double sume = 0;
    int N = str.length();
    double n;
    for (char c : freq.keySet()) {
      n = freq.get(c);
      sume += -n / N * Math.log(n / N) / Math.log(2);
    }
    return sume;
  }
}

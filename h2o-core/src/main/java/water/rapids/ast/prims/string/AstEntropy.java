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
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("entropy() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    //Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for (Vec v : fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = entropyCategoricalCol(v);
      else
        nvs[i] = entropyStringCol(v);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec entropyCategoricalCol(Vec vec) {
    Vec res = new MRTask() {
      transient double[] catEntropies;

      @Override
      public void setupLocal() {
        String[] doms = _fr.anyVec().domain();
        catEntropies = new double[doms.length];
        for (int i = 0; i < doms.length; i++) catEntropies[i] = calcEntropy(doms[i]);
      }

      @Override
      public void map(Chunk chk, NewChunk newChk) {
        //pre-allocate since the size is known
        newChk.alloc_doubles(chk._len);
        for (int i = 0; i < chk._len; i++)
          if (chk.isNA(i))
            newChk.addNA();
          else
            newChk.addNum(catEntropies[(int) chk.atd(i)]);
      }
    }.doAll(1, Vec.T_NUM, new Frame(vec)).outputFrame().anyVec();
    return res;
  }

  private Vec entropyStringCol(Vec vec) {
    return new MRTask() {
      @Override
      public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) //all NAs
          newChk.addNAs(chk.len());
        else if (((CStrChunk) chk)._isAllASCII) //fast-path operations
          ((CStrChunk) chk).asciiEntropy(newChk);
        else { //UTF requires Java string methods
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else {
              String str = chk.atStr(tmpStr, i).toString();
              newChk.addNum(calcEntropy(str));
            }
          }
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, vec).outputFrame().anyVec();
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

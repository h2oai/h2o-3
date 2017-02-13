package water.rapids.ast.prims.string;

import org.apache.commons.io.FileUtils;
import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 */
public class AstCountSubstringsWords extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "words"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (num_valid_substrings x words)

  @Override
  public String str() {
    return "num_valid_substrings";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String wordsPath = asts[2].exec(env).getStr();

    //Type check
    for (VecAry v : fr.vecs().singleVecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("num_valid_substrings() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    HashSet<String> words = null;
    try {
      words = new HashSet<>(FileUtils.readLines(new File(wordsPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    //Transform each vec
    VecAry nvs = new VecAry();// = new Vec[fr.numCols()];
    int i = 0;
    for (VecAry v : fr.vecs().singleVecs()) {
      if (v.isCategorical())
        nvs.append(countSubstringsWordsCategoricalCol(v, words));
      else
        nvs.append(countSubstringsWordsStringCol(v, words));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry countSubstringsWordsCategoricalCol(VecAry vec, final HashSet<String> words) {
    VecAry res = new MRTask() {
      transient double[] catCounts;

      @Override
      public void setupLocal() {
        String[] doms = _fr.anyVec().domain();
        catCounts = new double[doms.length];
        for (int i = 0; i < doms.length; i++) catCounts[i] = calcCountSubstringsWords(doms[i], words);
      }

      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        for (int i = 0; i < chk._len; i++)
          if (chk.isNA(i))
            newChk.addNA(0);
          else
            newChk.addNum(catCounts[(int) chk.atd(i)]);
      }
    }.doAll(1, Vec.T_NUM, new Frame(vec)).outputFrame().vecs();
    return res;
  }

  private VecAry countSubstringsWordsStringCol(VecAry vec, final HashSet<String> words) {
    return new MRTask() {
      @Override
      public void map(ChunkAry chk, NewChunkAry newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk._len; i++) {
          if (chk.isNA(i))
            newChk.addNA(0);
          else {
            String str = chk.atStr(tmpStr, i).toString();
            newChk.addNum(calcCountSubstringsWords(str, words));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, vec).outputFrame().vecs();
  }

  // count all substrings >= 2 chars that are in words
  private int calcCountSubstringsWords(String str, HashSet<String> words) {
    int wordCount = 0;
    int N = str.length();
    for (int i = 0; i < N - 1; i++)
      for (int j = i + 2; j < N + 1; j++) {
        if (words.contains(str.substring(i, j)))
          wordCount += 1;
      }
    return wordCount;
  }

}

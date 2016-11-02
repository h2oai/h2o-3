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
    for (Vec v : fr.vecs())
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
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for (Vec v : fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = countSubstringsWordsCategoricalCol(v, words);
      else
        nvs[i] = countSubstringsWordsStringCol(v, words);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec countSubstringsWordsCategoricalCol(Vec vec, final HashSet<String> words) {
    Vec res = new MRTask() {
      transient double[] catCounts;

      @Override
      public void setupLocal() {
        String[] doms = _fr.anyVec().domain();
        catCounts = new double[doms.length];
        for (int i = 0; i < doms.length; i++) catCounts[i] = calcCountSubstringsWords(doms[i], words);
      }

      @Override
      public void map(Chunk chk, NewChunk newChk) {
        //pre-allocate since the size is known
        newChk.alloc_doubles(chk._len);
        for (int i = 0; i < chk._len; i++)
          if (chk.isNA(i))
            newChk.addNA();
          else
            newChk.addNum(catCounts[(int) chk.atd(i)]);
      }
    }.doAll(1, Vec.T_NUM, new Frame(vec)).outputFrame().anyVec();
    return res;
  }

  private Vec countSubstringsWordsStringCol(Vec vec, final HashSet<String> words) {
    return new MRTask() {
      @Override
      public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) //all NAs
          newChk.addNAs(chk.len());
        else { //UTF requires Java string methods
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else {
              String str = chk.atStr(tmpStr, i).toString();
              newChk.addNum(calcCountSubstringsWords(str, words));
            }
          }
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, vec).outputFrame().anyVec();
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

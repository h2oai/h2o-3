package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

public class AstTokenize extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "regex"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (tokenize x regex)

  @Override
  public String str() {
    return "tokenize";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String regex = asts[2].exec(env).getStr();

    // Type check
    for (Vec v : fr.vecs())
      if (! v.isString())
        throw new IllegalArgumentException("tokenize() requires all input columns to be of a String type. "
                + "Received " + fr.anyVec().get_type_str() + ". Please convert column to a string column first.");

    Frame tokenized = new Tokenizer(regex).doAll(Vec.T_STR, fr).outputFrame();
    return new ValFrame(tokenized);
  }

  private static class Tokenizer extends MRTask<Tokenizer> {
    private final String _regex;

    public Tokenizer(String regex) {
      _regex = regex;
    }

    @Override
    public void map(Chunk[] cs, NewChunk nc) {
      BufferedString tmpStr = new BufferedString();
      for (int row = 0; row < cs[0]._len; row++) {
        for (Chunk chk : cs) {
          if (chk.isNA(row)) continue;
          String[] ss = chk.atStr(tmpStr, row).toString().split(_regex);
          for (String s : ss)
            nc.addStr(s);
        }
        nc.addNA();
      }
    }
  }

}
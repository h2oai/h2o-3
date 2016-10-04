package water.rapids.ast.prims.repeaters;

import water.fvec.*;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;


/**
 * Simple sequence of length n
 */
public class AstSeqLen extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"length"};
  }

  /* (seq_len n) */
  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public String str() {
    return "seq_len";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    int len = (int) Math.ceil(asts[1].exec(env).getNum());
    if (len <= 0)
      throw new IllegalArgumentException("Error in seq_len(" + len + "): argument must be coercible to positive integer");
    return new ValFrame(new Frame(Vec.makeSeq(len, true)));
  }
}

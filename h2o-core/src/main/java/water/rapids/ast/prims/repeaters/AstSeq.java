package water.rapids.ast.prims.repeaters;

import water.Futures;
import water.fvec.AppendableVec;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Same logic as R's generic seq method
 */
public class AstSeq extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"from", "to", "by"};
  }

  /* (seq from to by) */
  @Override
  public int nargs() {
    return 1 + 3;
  }

  @Override
  public String str() {
    return "seq";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    double from = asts[1].exec(env).getNum();
    double to = asts[2].exec(env).getNum();
    double by = asts[3].exec(env).getNum();
    double delta = to - from;
    if (delta == 0 && to == 0)
      throw new IllegalArgumentException("Expected `to` and `from` to have nonzero difference.");
    else {
      double n = delta / by;
      if (n < 0) throw new IllegalArgumentException("wrong sign in 'by' argument");
      else if (n > Double.MAX_VALUE) throw new IllegalArgumentException("'by' argument is much too small");
      Futures fs = new Futures();
      AppendableVec av = new AppendableVec(Vec.newKey(), Vec.T_NUM);
      NewChunk nc = new NewChunk(av, 0);
      int len = (int) n + 1;
      for (int r = 0; r < len; r++) nc.addNum(from + r * by);
      // May need to adjust values = by > 0 ? min(values, to) : max(values, to)
      nc.close(0, fs);
      Vec vec = av.layout_and_close(fs);
      fs.blockForPending();
      return new ValFrame(new Frame(vec));
    }
  }
}

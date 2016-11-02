package water.rapids.ast.prims.search;

import water.Futures;
import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Indices of which entries are not equal to 0
 */
public class AstWhich extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (which col)

  @Override
  public String str() {
    return "which";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();

    // The 1-row version
    if (f.numRows() == 1 && f.numCols() > 1) {
      AppendableVec v = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec(), Vec.T_NUM);
      NewChunk chunk = new NewChunk(v, 0);
      for (int i = 0; i < f.numCols(); i++)
        if (f.vecs()[i].at8(0) != 0)
          chunk.addNum(i);
      Futures fs = chunk.close(0, new Futures());
      Vec vec = v.layout_and_close(fs);
      fs.blockForPending();
      return new ValFrame(new Frame(vec));
    }

    // The 1-column version
    Vec vec = f.anyVec();
    if (f.numCols() > 1 || !vec.isInt())
      throw new IllegalArgumentException("which requires a single integer column");
    Frame f2 = new MRTask() {
      @Override
      public void map(Chunk c, NewChunk nc) {
        long start = c.start();
        for (int i = 0; i < c._len; ++i)
          if (c.at8(i) != 0) nc.addNum(start + i);
      }
    }.doAll(new byte[]{Vec.T_NUM}, vec).outputFrame();
    return new ValFrame(f2);
  }
}

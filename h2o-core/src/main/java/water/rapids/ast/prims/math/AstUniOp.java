package water.rapids.ast.prims.math;

import water.*;
import water.fvec.*;
import water.rapids.*;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.vals.ValRow;

/**
 * Subclasses auto-widen between scalars and Frames, and have exactly one argument
 */
public abstract class AstUniOp extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val val = stk.track(asts[1].exec(env));
    switch (val.type()) {
      case Val.NUM:
        return new ValNum(op(val.getNum()));
      case Val.FRM:
        Frame fr = val.getFrame();
        return new ValFrame(new MRTask() {
          @Override
          public void map(ChunkAry cs, NewChunkAry ncs) {
            for (int col = 0; col < cs._numCols; col++) {
              for (int i = 0; i < cs._len; i++)
                ncs.addNum(col,op(cs.atd(i,col)));
            }
          }
        }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame());
      case Val.ROW:
        double[] ds = new double[val.getRow().length];
        for (int i = 0; i < ds.length; ++i)
          ds[i] = op(val.getRow()[i]);
        String[] names = ((ValRow) val).getNames().clone();
        return new ValRow(ds, names);
      default:
        throw H2O.unimpl("unop unimpl: " + val.getClass());
    }
  }

  public abstract double op(double d);
}



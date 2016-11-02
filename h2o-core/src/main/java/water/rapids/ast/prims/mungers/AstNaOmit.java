package water.rapids.ast.prims.mungers;

import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;

/**
 * Remove rows with NAs from the H2OFrame
 * Note: Current implementation is NOT in-place replacement
 */
public class AstNaOmit extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public String str() {
    return "na.omit";
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame fr2 = new MRTask() {
      private void copyRow(int row, Chunk[] cs, NewChunk[] ncs) {
        for (int i = 0; i < cs.length; ++i) {
          if (cs[i] instanceof CStrChunk) ncs[i].addStr(cs[i], row);
          else if (cs[i] instanceof C16Chunk) ncs[i].addUUID(cs[i], row);
          else if (cs[i].hasFloat()) ncs[i].addNum(cs[i].atd(row));
          else ncs[i].addNum(cs[i].at8(row), 0);
        }
      }

      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        int col;
        for (int row = 0; row < cs[0]._len; ++row) {
          for (col = 0; col < cs.length; ++col)
            if (cs[col].isNA(row)) break;
          if (col == cs.length) copyRow(row, cs, ncs);
        }
      }
    }.doAll(fr.types(), fr).outputFrame(fr.names(), fr.domains());
    return new ValFrame(fr2);
  }
}

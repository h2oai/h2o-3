package water.rapids.ast.prims.mungers;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
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

      DVal _dv;
      private void copyRow(int row, ChunkAry cs, NewChunkAry ncs) {
        for (int i = 0; i < cs._numCols; ++i)
          ncs.addVal(i,cs.getInflated(row,i,_dv));
      }

      @Override
      public void map(ChunkAry cs, NewChunkAry ncs) {
        _dv = new DVal();
        int col;
        for (int row = 0; row < cs._len; ++row) {
          for (col = 0; col < cs._numCols; ++col)
            if (cs.isNA(row,col)) break;
          if (col == cs._numCols) copyRow(row, cs, ncs);
        }
      }
    }.doAll(fr.types(), fr).outputFrame(fr.names(), fr.domains());
    return new ValFrame(fr2);
  }
}

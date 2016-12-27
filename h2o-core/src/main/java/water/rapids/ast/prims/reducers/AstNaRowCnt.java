package water.rapids.ast.prims.reducers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNum;
import water.rapids.Val;

import java.util.HashSet;
import java.util.Set;

/**
 * This method counts the number of rows that contain NAs in a frame.  It does not count
 * each occurrence of the NA but rather the number of rows that contain at least one NA.
 */
public class AstNaRowCnt extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public String str() {
    return "naRowCnt";
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }  // (naCnt fr)

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Set<Long> naRowCnt = new HashSet<Long>();
    if (fr.hasNAs()) {
      for (int i = 0; i < fr.numCols(); ++i) { // check each data columns for nas
        Vec oneColumn = fr.vec(i);
        for (long r = 0; r < fr.numRows(); r++) {
          if (oneColumn.isNA(r)) {    // add row index if encountered NA
            naRowCnt.add(r);
          }
        }
      }
    }
    return new ValNum(naRowCnt.size());
  }
}

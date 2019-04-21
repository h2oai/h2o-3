package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.vals.ValFrame;


/** Sort the whole frame by the given columns.  String columns are allowed in the frame.  However, we do
 * not support sorting on string columns.
 */
public class AstSort extends AstPrimitive {
  @Override public String[] args() { return new String[]{"ary","cols"}; }
  @Override public String str(){ return "sort";}
  @Override public int nargs() { return 1+2+1; } // (sort ary [cols] [int])

  @Override public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int[] cols = ((AstParameter)asts[2]).columns(fr.names());
    int[] sortAsc;
    if (asts[3] instanceof AstNumList)
      sortAsc = ((AstNumList) asts[3]).expand4();
    else
      sortAsc = new int[]{(int) ((AstNum) asts[3]).getNum()};  // R client can send 1 element for some reason
    
    assert sortAsc.length==cols.length;
    return new ValFrame(Merge.sort(fr,cols, sortAsc));
  }
}

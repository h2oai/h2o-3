package water.rapids.ast.prims.mungers;

import water.fvec.*;
import water.rapids.Env;
import water.rapids.ast.prims.mungers.merge.Merge;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstFunction;
import water.rapids.ast.Ast;
import water.rapids.vals.ValFrame;


/** Sort the whole frame by the given columns
 */
public class AstSort extends AstFunction {
  @Override public String[] args() { return new String[]{"ary","cols"}; }
  @Override public String str(){ return "sort";}
  @Override public int nargs() { return 1+2; } // (sort ary [cols])

  @Override public ValFrame apply(Env env, Env.StackHelp stk, Ast asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int[] cols = ((AstParameter)asts[2]).columns(fr.names());
    return new ValFrame(Merge.sort(fr,cols));
  }
}

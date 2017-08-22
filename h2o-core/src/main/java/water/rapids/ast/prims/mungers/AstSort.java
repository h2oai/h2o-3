package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

import java.util.Arrays;


/** Sort the whole frame by the given columns.  However, an error will be thrown if there are
 * String columns within the frame.
 */
public class AstSort extends AstPrimitive {
  @Override public String[] args() { return new String[]{"ary","cols"}; }
  @Override public String str(){ return "sort";}
  @Override public int nargs() { return 1+2; } // (sort ary [cols])

  @Override public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int[] cols = ((AstParameter)asts[2]).columns(fr.names());
    String[] colTypes = fr.typesStr();
    if (Arrays.asList(colTypes).contains("String"))
      throw new IllegalArgumentException("Input frame contains String columns.  Remove String columns before " +
              "calling sort again.");
    return new ValFrame(Merge.sort(fr,cols));
  }
}

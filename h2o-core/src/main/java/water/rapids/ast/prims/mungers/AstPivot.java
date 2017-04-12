package water.rapids.ast.prims.mungers;

import water.etl.prims.mungers.Pivot;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

public class AstPivot extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "index", "column", "value"}; //the array and name of columns
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // (pivot ary index column value)

  @Override
  public String str() {
    return "pivot";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame origfr = stk.track(asts[1].exec(env)).getFrame();
    String index = stk.track(asts[2].exec(env)).getStr();
    String column = stk.track(asts[3].exec(env)).getStr();
    String value = stk.track(asts[4].exec(env)).getStr();
    return new ValFrame(Pivot.get(origfr, index, column, value));
  }
}

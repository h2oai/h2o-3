package water.rapids.ast.prims.time;

import water.parser.ParseTime;
import water.rapids.Env;
import water.rapids.vals.ValStr;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;


public class AstGetTimeZone extends AstPrimitive {
  @Override
  public String[] args() {
    return null;
  }

  // (getTimeZone)
  @Override
  public int nargs() {
    return 1;
  }

  @Override
  public String str() {
    return "getTimeZone";
  }

  @Override
  public ValStr apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return new ValStr(ParseTime.getTimezone().toString());
  }
}



package water.rapids.ast.prims.time;

import org.joda.time.DateTimeZone;
import water.MRTask;
import water.parser.ParseTime;
import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.Set;

/**
 */
public class AstSetTimeZone extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"tz"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (setTimeZone "TZ")

  @Override
  public String str() {
    return "setTimeZone";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    final String tz = asts[1].exec(env).getStr();
    Set<String> idSet = DateTimeZone.getAvailableIDs();
    if (!idSet.contains(tz))
      throw new IllegalArgumentException("Unacceptable timezone " + tz + " given.  For a list of acceptable names, use listTimezone().");
    new MRTask() {
      @Override
      public void setupLocal() {
        ParseTime.setTimezone(tz);
      }
    }.doAllNodes();
    return new ValNum(Double.NaN);
  }
}

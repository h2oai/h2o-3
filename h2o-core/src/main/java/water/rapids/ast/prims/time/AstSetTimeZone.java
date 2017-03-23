package water.rapids.ast.prims.time;

import org.joda.time.DateTimeZone;
import water.parser.ParseTime;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValStr;

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
  public ValStr apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    final String tz = asts[1].exec(env).getStr();
    Set<String> idSet = DateTimeZone.getAvailableIDs();
    if (!idSet.contains(tz))
      throw new IllegalArgumentException("Unacceptable timezone " + tz + " given.  For a list of acceptable names, use listTimezone().");

    //This is a distributed operation
    ParseTime.setTimezone(tz);
    return new ValStr(tz);
  }
}

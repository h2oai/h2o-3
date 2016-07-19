package water.rapids.ast.params;

import water.rapids.Env;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.AstParameter;

/**
 * A String.  Execution is just to return the constant.
 */
public class AstStr extends AstParameter {
  public AstStr() {
  }

  public AstStr(String str) {
    super(str);
  }

  public AstStr(Rapids e, char c) {
    super(e, c);
  }

  @Override
  public String str() {
    return _v.toString().replaceAll("^\"|^\'|\"$|\'$", "");
  }

  @Override
  public Val exec(Env env) {
    return _v;
  }

  @Override
  public String toJavaString() {
    return "\"" + str() + "\"";
  }

  @Override
  public int[] columns(String[] names) {
    int i = water.util.ArrayUtils.find(names, _v.getStr());
    if (i == -1) throw new IllegalArgumentException("Column " + _v.getStr() + " not found");
    return new int[]{i};
  }
}

package water.rapids.ast.params;

import water.rapids.Env;
import water.rapids.ast.AstParameter;
import water.rapids.vals.ValStr;

/**
 * A String.  Execution is just to return the constant.
 */
public class AstStr extends AstParameter {
  private final ValStr _v;

  public AstStr() {
    this(null);
  }

  public AstStr(String str) {
    _v = new ValStr(str);
  }

  @Override
  public String str() {
    return _v.toString().replaceAll("^\"|^\'|\"$|\'$", "");
  }

  @Override
  public ValStr exec(Env env) {
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

  public String getStr() {
    return _v.getStr();
  }
}

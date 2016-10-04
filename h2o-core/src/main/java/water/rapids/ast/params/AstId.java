package water.rapids.ast.params;

import water.rapids.Env;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.AstParameter;

/**
 * An ID.  Execution does lookup in the current scope.
 */
public class AstId extends AstParameter {
  private final String _id;

  public AstId() {
    _id = null;
  }

  public AstId(Rapids e) {
    _id = e.token();
  }

  public AstId(String id) {
    _id = id;
  }

  @Override
  public String str() {
    return _id;
  }

  @Override
  public Val exec(Env env) {
    return env.returning(env.lookup(_id));
  }

  @Override
  public int nargs() {
    return 1;
  }

  @Override
  public String toJavaString() {
    return "\"" + str() + "\"";
  }
}

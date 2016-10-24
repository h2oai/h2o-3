package water.rapids.ast.params;

import water.fvec.Frame;
import water.rapids.Env;
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

  public AstId(String id) {
    _id = id;
  }

  public AstId(Frame f) {
    _id = f._key.toString();
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
  public String toJavaString() {
    return "\"" + str() + "\"";
  }
}

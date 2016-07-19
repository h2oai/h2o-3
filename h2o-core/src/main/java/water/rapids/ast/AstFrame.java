package water.rapids.ast;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

/**
 * A Frame.  Execution is just to return the constant.
 */
public class AstFrame extends AstRoot {
  final ValFrame _fr;

  public AstFrame() {
    _fr = null;
  }

  public AstFrame(Frame fr) {
    _fr = new ValFrame(fr);
  }

  @Override
  public String str() {
    return _fr == null ? null : _fr.toString();
  }

  @Override
  public String example() {
    return null;
  }

  @Override
  public String description() {
    return null;
  }

  @Override
  public Val exec(Env env) {
    return env.returning(_fr);
  }

  @Override
  public int nargs() {
    return 1;
  }
}

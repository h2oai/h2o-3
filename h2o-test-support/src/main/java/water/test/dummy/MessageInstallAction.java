package water.test.dummy;

import water.DKV;
import water.Key;
import water.parser.BufferedString;

public class MessageInstallAction extends DummyAction<MessageInstallAction> {
  private final Key _trgt;
  private final String _msg;
  
  public MessageInstallAction(Key trgt, String msg) {
    _trgt = trgt;
    _msg = msg;
  }

  @Override
  protected String run(DummyModelParameters parms) {
    DKV.put(_trgt, new BufferedString("Computed " + _msg));
    return _msg;
  }

  @Override
  protected void cleanUp() {
    DKV.remove(_trgt);
  }
}

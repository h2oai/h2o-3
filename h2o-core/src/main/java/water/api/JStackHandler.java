package water.api;

import water.api.schemas3.JStackV3;
import water.util.JStack;

public class JStackHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JStackV3 fetch(int version, JStackV3 js) {
    return js.fillFromImpl(new JStack().execImpl());
  }
}

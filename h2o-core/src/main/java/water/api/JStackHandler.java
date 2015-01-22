package water.api;

import water.util.JStack;

public class JStackHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JStackV2 fetch(int version, JStackV2 js) {
    return js.fillFromImpl(new JStack().execImpl());
  }
}

package water.api;

import water.util.JStack;

public class JStackHandler extends Handler {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JStackV2 fetch(int version, JStackV2 js) {
    JStack _jstack = new JStack();
    _jstack.execImpl();
    return js.fillFromImpl(_jstack);
  }
}

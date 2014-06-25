package water.api;

import water.util.JStack;

public class JStackHandler extends Handler<JStackHandler,JStackV2> {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  //No Input

  //Output
  JStack _jstack; // for each node in the cloud it contains all threads stack traces

  @Override protected JStackV2 schema(int version) { return new JStackV2(); }
  @Override public void compute2() {
    _jstack = new JStack();
    _jstack.execImpl(); }
}

package water;

class TaskInvalidateKey extends DTask {
  static void invalidate( H2ONode target, Key key, Futures fs ) {
    throw H2O.unimpl();
  }
  @Override void compute2() { throw H2O.unimpl(); }
}

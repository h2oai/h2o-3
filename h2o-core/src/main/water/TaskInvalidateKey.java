package water;

class TaskInvalidateKey extends DTask {
  static void invalidate( H2ONode target, Key key, Futures fs ) {
    throw H2O.unimpl();
  }
}

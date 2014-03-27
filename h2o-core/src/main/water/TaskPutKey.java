package water;

class TaskPutKey extends DTask {
  Key _key;
  transient Value _xval;
  static void put( H2ONode target, Key key, Value val, Futures fs, boolean dontCache ) {
    throw H2O.unimpl();
  }
  @Override void compute2() { throw H2O.unimpl(); }
  
}

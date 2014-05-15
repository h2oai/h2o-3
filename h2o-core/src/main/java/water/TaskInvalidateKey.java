package water;

/** Invalidate cached value on remote. */
class TaskInvalidateKey extends TaskPutKey {
  private TaskInvalidateKey(Key key){super(key,null);}
  @Override public byte priority(){return H2O.INVALIDATE_PRIORITY;}
  static void invalidate( H2ONode h2o, Key key, Futures fs ) {
    fs.add(RPC.call(h2o,new TaskInvalidateKey(key)));
  }
}

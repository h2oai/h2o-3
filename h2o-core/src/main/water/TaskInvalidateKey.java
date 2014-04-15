package water;

class TaskInvalidateKey extends TaskPutKey {
  private TaskInvalidateKey(Key key){super(key,null);}
  @Override public byte priority(){return H2O.INVALIDATE_PRIORITY;}
  static void invalidate( H2ONode h2o, Key key, Futures fs ) {
    RPC rpc = RPC.call(h2o,new TaskInvalidateKey(key));
    if( fs != null ) fs.add(rpc);
  }
}

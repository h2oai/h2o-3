package water;

/** Invalidate cached value on remote. */
class TaskInvalidateKey extends TaskPutKey {
  private final transient Value _newval;
  private TaskInvalidateKey(Key key, Value newval){super(key,null); _newval=newval;}
  @Override public byte priority(){return H2O.INVALIDATE_PRIORITY;}
  static void invalidate( H2ONode h2o, Key key, Value newval, Futures fs ) {
    if( key.equals(water.fvec.Vec.ESPC.DEBUG) )
      System.err.println(key + ", invalidate starting to "+h2o);
    assert newval._key != null && key.home();
    newval.read_lock();// block further writes until all invalidates complete
    fs.add(RPC.call(h2o,new TaskInvalidateKey(key,newval)));
  }
  // Lower read-lock, possibly enabling pending writes to start
  @Override public void onAck() { _newval.lowerActiveGetCount(null); }
}

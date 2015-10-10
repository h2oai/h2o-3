package water;

/** Invalidate cached value on remote. */
class TaskInvalidateKey extends TaskPutKey {
  private final transient Value _newval;
  private TaskInvalidateKey(Key key, Value newval){super(key,null); _newval=newval;}
  @Override public byte priority(){return H2O.INVALIDATE_PRIORITY;}
  static void invalidate( H2ONode h2o, Key key, Value newval, Futures fs ) {
    assert newval._key != null && key.home();
    // Prevent the new Value from being overwritten by Yet Another PUT by
    // read-locking it.  It's safe to read, but not to over-write, until this
    // invalidate completes on the *prior* value.  
    newval.read_lock();// block further writes until all invalidates complete
    fs.add(RPC.call(h2o,new TaskInvalidateKey(key,newval)));
  }
  // Lower read-lock, possibly enabling pending writes to start
  @Override public void onAck() { _newval.lowerActiveGetCount(null); }
}

package water;

/**
 * Atomic update of a Key
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

abstract public class Atomic<T extends Atomic> extends DTask<T> {
  protected Key _key;           // Transaction key

  public Atomic(){ super(H2O.ATOMIC_PRIORITY); }
  public Atomic(H2O.H2OCountedCompleter completer){super(completer,H2O.ATOMIC_PRIORITY);}
  // User's function to be run atomically.  The Key's Value is fetched from the
  // home STORE and passed in.  The returned Value is atomically installed as
  // the new Value (and the function is retried until it runs atomically).  The
  // original Value is supposed to be read-only.  If the original Key misses
  // (no Value), one is created with 0 length and wrong Value._type to allow
  // the Key to passed in (as part of the Value)
  abstract protected Value atomic( Value val );

  /** Executed on the transaction key's <em>home</em> node after any successful
   *  atomic update.  Override this if you need to perform some action after
   *  the update succeeds (eg cleanup).
   */
  protected void onSuccess( Value old ){}

  /** Block until it completes, even if run remotely */
  public final Atomic<T> invoke( Key key ) {
    RPC<Atomic<T>> rpc = fork(key);
    return (rpc == null ? this : rpc.get()); // Block for it
  }

  // Fork off
  public final RPC<Atomic<T>> fork(Key key) {
    _key = key;
    if( key.home() ) {          // Key is home?
      compute2();               // Also, run it blocking/now
      return null;
    } else {                    // Else run it remotely
      return RPC.call(key.home_node(),this);
    }
  }

  // The (remote) workhorse:
  @Override
  public final void compute2() {
    assert _key.home() : "Atomic on wrong node; SELF="+H2O.SELF+
      ", key_home="+_key.home_node()+", key_is_home="+_key.home()+", class="+getClass();
    Futures fs = new Futures(); // Must block on all invalidates eventually
    Value val1 = DKV.get(_key);
    while( true ) {
      // Run users' function.  This is supposed to read-only from val1 and
      // return new val2 to atomically install.
      Value val2 = atomic(val1);
      if( val2 == null ) {      // ABORT: they gave up
        // Strongly order XTNs on same key, EVEN if aborting.  Generally abort
        // means some interesting condition is already met, but perhaps met by
        // the exactly proceeding XTN whose invalidates are still roaming about
        // the system.  If we do not block, the Atomic.invoke might complete
        // before the invalidates, and the invoker might then do a DKV.get()
        // and get his original value - instead of inval & fetching afresh.
        if (val1 != null) val1.blockTillNoReaders(); // Prior XTN that made val1 may not yet have settled out; block for it
        break; 
      }
      assert val1 != val2;      // No returning the same Value
      // Attempt atomic update
      Value res = DKV.DputIfMatch(_key,val2,val1,fs);
      if( res == val1 ) {       // Success?
        fs.blockForPending();   // Block for any pending invalidates on the atomic update
        onSuccess(val1);        // Call user's post-XTN function
        break;
      }
      val1 = res;               // Otherwise try again with the current value
    }                           // and retry
    _key = null;                // No need for key no more, don't send it back
    tryComplete();
  }
}

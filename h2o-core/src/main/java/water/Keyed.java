package water;

/* Iced, with a Key.  Support for DKV removal. */
public abstract class Keyed extends Iced {
  /** Key mapping a Value which holds this Vec.  */
  public final Key _key;        // Top-level key; may be null
  public Keyed( Key key ) { _key = key; }

  // Remove any K/V store parts associated with this Key
  public final void remove( ) { remove(new Futures()).blockForPending(); }
  public final Futures remove( Futures fs ) {
    if( _key != null ) DKV.remove(_key,fs);
    return remove_impl(fs);
  }

  // Override to remove subparts, but not self
  protected Futures remove_impl( Futures fs ) { return fs; }

  public static void remove( Key k ) {
    Value val = DKV.get(k);
    if( val==null ) return;
    ((Keyed)val.get()).remove();
  }
  public static void remove( Key k, Futures fs ) {
    Value val = DKV.get(k);
    if( val==null ) return;
    ((Keyed)val.get()).remove(fs);
  }
}

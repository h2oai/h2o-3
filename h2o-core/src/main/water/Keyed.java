package water;

/* Iced, with a Key.  Support for DKV removal. */
public abstract class Keyed<T extends Lockable<T>> extends Iced {
  /** Key mapping a Value which holds this Vec.  */
  protected Key _key;        // Top-level key

  // Remove any K/V store parts associated with this Key
  protected void remove( ) { remove(new Futures()).blockForPending(); }
  protected Futures remove( Futures fs ) {
    DKV.remove(_key,fs);
    return fs; 
  }
  static void remove( Key k ) {
    Value val = DKV.get(k);
    if( val==null ) return;
    ((Keyed)val.get()).remove();
  }
}

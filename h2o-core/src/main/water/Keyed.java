package water;

/* Iced, with a Key.  Support for DKV removal. */
public abstract class Keyed extends Iced {
  /** Key mapping a Value which holds this Vec.  */
  public Key _key;        // Top-level key
  public Keyed( Key key ) { _key = key; }

  // Remove any K/V store parts associated with this Key
  public void remove( ) { remove(new Futures()).blockForPending(); }
  public Futures remove( Futures fs ) {
    if( _key != null ) DKV.remove(_key,fs);
    return fs; 
  }
  public static void remove( Key k ) {
    Value val = DKV.get(k);
    if( val==null ) return;
    ((Keyed)val.get()).remove();
  }
}

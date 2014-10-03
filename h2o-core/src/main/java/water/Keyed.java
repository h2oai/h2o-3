package water;

import water.fvec.*;

/** Iced, with a Key.  Support for DKV removal. */
public abstract class Keyed extends Iced {
  /** Key mapping a Value which holds this object; may be null  */
  public final Key _key;
  public Keyed( Key key ) { _key = key; }

  /** Remove this Keyed object, and all subparts; blocking. */
  public final void remove( ) { remove(new Futures()).blockForPending(); }
  /** Remove this Keyed object, and all subparts.  */
  public final Futures remove( Futures fs ) {
    if( _key != null ) DKV.remove(_key,fs);
    return remove_impl(fs);
  }

  /** Override to remove subparts, but not self, of composite Keyed objects.  
   *  Examples include {@link Vec} (removing associated {@link Chunk} keys)
   *  and {@link Frame} (removing associated {@link Vec} keys.) */
  protected Futures remove_impl( Futures fs ) { return fs; }

  /** Remove this Keyed object, and all subparts; blocking. */
  public static void remove( Key k ) {
    Value val = DKV.get(k);
    if( val==null ) return;
    ((Keyed)val.get()).remove();
  }
  /** Remove this Keyed object, and all subparts. */
  public static void remove( Key k, Futures fs ) {
    Value val = DKV.get(k);
    if( val==null ) return;
    ((Keyed)val.get()).remove(fs);
  }

  /**
   * High-quality 64-bit checksum of the <i>content</i> of the
   * object.  Similar to hashcode(), but a long to reduce the
   * chance of hash clashes.  For composite objects this should
   * be defined using the subcomponents' checksums (or hashcodes
   * if not available).  If two Keyed objects have the same
   * checksum() there should be a 1 - 1/2^64 chance that they
   * are the same object by value.
   */
  abstract public long checksum();

}

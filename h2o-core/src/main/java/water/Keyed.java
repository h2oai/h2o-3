package water;

import water.api.schemas3.KeyV3;
import water.fvec.*;

/** Iced, with a Key.  Support for DKV removal. */
public abstract class Keyed<T extends Keyed> extends Iced<T> {
  /** Key mapping a Value which holds this object; may be null  */
  public Key<T> _key;
  public Keyed() { _key = null; } // NOTE: every Keyed that can come out of the REST API has to have a no-arg constructor.
  public Keyed( Key<T> key ) { _key = key; }

  // ---
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

  // ---
  /** Write this Keyed object, and all nested Keys. */
  public AutoBuffer writeAll(AutoBuffer ab) { return writeAll_impl(ab.put(this)); }
  // Override this to write out subparts
  protected AutoBuffer writeAll_impl(AutoBuffer ab) { return ab; }

  /** Read a Keyed object, and all nested Keys.  Nested Keys are injected into the K/V store
   *  overwriting what was there before.  */
  public static Keyed readAll(AutoBuffer ab) { 
    Futures fs = new Futures();
    Keyed k = ab.getKey(fs);
    fs.blockForPending();       // Settle out all internal Key puts
    return k;
  }
  // Override this to read in subparts
  protected Keyed readAll_impl(AutoBuffer ab, Futures fs) { return this; }

  /** High-quality 64-bit checksum of the <i>content</i> of the object.  Similar
   *  to hashcode(), but a long to reduce the chance of hash clashes.  For
   *  composite objects this should be defined using the subcomponents' checksums
   *  (or hashcodes if not available).  If two Keyed objects have the same
   *  checksum() there should be a 1 - 1/2^64 chance that they are the same
   *  object by value.
   */
  protected long checksum_impl() { throw H2O.fail("Checksum not implemented by class "+this.getClass()); }
  private long _checksum;
  // Efficiently fetch the checksum, setting on first access
  public final long checksum() {
    if( _checksum!=0 ) return _checksum;
    long x = checksum_impl();
    if( x==0 ) x=1;
    return (_checksum=x);
  }

  public Class<? extends KeyV3> makeSchema() { throw H2O.fail("Override in subclasses which can be the result of a Job"); }
}

package water;

import water.api.schemas3.KeyV3;
import water.fvec.*;
import water.util.Log;

/** Iced, with a Key.  Support for DKV removal. */
public abstract class Keyed<T extends Keyed> extends Iced<T> {
  /** Key mapping a Value which holds this object; may be null  */
  public Key<T> _key;
  public Keyed() { _key = null; } // NOTE: every Keyed that can come out of the REST API has to have a no-arg constructor.
  public Keyed( Key<T> key ) { _key = key; }

  public Key<T> getKey() {
    return _key;
  }

  // ---
  /** Remove this Keyed object, and all subparts; blocking. */
  public final void remove() {
    remove(true);
  }

  /** Remove this Keyed object, including all subparts if cascade = true; blocking. */
  public final void remove(boolean cascade) { remove(new Futures(), cascade).blockForPending(); }
  /** Remove this Keyed object, and all subparts.  */
  public final Futures remove( Futures fs ) {
    return remove(fs, true);
  }
  /** Remove this Keyed object, including all subparts if cascade = true.  */
  public final Futures remove( Futures fs, boolean cascade ) {
    fs = remove_self_key_impl(fs);
    return remove_impl(fs, cascade);
  }

  /**
   * @deprecated Better override {@link #remove_impl(Futures, boolean)} instead
   */
  @Deprecated
  protected Futures remove_impl(Futures fs) { return fs; }

  /** Override to remove subparts, but not self, of composite Keyed objects.
   *  Examples include {@link Vec} (removing associated {@link Chunk} keys)
   *  and {@link Frame} (removing associated {@link Vec} keys.) */
  protected Futures remove_impl(Futures fs, boolean cascade) { return remove_impl(fs); }

  /** Remove my own key from DKV.  */
  protected Futures remove_self_key_impl(Futures fs) {
    if (_key != null)
      DKV.remove(_key,fs);
    return fs;
  }

  /** 
   * Removes the Keyed object associated to the key, and all subparts; blocking.
   * @return true if there was anything to be removed.
   **/
  public static boolean remove( Key k ) {
    if (k==null) return false;
    Value val = DKV.get(k);
    if (val==null) return false;
    ((Keyed)val.get()).remove();
    return true;
  }
  public static void removeQuietly(Key k) {
    try {
      remove(k);
    } catch (Exception e) {
      String reason = e.getMessage() != null ? " Reason: " + e.getMessage() : "";
      Log.warn("Failed to correctly release memory associated with key=" + k + "." + reason);
      Log.debug("Failed to remove key " + k, e);
    }
  }
  /** Remove the Keyed object associated to the key, and all subparts. */
  public static Futures remove( Key k, Futures fs, boolean cascade) {
    if (k==null) return fs;
    Value val = DKV.get(k);
    if (val==null) return fs;
    return ((Keyed)val.get()).remove(fs, cascade);
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
  protected long checksum_impl(boolean noCache) { return checksum_impl(); }
  private long _checksum;
  // Efficiently fetch the checksum, setting on first access
  public final long checksum() {
    if( _checksum!=0 ) return _checksum;
    long x = checksum_impl(false);
    if( x==0 ) x=1;
    return (_checksum=x);
  }
  public final long checksum(boolean noCache) {
    if (noCache)
      return checksum_impl(noCache);
    return checksum();
  }

  // TODO: REMOVE THIS!  It's not necessary; we can do it with reflection.
  public Class<? extends KeyV3> makeSchema() { throw H2O.fail("Override in subclasses which can be the result of a Job"); }
}

package water;

import sun.misc.Unsafe;
import water.nbhm.UtilUnsafe;

/** Base Class for the {@link Iced} implementation hierarchy; subclasses are
 *  all auto-gen'd and no user code should call or extend this class.  Since
 *  this is the base, it has no fields to read or write.  */
public class Icer<T extends Freezable> { 
  protected static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
  static final Icer<Iced> ICER = new Icer<Iced>(null);
  private final T _new;
  public Icer(T iced) { _new=iced; }
  final T theFreezable() { return _new; }
  protected AutoBuffer write    (AutoBuffer ab, T ice) { /*base of the write call chain; no fields to write*/return ab; } 
  protected AutoBuffer writeJSON(AutoBuffer ab, T ice) { return ab.put1('{').put1('}'); }
  protected T read    (AutoBuffer ab, T ice) { /*base of the read call chain; no fields to read*/return ice; }
  protected T readJSON(AutoBuffer ab, T ice) { /*base of the read call chain; no fields to read*/return ice; }
  protected void copyOver( T dst, T src ) { /*base of the call chain; no fields to copy*/ }
  protected int frozenType() { throw fail(); }
  protected String className() { throw fail(); }
  private RuntimeException fail() {
    return new RuntimeException(getClass().toString()+" should be automatically overridden by the auto-serialization code");
  }
  // The generated delegate call-chain will bottom out with final methods
  // that end in the TypeMap ID for "Iced" class - which is "2".
  protected final AutoBuffer write2    (AutoBuffer ab, T ice) { return ab; } 
  protected final AutoBuffer writeJSON2(AutoBuffer ab, T ice) { return ab; }
  protected final T read2    (AutoBuffer ab, T ice) { return ice; }
  protected final T readJSON2(AutoBuffer ab, T ice) { return ice; }
  // That end in the TypeMap ID for "H2OCountedCompleter" class - which is "3".
  protected final AutoBuffer write3    (AutoBuffer ab, T ice) { return ab; } 
  protected final AutoBuffer writeJSON3(AutoBuffer ab, T ice) { return ab; }
  protected final T read3    (AutoBuffer ab, T ice) { return ice; }
  protected final T readJSON3(AutoBuffer ab, T ice) { return ice; }
}

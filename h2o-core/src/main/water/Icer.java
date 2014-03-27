package water;

import sun.misc.Unsafe;
import water.nbhm.UtilUnsafe;

// Base Class for the "iced implementation" heirarchy.  Subclasses are all
// auto-gen'd.  Since this is the base, it has no fields to read or write.
public class Icer<T extends Freezable> { 
  protected static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
  static final Icer<Iced> ICER = new Icer<Iced>(null);
  private final T _new;
  protected Icer(T iced) { _new=iced; }
  final T newFreezable() { return _new.clone(); }
  AutoBuffer write(AutoBuffer ab, T ice) { /*base of the write call chain; no fields to write*/return ab; } 
  AutoBuffer writeJSONFields(AutoBuffer ab, T ice) { return ab; }
  T read(AutoBuffer ab, T ice) { /*base of the read call chain; no fields to read*/return ice; }
  int frozenType() { throw fail(); }
  String className() { throw fail(); }
  private RuntimeException fail() {
    return new RuntimeException(getClass().toString()+" should be automatically overridden by the auto-serialization code");
  }
  // The generated delegate call-chain will bottom out with final methods
  // that end in the TypeMap ID for "Iced" class - which is "2".
  protected final AutoBuffer write2(AutoBuffer ab, T ice) { return ab; } 
  protected final T read2(AutoBuffer ab, T ice) { return ice; }
  // That end in the TypeMap ID for "H2OCountedCompleter" class - which is "3".
  protected final AutoBuffer write3(AutoBuffer ab, T ice) { return ab; } 
  protected final T read3(AutoBuffer ab, T ice) { return ice; }
}

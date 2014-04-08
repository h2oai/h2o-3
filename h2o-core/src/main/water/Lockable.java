package water;

public abstract class Lockable<T extends Lockable<T>> extends Iced {
  static void delete( Key k ) { throw H2O.unimpl(); }
}

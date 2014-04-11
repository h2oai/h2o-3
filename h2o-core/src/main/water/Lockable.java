package water;

public abstract class Lockable<T extends Lockable<T>> extends Keyed {
  public Lockable( Key key ) { super(key); }
}

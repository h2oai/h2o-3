package water;

public class MemKeyed<T extends MemKeyed<T>> extends Keyed<T> {

  protected byte[] _mem;

  public MemKeyed(Key<T> key, byte[] mem) {
    super(key);
    _mem = mem;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final T reloadFromBytes(Key k, byte[] ary) {
    _key = k;
    _mem = ary;
    postReload();
    return (T) this;
  }

  public void postReload() {
    // do nothing
  }
  
  public final byte[] asBytes() {
    return _mem;
  }

  public final byte[] rawMem() {
    return _mem;
  }
  
  @Override
  public final T reloadFromBytes(byte[] ary) {
    throw new IllegalStateException("Reloading from bytes is not supported for MemKeyed objects");
  }
}

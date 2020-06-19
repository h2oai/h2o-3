package water;

public abstract class LightKeyed<T extends LightKeyed<T>> extends Keyed<T> {

    public LightKeyed( Key<T> key ) {
        super(key);
    }

    public abstract T reloadFromBytes(Key<T> k, byte[] ary);
    public abstract byte[] asBytes();

    @Override
    public T reloadFromBytes(byte[] ary) {
        throw new IllegalStateException("Reloading from bytes is not supported for LightKeyed objects");
    }
}

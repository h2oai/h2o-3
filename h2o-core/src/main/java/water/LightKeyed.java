package water;

public abstract class LightKeyed<T extends LightKeyed> extends Keyed<T> {

    public LightKeyed( Key<T> key ) {
        super(key);
    }

    public abstract T reloadFromBytes(Key k, byte [] ary);
    public abstract byte[] asBytes();

}

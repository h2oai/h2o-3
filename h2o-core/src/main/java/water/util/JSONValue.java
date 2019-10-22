package water.util;

import water.Iced;
import water.api.Schema;

import java.lang.reflect.Array;
import java.util.Objects;

public class JSONValue<V> extends Iced {

    @SuppressWarnings("unchecked")
    public static <V> JSONValue<V> fromValue(V v) {
        return new JSONValue(JSONUtils.toJSON(v), v.getClass());
    }

    protected String _json;
    protected Class<V> _clazz;

    public JSONValue(String json) {
        this(json, null);
    }

    public JSONValue(String _json, Class<V> _clazz) {
        this._json = _json;
        this._clazz = _clazz;
    }

    public V value() {
        return valueAs(_clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T valueAs(Class<T> clazz) {
        if (clazz == null) return (T)JSONUtils.parse(_json);
        return JSONUtils.parse(_json, clazz);
    }

    public <T extends Iced, S extends Schema<T, S>> T valueAs(Class<T> clazz, Class<S> schema) {
        S s = valueAs(schema);
        return s.createAndFillImpl();
    }

    @SuppressWarnings("unchecked")
    public <T extends Iced, S extends Schema<T, S>> T[] valueAsArray(Class<T[]> clazz, Class<S[]> schema) {
        S[] ss = valueAs(schema);
        Class<T> tClazz = (Class<T>)clazz.getComponentType();
        T[] ts = (T[])Array.newInstance(tClazz, ss.length);
        for (int i = 0; i < ss.length; i++) {
            ts[i] = ss[i].createAndFillImpl();
        }
        return ts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JSONValue<?> jsonValue = (JSONValue<?>) o;
        return Objects.equals(_json, jsonValue._json) && Objects.equals(_clazz, jsonValue._clazz);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_json, _clazz);
    }
}

package water.util;

import water.Iced;
import water.api.Schema;
import water.api.Schema.AutoParseable;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Objects;

/**
 * CLass providing encapsulation for json values, especially for parts of json objects with polymorphic values.
 *
 * Idea is to store json (or part of json object) as json string + class for serialization.
 * It can then later be parsed dynamically using the provided `value` and `valueAs` methods.
 * @param <V>
 */
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

    public JSONValue(String json, Class<V> clazz) {
        _json = json;
        _clazz = clazz;
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
        return valueAsSchema(schema).createAndFillImpl();
    }

    @SuppressWarnings("unchecked")
    public <T extends Iced, S extends Schema<T, S>> T[] valueAsArray(Class<T[]> clazz, Class<S[]> schema) {
        final S[] ss = valueAsSchemas(schema);
        final Class<T> tClazz = (Class<T>)clazz.getComponentType();
        final T[] ts = (T[])Array.newInstance(tClazz, ss.length);
        for (int i=0; i<ss.length; i++) {
            ts[i] = ss[i].createAndFillImpl();
        }
        return ts;
    }

    public <S extends Schema> S valueAsSchema(Class<S> schema) {
        final S s;
        if (AutoParseable.class.isAssignableFrom(schema)) {
            s = valueAs(schema);
        } else {
            s = Schema.newInstance(schema);
            PojoUtils.fillFromJson(s, _json);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    public <S extends Schema> S[] valueAsSchemas(Class<S[]> schema) {
        final Class<S> sClazz = (Class<S>)schema.getComponentType();
        final S[] ss;
        if (AutoParseable.class.isAssignableFrom(sClazz)) {
            ss = valueAs(schema);
        } else {
            final Map[] maps = valueAs(Map[].class);
            ss = (S[]) Array.newInstance(sClazz, maps.length);
            for (int i=0; i<ss.length; i++) {
                ss[i] = JSONValue.fromValue(maps[i]).valueAsSchema(sClazz);
            }
        }
        return ss;
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

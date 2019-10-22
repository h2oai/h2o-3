package water.util;

import water.Iced;

public class JSONValue<V> extends Iced {

    public static <V> JSONValue<V> fromValue(V v) {
        return new JSONValue<>(JSONUtils.toJSON(v));
    }

    protected String _json;

    public JSONValue(String json) {
        _json = json;
    }

    @SuppressWarnings("unchecked")
    public V value() {
        return (V) JSONUtils.parse(_json);
    }
}

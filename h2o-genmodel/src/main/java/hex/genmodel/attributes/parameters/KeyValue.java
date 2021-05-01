package hex.genmodel.attributes.parameters;

import java.io.Serializable;

public class KeyValue implements Serializable, Pair<String, Double> {
    
    public final String key;
    public final double value;

    public KeyValue(String key, double value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public Double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{Key: " + key + ", Value: " + value + "}";
    }
}

package hex.genmodel.attributes.parameters;

import java.io.Serializable;
import java.util.Comparator;

public class KeyValue implements Serializable {
    
    public final String key;
    public final double value;

    public KeyValue(String key, double value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{Key: " + key + ", Value: " + value + "}";
    }
}

package hex.genmodel.attributes.parameters;

import java.io.Serializable;
import java.util.Objects;

public class StringPair implements Serializable {

    public StringPair(String a, String b) {
        _a = a;
        _b = b;
    }

    public final String _a;
    public final String _b;

    @Override
    public String toString() {
        return "(" + _a + ":" + _b + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringPair that = (StringPair) o;
        return _a.equals(that._a) && _b.equals(that._b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_a, _b);
    }
}

package hex.tree.xgboost;

import water.BootstrapFreezable;
import water.Iced;

import java.util.Objects;

public class EvalMetric extends Iced<EvalMetric> implements BootstrapFreezable<EvalMetric> {

    public final String _name;
    public final double _value;

    public EvalMetric(String name, double value) {
        _name = name;
        _value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvalMetric that = (EvalMetric) o;
        return Double.compare(that._value, _value) == 0 && Objects.equals(_name, that._name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _value);
    }
}

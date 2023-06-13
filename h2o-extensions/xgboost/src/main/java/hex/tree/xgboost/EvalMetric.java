package hex.tree.xgboost;

import water.BootstrapFreezable;
import water.Iced;

import java.util.Objects;

public final class EvalMetric extends Iced<EvalMetric> implements BootstrapFreezable<EvalMetric> {

    public final String _name;
    public final double _trainValue;
    public final double _validValue;

    public EvalMetric(String name, double trainValue, double validValue) {
        _name = name;
        _trainValue = trainValue;
        _validValue = validValue;
    }

    private EvalMetric(String name) {
        this(name, Double.NaN, Double.NaN);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvalMetric that = (EvalMetric) o;
        return Double.compare(that._trainValue, _trainValue) == 0 && Double.compare(that._validValue, _validValue) == 0 && Objects.equals(_name, that._name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _trainValue, _validValue);
    }

    public static EvalMetric empty(String name) {
        return new EvalMetric(name);
    }

}

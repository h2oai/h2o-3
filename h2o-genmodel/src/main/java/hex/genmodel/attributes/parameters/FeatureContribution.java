package hex.genmodel.attributes.parameters;

import java.io.Serializable;

public class FeatureContribution implements Serializable, Pair<Object, Double> {

    public final Object columnId;
    public final double shapleyContribution;

    public FeatureContribution(Object columnId, double shapleyContribution) {
        this.columnId = columnId;
        this.shapleyContribution = shapleyContribution;
    }

    public Object getKey() {
        return columnId;
    }

    public Double getValue() {
        return shapleyContribution;
    }

    @Override
    public String toString() {
        return "{ColumnName: " + columnId + ", ShapleyContribution: " + shapleyContribution + "}";
    }
}

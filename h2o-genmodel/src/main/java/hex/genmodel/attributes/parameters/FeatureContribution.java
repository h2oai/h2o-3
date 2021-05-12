package hex.genmodel.attributes.parameters;

import java.io.Serializable;

public class FeatureContribution implements Serializable{

    public final String columnName;
    public final double shapleyContribution;

    public FeatureContribution(String columnName, double shapleyContribution) {
        this.columnName = columnName;
        this.shapleyContribution = shapleyContribution;
    }

    @Override
    public String toString() {
        return "{ColumnName: " + columnName + ", ShapleyContribution: " + shapleyContribution + "}";
    }
}

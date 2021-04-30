package hex.genmodel.algos.rulefit;

import java.io.Serializable;

public class MojoCondition implements Serializable {

    public enum Type {Categorical, Numerical};
    public enum Operator {LessThan, GreaterThanOrEqual, In}
    int featureIndex;
    Type type;
    public Operator operator;
    public String featureName;
    public boolean NAsIncluded;
    public String languageCondition;
    public double numTreshold;
    public String[] languageCatTreshold;
    public int[] catTreshold;

    void map(double[] cs, byte[] out) {
        double col = cs[MojoCondition.this.featureIndex];
        byte newVal = 0;
        if (out[0] == (byte)0) {
            return;
        }
        boolean isNA = Double.isNaN(col);
        // check whether condition is fulfilled:
        if (MojoCondition.this.NAsIncluded && isNA) {
                newVal = 1;
        } else if (!isNA) {
            if (MojoCondition.Type.Numerical.equals(MojoCondition.this.type)) {
                if (MojoCondition.Operator.LessThan.equals(MojoCondition.this.operator)) {
                    if (col < MojoCondition.this.numTreshold) {
                        newVal = 1;
                    }
                } else if (MojoCondition.Operator.GreaterThanOrEqual.equals(MojoCondition.this.operator)) {
                    if (col >= MojoCondition.this.numTreshold) {
                        newVal = 1;
                    }
                }
            } else if (MojoCondition.Type.Categorical.equals(MojoCondition.this.type)) {
                for (int i = 0; i < MojoCondition.this.catTreshold.length; i++) {
                    if (MojoCondition.this.catTreshold[i] == col) {
                        newVal = 1;
                    }
                }
            }
        }
       out[0] = newVal;
    }
}

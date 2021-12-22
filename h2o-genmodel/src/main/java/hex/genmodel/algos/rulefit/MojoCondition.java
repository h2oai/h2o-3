package hex.genmodel.algos.rulefit;

import java.io.Serializable;

public class MojoCondition implements Serializable {

    public enum Type {Categorical, Numerical};
    public enum Operator {LessThan, GreaterThanOrEqual, In}
    int _featureIndex;
    Type _type;
    public Operator _operator;
    public String _featureName;
    public boolean _NAsIncluded;
    public String _languageCondition;
    public double _numThreshold;
    public String[] _languageCatThreshold;
    public int[] _catThreshold;

    void map(double[] cs, byte[] out) {
        double col = cs[MojoCondition.this._featureIndex];
        byte newVal = 0;
        if (out[0] == (byte)0) {
            return;
        }
        boolean isNA = Double.isNaN(col);
        // check whether condition is fulfilled:
        if (MojoCondition.this._NAsIncluded && isNA) {
                newVal = 1;
        } else if (!isNA) {
            if (MojoCondition.Type.Numerical.equals(MojoCondition.this._type)) {
                if (MojoCondition.Operator.LessThan.equals(MojoCondition.this._operator)) {
                    if (col < MojoCondition.this._numThreshold) {
                        newVal = 1;
                    }
                } else if (MojoCondition.Operator.GreaterThanOrEqual.equals(MojoCondition.this._operator)) {
                    if (col >= MojoCondition.this._numThreshold) {
                        newVal = 1;
                    }
                }
            } else if (MojoCondition.Type.Categorical.equals(MojoCondition.this._type)) {
                for (int i = 0; i < MojoCondition.this._catThreshold.length; i++) {
                    if (MojoCondition.this._catThreshold[i] == col) {
                        newVal = 1;
                    }
                }
            }
        }
       out[0] = newVal;
    }
}

package hex.genmodel.algos.rulefit;

import java.io.Serializable;

public class MojoRule implements Serializable {
    MojoCondition[] _conditions;
    double _predictionValue;
    String _languageRule;
    double _coefficient;
    String _varName;

    public void map(double[] cs, byte[] out) {
        for (MojoCondition c : _conditions) {
            c.map(cs, out);
        }
    }
}

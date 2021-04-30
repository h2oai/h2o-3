package hex.genmodel.algos.rulefit;

import java.io.Serializable;

public class MojoRule implements Serializable {
    MojoCondition[] conditions;
    double predictionValue;
    String languageRule;
    double coefficient;
    String varName;

    public void map(double[] cs, byte[] out) {
        for (MojoCondition c : conditions) {
            c.map(cs, out);
        }
    }
}

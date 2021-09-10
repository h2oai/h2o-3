package hex.genmodel.algos.rulefit;

import java.io.Serializable;
import java.util.Arrays;

public class MojoRuleEnsemble implements Serializable {
    
    MojoRule[][][] _orderedRules;

    public MojoRuleEnsemble(MojoRule[][][] orderedRules) {
        this._orderedRules = orderedRules;
    }
    
    public double[] transformRow(double[] row, int depth, int ntrees, String[] linearModelNames, String[][] linearModelDomains) {
        double[] transformedRow = new double[depth * ntrees];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < ntrees; j++) {
                MojoRule[] filteredOrderedRules = _orderedRules[i][j];
                transformedRow[i * ntrees + j] = decode(transform(row, _orderedRules[i][j]), filteredOrderedRules, linearModelNames, linearModelDomains);
            }
        }
        return transformedRow;
    }
    
    static double decode(double[] cs, MojoRule[] rules, String[] linearModelNames, String[][] linearModelDomains) {
        int newValue = -1;
        for (int iCol = 0; iCol < cs.length; iCol++) {
            if (cs[iCol] == 1) {
                newValue = getValueByVarName(rules[iCol]._varName, linearModelNames, linearModelDomains);
            }
        }
        if (newValue >= 0)
            return newValue;
        else
            return Double.NaN;
    }
    
    static int getValueByVarName(String varname, String[] linearModelNames, String[][] linearModelDomains) { 
        int i = Arrays.asList(linearModelNames).indexOf(varname.substring(0,varname.indexOf('N')));
        return Arrays.asList(linearModelDomains[i]).indexOf(varname);
    }

    static double[] transform(double[] row, MojoRule[] rules) {
        double[] transformedRow = new double[rules.length];
        byte[] out = new byte[] {1};
        for (int i = 0; i < rules.length; i++) {
            out[0] = 1;
            rules[i].map(row, out);
            transformedRow[i] = out[0];
        }
        return transformedRow;
    }
}

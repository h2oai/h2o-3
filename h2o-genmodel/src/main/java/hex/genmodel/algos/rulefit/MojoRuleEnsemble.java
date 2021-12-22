package hex.genmodel.algos.rulefit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MojoRuleEnsemble implements Serializable {
    
    MojoRule[][][] _orderedRules;

    public MojoRuleEnsemble(MojoRule[][][] orderedRules) {
        this._orderedRules = orderedRules;
    }
    
    public double[] transformRow(double[] row, int depth, int ntrees, String[] linearModelNames, String[][] linearModelDomains, String[] classes) {
        boolean isMultinomial = classes != null && classes.length > 2;
        double[] transformedRow = isMultinomial ? new double[depth * ntrees * classes.length] : new double[depth * ntrees];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < ntrees; j++) {
                MojoRule[] filteredOrderedRules = _orderedRules[i][j];
                if (isMultinomial) {
                    List<MojoRule>[] classRules = new ArrayList[classes.length];

                    for (int k = 0; k < filteredOrderedRules.length; k++) {
                        for (int l = 0; l < classes.length; l++) {
                            if (filteredOrderedRules[k]._varName.endsWith(classes[l])) {
                                if (classRules[l] == null) {
                                    classRules[l] = new ArrayList<>();
                                }
                                classRules[l].add(filteredOrderedRules[k]);
                            }
                        }
                    }

                    for (int k = 0; k < classes.length; k++) {
                        transformedRow[i * ntrees * classes.length + j * classes.length + k] = decode(transform(row, classRules[k].toArray(new MojoRule[0])), classRules[k].toArray(new MojoRule[0]), linearModelNames, linearModelDomains, k);
                    }
                } else {
                    transformedRow[i * ntrees + j] = decode(transform(row, _orderedRules[i][j]), filteredOrderedRules, linearModelNames, linearModelDomains, -1);
                }
            }
        }
        return transformedRow;
    }
    
    static double decode(double[] cs, MojoRule[] rules, String[] linearModelNames, String[][] linearModelDomains, int classId) {
        int newValue = -1;
        for (int iCol = 0; iCol < cs.length; iCol++) {
            if (cs[iCol] == 1) {
                newValue = getValueByVarName(rules[iCol]._varName, linearModelNames, linearModelDomains, classId);
            }
        }
        if (newValue >= 0)
            return newValue;
        else
            return Double.NaN;
    }
    
    static int getValueByVarName(String varname, String[] linearModelNames, String[][] linearModelDomains, int classId) { 
        String var = varname.substring(0,varname.indexOf('N'));
        if (classId != -1) {
            var +=  "C" + classId; 
        }
        int i = Arrays.asList(linearModelNames).indexOf(var);
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

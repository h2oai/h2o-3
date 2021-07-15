package hex.genmodel.algos.rulefit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MojoRuleEnsemble implements Serializable {
    
    MojoRule[] rules;

    public MojoRuleEnsemble(MojoRule[] rules) {
        this.rules = rules;
    }
    
    public double[] transformRow(double[] row, int depth, int ntrees, String[] linearModelNames, String[][] linearModelDomains) {
        double[] transformedRow = new double[depth * ntrees];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < ntrees; j++) {
                // filter rules according to varname
                // varname is of structue "M" + modelId + "T" + node.getSubgraphNumber() + "N" + node.getNodeNumber()
                String regex = "M" + i + "T" + j + "N" + "\\d+";
                List<MojoRule> filteredRules = new ArrayList<>();
                for (int k = 0; k < rules.length; k++) {
                    if (rules[k].varName.matches(regex)) {
                        filteredRules.add(rules[k]);
                    }
                }
                MojoRuleEnsemble ruleEnsemble = new MojoRuleEnsemble(filteredRules.toArray(new MojoRule[] {}));
                transformedRow[i * ntrees + j] = decode(ruleEnsemble.transform(row), filteredRules.toArray(new MojoRule[] {}), linearModelNames, linearModelDomains);
            }
        }
        return transformedRow;
    }
    
    double decode(double[] cs, MojoRule[] rules, String[] linearModelNames, String[][] linearModelDomains) {
        int newValue = -1;
        for (int iCol = 0; iCol < cs.length; iCol++) {
            if (cs[iCol] == 1) {
                newValue = getValueByVarName(rules[iCol].varName, linearModelNames, linearModelDomains);
            }
        }
        if (newValue >= 0)
            return newValue;
        else
            return Double.NaN;
    }
    
    int getValueByVarName(String varname, String[] linearModelNames, String[][] linearModelDomains) { 
        int i = Arrays.asList(linearModelNames).indexOf(varname.substring(0,varname.indexOf('N')));
        return Arrays.asList(linearModelDomains[i]).indexOf(varname);
    }
    
    double[] transform(double[] row) {
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

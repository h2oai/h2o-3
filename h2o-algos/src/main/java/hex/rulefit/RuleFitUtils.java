package hex.rulefit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RuleFitUtils {

    public static String[] getPathNames(int modelId, int numCols, String[] names) {
        String[] pathNames = new String[numCols];
        for (int i = 0; i < numCols; i++) {
            pathNames[i] = "tree_" + modelId + "." + names[i];
        }
        return pathNames;
    }

    public static String[] getLinearNames(int numCols, String[] names) {
        String[] pathNames = new String[numCols];
        for (int i = 0; i < numCols; i++) {
            pathNames[i] = "linear." + names[i];
        }
        return pathNames;
    }
    
    static Rule[] deduplicateRules(Rule[] rules, boolean remove_duplicates) {
        if (remove_duplicates) {
            List<Rule> list = Arrays.asList(rules);
    
            // group by non linear rules
            List<Rule> transform = list.stream()
                    .filter(rule -> rule.conditions != null)
                    .collect(Collectors.groupingBy(rule -> rule.languageRule))
                    .entrySet().stream()
                    .map(e -> e.getValue().stream()
                            .reduce((r1,r2) -> new Rule(r1.conditions, r1.predictionValue, r1.varName + ", " + r2.varName, r1.coefficient + r2.coefficient)))
                    .map(f -> f.get())
                    .collect(Collectors.toList());
    
            // add linear rules
            transform.addAll(list.stream().filter(rule -> rule.conditions == null).collect(Collectors.toList()));
    
            return transform.toArray(new Rule[0]);
        } else {
            return rules;
        }
    }

    static Rule[] sortRules(Rule[] rules) {
        Comparator<Rule> ruleAbsCoefficientComparator = Comparator.comparingDouble(Rule::getAbsCoefficient).reversed();
        Arrays.sort(rules, ruleAbsCoefficientComparator);
        return rules;
    }

    /** 
     * Returns a ruleId. 
     * If the ruleId is in form after deduplication:  "M0T0N1, M0T9N56, M9T34N56", meaning contains ", "
     * finds only first rule (other are equivalents)
     */
    static String readRuleId(String ruleId) {
        if (ruleId.contains(",")) {
            return ruleId.split(",")[0];
        } else {
            return ruleId;
        }
    }
}

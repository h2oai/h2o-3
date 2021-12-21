package hex.rulefit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

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

    static Rule[] consolidateRules(Rule[] rules, boolean remove_duplicates) {
        for (int i = 0; i < rules.length; i++) {
            if (rules[i].conditions != null) { // linear rules doesn't need to consolidate
                rules[i] = consolidateRule(rules[i], remove_duplicates);
            }
        }
        
        if (remove_duplicates)
            return deduplicateRules(rules);
        else
            return rules;
    }
    
    static Rule[] sortRules(Rule[] rules) {
        Comparator<Rule> ruleAbsCoefficientComparator = Comparator.comparingDouble(Rule::getAbsCoefficient).reversed();
        Arrays.sort(rules, ruleAbsCoefficientComparator);
        return rules;
    }

    static Rule consolidateRule(Rule rule, boolean remove_duplicates) {
        List<Condition> consolidatedConditions = new ArrayList<>();

        Condition[] conditions = rule.conditions;
        List<String> varNames = new ArrayList<>();
        for (int i = 0; i < conditions.length; i++) {
            if (!varNames.contains(conditions[i].featureName)) {
                varNames.add(conditions[i].featureName);
            }
        }
        for (int i = 0; i < varNames.size(); i++) {
            consolidatedConditions.addAll(consolidateConditionsByVar(conditions, varNames.get(i)));
        }
        
        if (remove_duplicates) {
            // sort by feature name as a preparation for rules deduplication
            rule.conditions = consolidatedConditions.stream()
                    .sorted(Comparator.comparing(condition -> condition.featureName))
                    .collect(Collectors.toList()).toArray(new Condition[0]);
        } else {
            rule.conditions = consolidatedConditions.toArray(new Condition[0]);
        }
        rule.languageRule = rule.generateLanguageRule();
        return rule;
    }

    static List<Condition>  consolidateConditionsByVar(Condition[] conditions, String varname) {
        List<Condition> currVarConditions = new ArrayList<>();
        for (int i = 0; i < conditions.length; i++) {
            if (varname.equals(conditions[i].featureName))
                currVarConditions.add(conditions[i]);
        }
        if (currVarConditions.size() == 1) {
            return currVarConditions;
        } else {
            Condition potentialLessThan = null;
            Condition potentialGreaterThanOrEqual = null;
            Condition potentialIn = null;
            
            for (int i = 0; i < currVarConditions.size(); i++) {
                Condition currCondition = currVarConditions.get(i);
                if (Condition.Operator.LessThan.equals(currCondition.operator)) {
                    if (potentialLessThan == null) {
                        potentialLessThan = currCondition;
                    } else {
                        potentialLessThan = potentialLessThan.expandBy(currCondition);
                    }
                } else if (Condition.Operator.GreaterThanOrEqual.equals(currCondition.operator)) {
                    if (potentialGreaterThanOrEqual == null) {
                        potentialGreaterThanOrEqual = currCondition;
                    } else {
                        potentialGreaterThanOrEqual = potentialGreaterThanOrEqual.expandBy(currCondition);
                    }
                } else {
                    assert Condition.Operator.In.equals(currCondition.operator);
                    if (potentialIn == null) {
                        potentialIn = currCondition;
                    } else {
                        potentialIn = potentialIn.expandBy(currCondition);
                    }
                }
            }

            List<Condition> currVarConsolidatedConditions = new ArrayList<>();

            if (potentialLessThan != null)
                currVarConsolidatedConditions.add(potentialLessThan);
            if (potentialGreaterThanOrEqual != null)
                currVarConsolidatedConditions.add(potentialGreaterThanOrEqual);
            if (potentialIn != null)
                currVarConsolidatedConditions.add(potentialIn);

            return currVarConsolidatedConditions;
        }
    }
    
    static Rule[] deduplicateRules(Rule[] rules) {
        List<Rule> list = Arrays.asList(rules);

        // group by non linear rules
        List<Rule> transform = list.stream()
                .filter(rule -> rule.conditions != null)
                .collect(Collectors.groupingBy(rule -> rule.languageRule))
                .entrySet().stream()
                .map(e -> e.getValue().stream()
                        .reduce((r1,r2) -> new Rule(r1.conditions, r1.predictionValue, r1.varName + ", " + r2.varName,
                                r1.coefficient + r2.coefficient, r1.support, 
                                 ruleImportance(r1.coefficient + r2.coefficient, r1.support))))
                .map(f -> f.get())
                .collect(Collectors.toList());

        // add linear rules
        transform.addAll(list.stream().filter(rule -> rule.conditions == null).collect(Collectors.toList()));

        return transform.toArray(new Rule[0]);
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

    static double ruleImportance(double lassoWeight, double support) {
        return abs(lassoWeight) * sqrt(support * (1 - support));
    }
}

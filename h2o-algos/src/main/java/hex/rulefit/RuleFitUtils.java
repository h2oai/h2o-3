package hex.rulefit;

import java.util.ArrayList;
import java.util.List;

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

    static Rule[] consolidateRules(Rule[] rules) {
        for (int i = 0; i < rules.length; i++) {
            rules[i] = consolidateRule(rules[i]);
        }
        return rules;
    }

    static Rule consolidateRule(Rule rule) {
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

        rule.conditions = consolidatedConditions.toArray(new Condition[0]);
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
}

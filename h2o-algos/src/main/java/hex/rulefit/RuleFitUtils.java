package hex.rulefit;

import water.util.TwoDimTable;

import java.util.*;
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
            List<Rule> transform = new ArrayList<>();
            for (int i = 0; i < rules.length; i++) {
                Rule currRule = rules[i];
                if (currRule.conditions != null) {
                    // non linear rules:
                    if (!transform.contains(currRule)) {
                        transform.add(currRule);
                    } else {
                        for (int j = 0; j < transform.size(); j++) {
                            if (i != j) {
                                Rule ruleToExtend = transform.get(j);
                                if (currRule.equals(ruleToExtend)) {
                                    transform.remove(j);
                                    Rule newRule = new Rule(ruleToExtend.conditions,  ruleToExtend.predictionValue, ruleToExtend.varName + ", " + currRule.varName,  ruleToExtend.coefficient + currRule.coefficient, ruleToExtend.support);
                                    transform.add(newRule);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    // linear rules:
                    transform.add(currRule);
                }
            }
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

    static Rule[] getRules(HashMap<String, Double> glmCoefficients, RuleEnsemble ruleEnsemble, String[] classNames, int nclasses) {
        // extract variable-coefficient map (filter out intercept and zero betas)
        Map<String, Double> filteredRules = glmCoefficients.entrySet()
                .stream()
                .filter(e -> !("Intercept".equals(e.getKey()) || e.getKey().contains("Intercept_")) && 0 != e.getValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<Rule> rules = new ArrayList<>();
        Rule rule;
        for (Map.Entry<String, Double> entry : filteredRules.entrySet()) {
            if (!entry.getKey().startsWith("linear.")) {
                rule = ruleEnsemble.getRuleByVarName(getVarName(entry.getKey(), classNames, nclasses));
            } else {
                rule = new Rule(null, entry.getValue(), entry.getKey());
                // linear rule applies to all the rows
                rule.support = 1.0;
            }
            rule.setCoefficient(entry.getValue());
            rules.add(rule);
        }

        return rules.toArray(new Rule[] {});
    }

    static private String getVarName(String ruleKey, String[] classNames, int nclasses) {
        if (nclasses > 2) {
            ruleKey = removeClassNameSuffix(ruleKey, classNames);
        }
        return ruleKey.substring(ruleKey.lastIndexOf(".") + 1);
    }

    private static String removeClassNameSuffix(String ruleKey, String[] classNames) {
        for (int i = 0; i < classNames.length; i++) {
            if (ruleKey.endsWith(classNames[i]))
                return ruleKey.substring(0, ruleKey.length() - classNames[i].length() - 1);
        }
        return ruleKey;
    }



    static  TwoDimTable convertRulesToTable(Rule[] rules, boolean isMultinomial, boolean generateLanguageRule) {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("variable");
        colTypes.add("string");
        colFormat.add("%s");
        if (isMultinomial) {
            colHeaders.add("class");
            colTypes.add("string");
            colFormat.add("%s");
        }
        colHeaders.add("coefficient");
        colTypes.add("double");
        colFormat.add("%.5f");
        colHeaders.add("support");
        colTypes.add("double");
        colFormat.add("%.5f");
        colHeaders.add("rule");
        colTypes.add("string");
        colFormat.add("%s");

        final int rows = rules.length;
        TwoDimTable table = new TwoDimTable("Rule Importance", null, new String[rows],
                colHeaders.toArray(new String[0]), colTypes.toArray(new String[0]), colFormat.toArray(new String[0]), "");

        for (int row = 0; row < rows; row++) {
            int col = 0;
            String varname = (rules[row]).varName;
            table.set(row, col++, varname);
            if (isMultinomial) {
                String segments[] = varname.split("_");
                table.set(row, col++, segments[segments.length - 1]);
            }
            table.set(row, col++, (rules[row]).coefficient);
            table.set(row, col++, (rules[row]).support);
            table.set(row, col, generateLanguageRule ? rules[row].generateLanguageRule() : rules[row].languageRule);
        }

        return table;
    }
}

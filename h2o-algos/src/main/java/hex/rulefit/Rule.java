package hex.rulefit;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.SharedTreeModel;
import org.apache.commons.math3.util.Precision;
import water.Iced;
import water.fvec.Chunk;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class Rule extends Iced {
    
    Condition[] conditions;
    double predictionValue;
    String languageRule;
    double coefficient;
    String varName;
    double support;

    public Rule(Condition[] conditions, double predictionValue, String varName) {
        this.conditions = conditions;
        this.predictionValue = predictionValue;
        this.varName = varName;
    }

    public Rule(Condition[] conditions, double predictionValue, String varName,  double coefficient, double support) {
        this.conditions = conditions;
        this.predictionValue = predictionValue;
        this.varName = varName; 
        this.coefficient = coefficient;
        this.support = support;
    }

    public void setCoefficient(double coefficient) {
        this.coefficient = coefficient;
    }

    public void setVarName(String varName) {
        this.varName = varName;
    }

    String generateLanguageRule() {
        StringBuilder languageRule = new StringBuilder();
        if (!this.varName.startsWith("linear.")) {
            for (int i = 0; i < conditions.length; i++) {
                if (i != 0) languageRule.append(" & ");
                languageRule.append(conditions[i].constructLanguageCondition());
            }
        }
        return languageRule.toString();
    }

    public void map(Chunk[] cs, byte[] out) {
        for (Condition c : conditions) {
            c.map(cs, out);
        }
    }

    // two non-linear rules are considered equal when they have the same condition but can differ in varname/pred value/coefficient
    // two linear rules (conditions == null) are considered equal when they have the same varname
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Rule))
            return false;

        Rule rule = (Rule) obj;
        if (this.conditions == null && rule.conditions == null) {
            if (this.varName == rule.varName) {
                return true;
            }
            return false;
        }
        if ((this.conditions == null && rule.conditions != null) || (this.conditions != null && rule.conditions == null)) {
            return false;
        }
        if (!Arrays.asList(rule.conditions).containsAll(Arrays.asList(conditions))) {
            return false;
        }

        return Math.abs(this.support - rule.support) < 1e-5;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(Precision.round(support, 5, BigDecimal.ROUND_HALF_UP));

        if (conditions != null) {
            Condition[] sorted = Arrays.asList(conditions).stream().sorted(
                    Comparator.comparing(Condition::getFeatureIndex)
                            .thenComparing(Condition::isNAsIncluded)
                            .thenComparing(Condition::getType)
                            .thenComparing(Condition::getOperator)
                            .thenComparing(Condition::getNumCatTreshold)
                            .thenComparing(Condition::getNumTreshold)
            ).collect(Collectors.toList()).toArray(new Condition[0]);

            result = 31 * result + Arrays.hashCode(sorted);
        } else {
            result = 31 * result + varName.hashCode();
        }
        return result;
    }

    public static List<Rule> extractRulesListFromModel(SharedTreeModel model, int modelId, int nclasses) {
        List<Rule> rules = new ArrayList<>();
        nclasses = nclasses > 2 ? nclasses : 1;
        for (int i = 0; i < ((SharedTreeModel.SharedTreeParameters) model._parms)._ntrees; i++) {
            for (int treeClass = 0; treeClass < nclasses; treeClass++) {
                SharedTreeSubgraph sharedTreeSubgraph = model.getSharedTreeSubgraph(i, treeClass);
                if (sharedTreeSubgraph == null)
                    continue;
                String classString = nclasses > 2 ? "_" + model._output.classNames()[treeClass] : null;
                rules.addAll(extractRulesFromTree(sharedTreeSubgraph, modelId, classString));
            }
        }

        return rules;
    }
    
    public static Set<Rule> extractRulesFromTree(SharedTreeSubgraph tree, int modelId, String classString) {
        Set<Rule> rules = new HashSet<>();
        // filter leaves
        List<SharedTreeNode> leaves = tree.nodesArray.stream().filter(sharedTreeNode -> sharedTreeNode.isLeaf()).collect(Collectors.toList());
        // traverse paths
        for (SharedTreeNode leaf : leaves) {
            String varName = "M" + modelId + "T" + leaf.getSubgraphNumber() + "N" + leaf.getNodeNumber();
            if (classString != null) {
                varName += classString;
            }
            traversePath(leaf, rules, varName, leaf.getPredValue());
        }
        return rules;
    }
    
    private static void traversePath(SharedTreeNode node, List<Condition> conditions, Set<Rule> rules, String varName, double predValue) {
        SharedTreeNode parent = node.getParent();
        if (parent == null) {
            conditions = conditions.stream().sorted(Comparator.comparing(condition -> condition.featureName)).collect(Collectors.toList());
            rules.add(new Rule(conditions.toArray(new Condition[]{}), predValue, varName));
        } else {
            Condition actualCondition;
            Condition newCondition;
            String featureName = parent.getColName();
            int colId = parent.getColId();
            if (node.getInclusiveLevels() != null && parent.getDomainValues() != null) {
                // categorical condition
                actualCondition = getConditionByFeatureNameAndOperator(conditions, parent.getColName(), Condition.Operator.In);
                CategoricalThreshold categoricalThreshold = extractCategoricalThreshold(node.getInclusiveLevels(), parent.getDomainValues());
                newCondition = new Condition(colId, Condition.Type.Categorical, Condition.Operator.In, -1, categoricalThreshold.catThreshold, categoricalThreshold.catThresholdNum, featureName, node.isInclusiveNa());
                
            } else {
                float splitValue = parent.getSplitValue();
                Condition.Operator operator = parent.getLeftChild().equals(node) ? Condition.Operator.LessThan : Condition.Operator.GreaterThanOrEqual;
                actualCondition = getConditionByFeatureNameAndOperator(conditions, parent.getColName(), operator);
                newCondition = new Condition(colId, Condition.Type.Numerical, operator, splitValue, null, null, featureName, node.isInclusiveNa());
            }
            if (actualCondition == null ) {
                conditions.add(newCondition);
            } else {
                actualCondition = actualCondition.expandBy(newCondition);
            }
            traversePath(node.getParent(), conditions, rules, varName, predValue);
        }
    }

    private static void traversePath(SharedTreeNode node, Set<Rule> rules, String varName, double predValue) {
        traversePath(node, new ArrayList<>(), rules, varName, predValue);
    }
    
    private static Condition getConditionByFeatureNameAndOperator(List<Condition> conditions, String featureName, Condition.Operator operator) {
        List<Condition> filteredConditions = conditions.stream().filter(condition -> condition.featureName.equals(featureName) && condition.operator.equals(operator)).collect(Collectors.toList());
        if (filteredConditions.size() != 0) {
            return filteredConditions.get(0);
        } else {
            return null;
        }
    }
    
    static CategoricalThreshold extractCategoricalThreshold(BitSet inclusiveLevels, String[] domainValues) {
        List<Integer> matchedDomainValues = new ArrayList<>();
        String[] catThreshold = new String[inclusiveLevels.cardinality()];
        int[] catThresholdNum = new int[inclusiveLevels.cardinality()];
        for (int i = inclusiveLevels.nextSetBit(0); i >= 0; i = inclusiveLevels.nextSetBit(i+1)) {
            matchedDomainValues.add(i);
        }
        for (int i = 0; i < catThreshold.length; i++) {
            catThreshold[i] = domainValues[matchedDomainValues.get(i)];
            catThresholdNum[i] = matchedDomainValues.get(i);
        }
        return new CategoricalThreshold(catThreshold, catThresholdNum);
    }

    static class CategoricalThreshold {
        String[] catThreshold;
        int[] catThresholdNum;

            public CategoricalThreshold(String[] catThreshold, int[] catThresholdNum) {
            this.catThreshold = catThreshold;
            this.catThresholdNum = catThresholdNum;
        }
    }

    double getAbsCoefficient() {
        return Math.abs(coefficient);
    }

}

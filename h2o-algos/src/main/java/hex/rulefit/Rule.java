package hex.rulefit;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.SharedTreeModel;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.*;

import static hex.tree.TreeUtils.getResponseLevelIndex;

public class Rule extends Iced {
    
    Condition[] conditions;
    double predictionValue;
    String languageRule;
    double coefficient;
    String varName;

    public Rule(Condition[] conditions, double predictionValue, String varName) {
        this.conditions = conditions;
        this.predictionValue = predictionValue;
        this.varName = varName;
        this.languageRule = generateLanguageRule();
        
    }

    public void setCoefficient(double coefficient) {
        this.coefficient = coefficient;
    }

    String generateLanguageRule() {
        StringBuilder languageRule = new StringBuilder();
        if (!this.varName.startsWith("linear.")) {
            for (int i = 0; i < conditions.length; i++) {
                if (i != 0) languageRule.append(" & ");
                languageRule.append(conditions[i].languageCondition);
            }
        }
        return languageRule.toString();
    }

    public Frame transform(Frame frame) {
        Frame frameToReduce = new Frame();
        for (int i = 0; i < conditions.length; i++) {
            frameToReduce.add(conditions[i].transform(frame));
        }
        RuleReducer mrtask = new RuleReducer();
        return mrtask.doAll(1, Vec.T_NUM, frameToReduce).outputFrame();

    }

    @Override
    public int hashCode() {
        int hashCode = 0;
        for (int i = 0; i < conditions.length; i++) {
            hashCode += conditions[i].hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        return this.hashCode() == obj.hashCode();
    }

    public static List<Rule> extractRulesListFromModel(SharedTreeModel model, int modelId) {
        List<Rule> rules = new ArrayList<>();
        final SharedTreeModel.SharedTreeOutput sharedTreeOutput = (SharedTreeModel.SharedTreeOutput) model._output;
        final int treeClass = getResponseLevelIndex(null, sharedTreeOutput);
        for (int i = 0; i < ((SharedTreeModel.SharedTreeParameters) model._parms)._ntrees; i++) {
            SharedTreeSubgraph sharedTreeSubgraph = model.getSharedTreeSubgraph(i, treeClass);
            rules.addAll(extractRulesFromTree(sharedTreeSubgraph, modelId));
        }

        return rules;
    }
    
    public static Set<Rule> extractRulesFromTree(SharedTreeSubgraph tree, int modelId) {
        Set<Rule> rules = new HashSet<>();
        List<Condition> conditions = new ArrayList<>();
        traverseNodes(tree.rootNode, conditions, rules, null, modelId);
        return rules;
    }
    
    private static void traverseNodes(SharedTreeNode node, List<Condition> conditions, Set<Rule> rules, Condition conditionToAdd, int modelId) {
        if (conditionToAdd != null) {
            conditions.add(conditionToAdd);
        }
        
        if (node.isLeaf()) {
            // create Rule
            rules.add(new Rule(conditions.toArray(new Condition[]{}), node.getPredValue(), "M" + modelId + "T" + node.getSubgraphNumber() + "N" + node.getNodeNumber()));
        } else {
            // traverse
            int colId = node.getColId();
            String colName = node.getColName();
            
            if (node.getDomainValues() == null) {
                float splitValue = node.getSplitValue();
                traverseNodes(node.getRightChild(), new ArrayList<>(conditions), rules, 
                        new Condition(colId, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, splitValue, null, null, colName, node.getRightChild().isInclusiveNa()), modelId);
                traverseNodes(node.getLeftChild(), new ArrayList<>(conditions), rules,
                        new Condition(colId, Condition.Type.Numerical, Condition.Operator.LessThan, splitValue, null, null, colName, node.getLeftChild().isInclusiveNa()), modelId);
            } else {
                String[] domainValues = node.getDomainValues();
                CategoricalThreshold rightCategoricalThreshold = extractCategoricalThreshold(node.getRightChild().getInclusiveLevels(), domainValues);
                traverseNodes(node.getRightChild(), new ArrayList<>(conditions), rules, 
                        new Condition(colId, Condition.Type.Categorical, Condition.Operator.In, -1, rightCategoricalThreshold.catThreshold, rightCategoricalThreshold.catThresholdNum, colName, node.getRightChild().isInclusiveNa()), modelId);
                CategoricalThreshold leftCategoricalThreshold = extractCategoricalThreshold(node.getLeftChild().getInclusiveLevels(), domainValues);
                traverseNodes(node.getLeftChild(), new ArrayList<>(conditions), rules,
                        new Condition(colId, Condition.Type.Categorical, Condition.Operator.In, -1, leftCategoricalThreshold.catThreshold, leftCategoricalThreshold.catThresholdNum, colName, node.getLeftChild().isInclusiveNa()), modelId);
            }
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

    static class RuleReducer extends MRTask<RuleReducer> {
        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
            int newVal;
            for (int iRow = 0; iRow < cs[0].len(); iRow++) {
                newVal = 1;
                for (int iCol = 0; iCol < cs.length; iCol++) {
                    newVal *= cs[iCol].at8(iRow);
                }
                ncs[0].addNum(newVal);
            }
        }
    }
}

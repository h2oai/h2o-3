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
        String languageRule = "";
        if (!this.varName.startsWith("linear.")) {
            for (int i = 0; i < conditions.length; i++) {
                languageRule += conditions[i].languageCondition;
                if (i != conditions.length - 1)
                    languageRule += " & ";
            }
        }
        return languageRule;
    }

    public Frame transform(Frame frame) {
        Frame frameToReduce = new Frame();
        for (int i = 0; i < conditions.length; i++) {
            frameToReduce.add(conditions[i].transform(frame));
        }
        RuleReducer mrtask = new RuleReducer();
        return mrtask.doAll(1, Vec.T_NUM, frameToReduce).outputFrame();

    }
    /*-----comment when runing wth list*/
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
    /*-----comment when runing wth list*/
    
    public static Set<Rule> extractRulesFromModel(SharedTreeModel model, int modelId) {
        Set<Rule> rules = new HashSet<>();
        final SharedTreeModel.SharedTreeOutput sharedTreeOutput = (SharedTreeModel.SharedTreeOutput) model._output;
        final int treeClass = getResponseLevelIndex(null, sharedTreeOutput);
        for (int i = 0; i < ((SharedTreeModel.SharedTreeParameters) model._parms)._ntrees; i++) {
        SharedTreeSubgraph sharedTreeSubgraph = model.getSharedTreeSubgraph(i, treeClass);
        rules.addAll(extractRulesFromTree(sharedTreeSubgraph, modelId));
        }
        
        return rules;
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
        rules = traverseNodes(tree.rootNode, conditions, rules, null, modelId);
        return rules;
    }

    public static List<Rule> extractRulesListFromTree(SharedTreeSubgraph tree, int modelId) {
        List<Rule> rules = new ArrayList<>();
        List<Condition> conditions = new ArrayList<>();
        rules = new ArrayList(traverseNodes(tree.rootNode, conditions, new HashSet<Rule>(), null, modelId));
        return rules;
    }
    
    private static Set<Rule> traverseNodes(SharedTreeNode node, List<Condition> conditions, Set<Rule> rules, Condition conditionToAdd, int modelId) {
        if (conditionToAdd != null)
            conditions.add(conditionToAdd);
        
        // extract this node
        Condition.Type type;
        Condition.Operator operator;
        String[] catTreshold;
        
        if (node.isLeaf()) {
            // create Rule to return
            rules.add(new Rule(conditions.toArray(new Condition[]{}), node.getPredValue(), "M" + modelId + "T" + node.getSubgraphNumber() + "N" + node.getNodeNumber()));
            return rules;
        } else {
            // extract condition leading to the right and recurse there
            if (node.getDomainValues() == null) {
                type = Condition.Type.Numerical;
                operator = Condition.Operator.GreaterThanOrEqual;
                rules.addAll(traverseNodes(node.getRightChild(), new ArrayList<>(conditions), rules, new Condition(node.getColId(), type, operator,  node.getSplitValue(), null, node.getColName(), node.getRightChild().isInclusiveNa()), modelId));
            } else {
                type = Condition.Type.Categorical;
                operator = Condition.Operator.In;
                catTreshold = new String[node.getDomainValues().length - (int) node.getSplitValue() - 1];
                for (int i = 0; i < catTreshold.length; i++) {
                    catTreshold[i] = node.getDomainValues()[i + (int) node.getSplitValue() + 1];
                }
                rules.addAll(traverseNodes(node.getRightChild(), new ArrayList<>(conditions), rules, new Condition(node.getColId(), type, operator, -1, catTreshold, node.getColName(), node.getRightChild().isInclusiveNa()), modelId));
            }
               
            // extract condition leading to the left and recurse there
            if (node.getDomainValues() == null) {
                type = Condition.Type.Numerical;
                operator = Condition.Operator.LessThan;
                rules.addAll(traverseNodes(node.getLeftChild(), new ArrayList<>(conditions), rules, new Condition(node.getColId(), type, operator, node.getSplitValue(), null, node.getColName(), node.getLeftChild().isInclusiveNa()), modelId));
            } else {
                type = Condition.Type.Categorical;
                operator = Condition.Operator.In;
                catTreshold = new String[(int) node.getSplitValue() + 1];
                for (int i = 0; i < catTreshold.length; i++) {
                    catTreshold[i] = node.getDomainValues()[i];
                }
                rules.addAll(traverseNodes(node.getLeftChild(), new ArrayList<>(conditions), rules, new Condition(node.getColId(), type, operator, -1, catTreshold, node.getColName(), node.getLeftChild().isInclusiveNa()), modelId));
            }
            
        }
        return rules;
    }

    double getAbsCoefficient() {
        return Math.abs(coefficient);
    }
}

class RuleReducer extends MRTask<RuleReducer> {
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

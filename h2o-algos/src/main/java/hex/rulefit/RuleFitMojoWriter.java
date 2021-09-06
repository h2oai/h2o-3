package hex.rulefit;

import hex.Model;
import hex.MultiModelMojoWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class RuleFitMojoWriter extends MultiModelMojoWriter<RuleFitModel,
        RuleFitModel.RuleFitParameters, RuleFitModel.RuleFitOutput> {

    @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
    public RuleFitMojoWriter() {}

    public RuleFitMojoWriter(RuleFitModel model) {
        super(model);
    }

    @Override
    public String mojoVersion() {
        return "1.00";
    }

    @Override
    protected List<Model> getSubModels() {
        LinkedList<Model> subModels = new LinkedList<>();
        if (model.glmModel != null) {
            subModels.add(model.glmModel);
        }
        return subModels;
    }
    
    @Override
    protected void writeParentModelData() throws IOException {
        writekv("linear_model", model._output.glmModelKey);
        if (!model._parms._model_type.equals(RuleFitModel.ModelType.LINEAR)) {
            writeOrderedRuleEnsemble(model.ruleEnsemble, model._parms._max_rule_length - model._parms._min_rule_length + 1, model._parms._rule_generation_ntrees);
        }
        if (model._parms._model_type.equals(RuleFitModel.ModelType.LINEAR)) {
            writekv("model_type", 0);
        } else if (model._parms._model_type.equals(RuleFitModel.ModelType.RULES_AND_LINEAR)) {
            writekv("model_type", 1);
        } else {
            writekv("model_type", 2);
        }
        writekv("type", model._output.glmModelKey);
        writekv("depth", model._parms._max_rule_length - model._parms._min_rule_length + 1);
        writekv("ntrees", model._parms._rule_generation_ntrees);
        writekv("data_from_rules_codes_len", model.dataFromRulesCodes.length);
        for (int i = 0; i < model.dataFromRulesCodes.length; i++) {
            writekv("data_from_rules_codes_" + i, model.dataFromRulesCodes[i]);
        }
        
        if (model._parms._weights_column != null) {
            writekv("weights_column", model._parms._weights_column);
        }

        writekv("linear_names_len", model._output._linear_names.length);
        for (int i = 0; i < model._output._linear_names.length; i++) {
            writekv("linear_names_" + i, model._output._linear_names[i]);
        }
    }

    void writeOrderedRuleEnsemble(RuleEnsemble ruleEnsemble, int depth, int ntrees) throws IOException {
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < ntrees; j++) {
                // filter rules according to varname
                // varname is of structue "M" + modelId + "T" + node.getSubgraphNumber() + "N" + node.getNodeNumber()
                String regex = "M" + i + "T" + j + "N" + "\\d+";
                List<Rule> filteredRules = new ArrayList<>();
                for (int k = 0; k < ruleEnsemble.rules.length; k++) {
                    if (ruleEnsemble.rules[k].varName.matches(regex)) {
                        filteredRules.add(ruleEnsemble.rules[k]);
                    }
                }
                int currNumRules = filteredRules.size();
                writekv("num_rules_M".concat(String.valueOf(i)).concat("T").concat(String.valueOf(j)), currNumRules);
                String currIdPrefix = String.valueOf(i).concat("_").concat(String.valueOf(j)).concat("_");
                for (int k = 0; k < currNumRules; k++) {
                    writeRule(filteredRules.get(k), currIdPrefix.concat(String.valueOf(k)));
                }
            }
        }
    }

    void writeRule(Rule rule, String ruleId) throws IOException {
        int numConditions = rule.conditions.length;
        writekv("num_conditions_rule_id_" + ruleId, numConditions);
        for (int i = 0; i < numConditions; i++) {
            writeCondition(rule.conditions[i], i, ruleId);
        }
        writekv("prediction_value_rule_id_" + ruleId, rule.predictionValue);
        writekv("language_rule_rule_id_" + ruleId, rule.languageRule);
        writekv("coefficient_rule_id_" + ruleId, rule.coefficient);
        writekv("var_name_rule_id_" + ruleId, rule.varName);
    }

    void writeCondition(Condition condition, int conditionId, String ruleId) throws IOException {
        String conditionIdentifier = conditionId + "_" + ruleId;
        writekv("feature_index_" + conditionIdentifier, condition.featureIndex);
        if (Condition.Type.Categorical.equals(condition.type)) {
            writekv("type_" + conditionIdentifier, 0);
            int languageCatTresholdLength = condition.languageCatTreshold.length;
            writekv("language_cat_treshold_length_" + conditionIdentifier, languageCatTresholdLength);
            for (int i = 0; i < languageCatTresholdLength; i++) {
                writekv("language_cat_treshold_" + i + "_" + conditionIdentifier, condition.languageCatTreshold[i]);
            }
            int catTresholdLength = condition.catTreshold.length;
            writekv("cat_treshold_length_" + conditionIdentifier, catTresholdLength);
            for (int i = 0; i < catTresholdLength; i++) {
                writekv("cat_treshold_length_" + i + "_" + conditionIdentifier, condition.catTreshold[i]);
            }
        } else {
            writekv("type_" + conditionIdentifier, 1); // Numerical
            writekv("num_treshold" + conditionIdentifier, condition.numTreshold);
        }
        if (Condition.Operator.LessThan.equals(condition.operator)) {
            writekv("operator_" + conditionIdentifier, 0);
        } else if (Condition.Operator.GreaterThanOrEqual.equals(condition.operator)) {
            writekv("operator_" + conditionIdentifier, 1);
        } else {
            writekv("operator_" + conditionIdentifier, 2); // In
        }
        writekv("feature_name_" + conditionIdentifier, condition.featureName);
        writekv("nas_included_" + conditionIdentifier, condition.NAsIncluded);
        writekv("language_condition" + conditionIdentifier, condition.languageCondition);
        
        
       
    }
}

package hex.genmodel.algos.rulefit;

import hex.genmodel.MultiModelMojoReader;
import java.io.IOException;

public class RuleFitMojoReader extends MultiModelMojoReader<RuleFitMojoModel> {

  @Override
  protected void readParentModelData() throws IOException {
    _model._linearModel = getModel((String) readkv("linear_model"));
    _model.model_type = readkv("model_type");
    _model.depth = readkv("depth");
    _model.ntrees = readkv("ntrees");

    if (_model.model_type != 0) {
      _model._ruleEnsemble = readRuleEnseble();
    }
    
    int len = readkv("data_from_rules_codes_len");
    _model.dataFromRulesCodes = new String[len];
    for (int i = 0; i < len; i++) {
      _model.dataFromRulesCodes[i] = readkv("data_from_rules_codes_" + i);
    }
    _model.weights_column =  readkv("weights_column");
    len = readkv("linear_names_len");
    _model.linear_names = new String[len];
    for (int i = 0; i < len; i++) {
      _model.linear_names[i] = readkv("linear_names_" + i);
    }

  }

  @Override
  public String getModelName() {
    return "rulefit";
  }

  @Override
  protected RuleFitMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new RuleFitMojoModel(columns, domains, responseColumn);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

  MojoRuleEnsemble readRuleEnseble() throws IOException {
    MojoRuleEnsemble ruleEnsemble = new MojoRuleEnsemble(readOrderedRuleEnseble());
    return ruleEnsemble;
  }
  
  MojoRule[][][] readOrderedRuleEnseble() throws IOException {
    MojoRule[][][] orderedRules = new MojoRule[_model.depth][_model.ntrees][];

    for (int i = 0; i < _model.depth; i++) {
      for (int j = 0; j < _model.ntrees; j++) {
        int currNumRules = readkv("num_rules_M".concat(String.valueOf(i)).concat("T").concat(String.valueOf(j)));
        MojoRule[] currRules = new MojoRule[currNumRules];
        String currIdPrefix = String.valueOf(i).concat("_").concat(String.valueOf(j)).concat("_");
        for (int k = 0; k < currNumRules; k++) {
          currRules[k] = readRule(currIdPrefix.concat(String.valueOf(k)));
        }
        orderedRules[i][j] = currRules;
      }
    }
    return orderedRules;
  }
  
  MojoRule readRule(String ruleId) throws IOException {
    MojoRule rule = new MojoRule();
    int numConditions = readkv("num_conditions_rule_id_" + ruleId);
    MojoCondition[] conditions = new MojoCondition[numConditions];
    for (int i = 0; i < numConditions; i++) {
      conditions[i] = readCondition(i, ruleId);
    }
    rule.conditions = conditions;
    rule.predictionValue = readkv("prediction_value_rule_id_" + ruleId);
    rule.languageRule = readkv("language_rule_rule_id_" + ruleId);
    rule.coefficient = readkv("coefficient_rule_id_" + ruleId);
    rule.varName = readkv("var_name_rule_id_" + ruleId);
    return rule;
  }
  
  MojoCondition readCondition(int conditionId, String ruleId) {
    MojoCondition condition = new MojoCondition();
    String conditionIdentifier = conditionId + "_" + ruleId;
    condition.featureIndex = readkv("feature_index_" + conditionIdentifier);
    int type = readkv("type_" + conditionIdentifier);
    if (type == 0) {
      condition.type = MojoCondition.Type.Categorical;
      int languageCatTresholdLength = readkv("language_cat_treshold_length_" + conditionIdentifier);
      String[] languageCatTreshold = new String[languageCatTresholdLength];
      for (int i = 0; i < languageCatTresholdLength; i++) {
        languageCatTreshold[i] = readkv("language_cat_treshold_" + i + "_" + conditionIdentifier).toString();
      }
      condition.languageCatTreshold = languageCatTreshold;
      int catTresholdLength = readkv("cat_treshold_length_" + conditionIdentifier);
      int[] catTreshold = new int[catTresholdLength];
      for (int i = 0; i < catTresholdLength; i++) {
        catTreshold[i] = readkv("cat_treshold_length_" + i + "_" + conditionIdentifier);
      }
      condition.catTreshold = catTreshold;
    } else {
      condition.type = MojoCondition.Type.Numerical;
      condition.numTreshold = readkv("num_treshold" + conditionIdentifier);
    }
    int operator = readkv("operator_" + conditionIdentifier);
    if (operator == 0) {
      condition.operator = MojoCondition.Operator.LessThan;
    } else if (operator == 1) {
      condition.operator = MojoCondition.Operator.GreaterThanOrEqual;
    } else {
      condition.operator = MojoCondition.Operator.In;
    }
    condition.featureName = readkv("feature_name_" + conditionIdentifier);
    condition.NAsIncluded = readkv("nas_included_" + conditionIdentifier);
    condition.languageCondition = readkv("language_condition" + conditionIdentifier);
    
    return condition;
  }
}

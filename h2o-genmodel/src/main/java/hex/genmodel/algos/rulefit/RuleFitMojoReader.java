package hex.genmodel.algos.rulefit;

import hex.genmodel.MultiModelMojoReader;
import java.io.IOException;

public class RuleFitMojoReader extends MultiModelMojoReader<RuleFitMojoModel> {

  @Override
  protected String getModelMojoReaderClassName() { return "hex.rulefit.RuleFitMojoWriter"; }

  @Override
  protected void readParentModelData() throws IOException {
    _model._linearModel = getModel((String) readkv("linear_model"));
    int modelType = readkv("model_type");
    if (modelType == 0) {
      _model._modelType = RuleFitMojoModel.ModelType.LINEAR;
    } else if (modelType == 1) {
      _model._modelType = RuleFitMojoModel.ModelType.RULES_AND_LINEAR;
    } else {
      _model._modelType = RuleFitMojoModel.ModelType.RULES;
    }
    
    _model._depth = readkv("depth");
    _model._ntrees = readkv("ntrees");

    if (!_model._modelType.equals(RuleFitMojoModel.ModelType.LINEAR)) {
      _model._ruleEnsemble = readRuleEnseble();
    }
    
    int len = readkv("data_from_rules_codes_len");
    _model._dataFromRulesCodes = new String[len];
    for (int i = 0; i < len; i++) {
      _model._dataFromRulesCodes[i] = readkv("data_from_rules_codes_" + i);
    }
    _model._weightsColumn =  readkv("weights_column");
    len = readkv("linear_names_len");
    _model._linearNames = new String[len];
    for (int i = 0; i < len; i++) {
      _model._linearNames[i] = readkv("linear_names_" + i);
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
    MojoRule[][][] orderedRules = new MojoRule[_model._depth][_model._ntrees][];

    for (int i = 0; i < _model._depth; i++) {
      for (int j = 0; j < _model._ntrees; j++) {
        int currNumRules = readkv("num_rules_M".concat(String.valueOf(i)).concat("T").concat(String.valueOf(j)));
        MojoRule[] currRules = new MojoRule[currNumRules];
        String currIdPrefix = i + "_" + j + "_";
        for (int k = 0; k < currNumRules; k++) {
          currRules[k] = readRule(currIdPrefix + k);
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
    rule._conditions = conditions;
    rule._predictionValue = readkv("prediction_value_rule_id_" + ruleId);
    rule._languageRule = readkv("language_rule_rule_id_" + ruleId);
    rule._coefficient = readkv("coefficient_rule_id_" + ruleId);
    rule._varName = readkv("var_name_rule_id_" + ruleId);
    if (readkv("support_rule_id_" + ruleId) != null) 
      rule._support = readkv("support_rule_id_" + ruleId);
    else
      rule._support = Double.NaN;
    return rule;
  }
  
  MojoCondition readCondition(int conditionId, String ruleId) {
    MojoCondition condition = new MojoCondition();
    String conditionIdentifier = conditionId + "_" + ruleId;
    condition._featureIndex = readkv("feature_index_" + conditionIdentifier);
    int type = readkv("type_" + conditionIdentifier);
    if (type == 0) {
      condition._type = MojoCondition.Type.Categorical;
      int languageCatTresholdLength = readkv("language_cat_treshold_length_" + conditionIdentifier);
      String[] languageCatTreshold = new String[languageCatTresholdLength];
      for (int i = 0; i < languageCatTresholdLength; i++) {
        languageCatTreshold[i] = readkv("language_cat_treshold_" + i + "_" + conditionIdentifier).toString();
      }
      condition._languageCatThreshold = languageCatTreshold;
      int catTresholdLength = readkv("cat_treshold_length_" + conditionIdentifier);
      int[] catTreshold = new int[catTresholdLength];
      for (int i = 0; i < catTresholdLength; i++) {
        catTreshold[i] = readkv("cat_treshold_length_" + i + "_" + conditionIdentifier);
      }
      condition._catThreshold = catTreshold;
    } else {
      condition._type = MojoCondition.Type.Numerical;
      condition._numThreshold = readkv("num_treshold" + conditionIdentifier);
    }
    int operator = readkv("operator_" + conditionIdentifier);
    if (operator == 0) {
      condition._operator = MojoCondition.Operator.LessThan;
    } else if (operator == 1) {
      condition._operator = MojoCondition.Operator.GreaterThanOrEqual;
    } else {
      condition._operator = MojoCondition.Operator.In;
    }
    condition._featureName = readkv("feature_name_" + conditionIdentifier);
    condition._NAsIncluded = readkv("nas_included_" + conditionIdentifier);
    condition._languageCondition = readkv("language_condition" + conditionIdentifier);
    
    return condition;
  }
}

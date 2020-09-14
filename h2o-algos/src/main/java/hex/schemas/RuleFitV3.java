package hex.schemas;

import hex.rulefit.RuleFit;
import hex.rulefit.RuleFitModel;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;


public class RuleFitV3 extends ModelBuilderSchema<RuleFit, RuleFitV3, RuleFitV3.RuleFitParametersV3> {

  public static final class RuleFitParametersV3 extends ModelParametersSchemaV3<RuleFitModel.RuleFitParameters, RuleFitParametersV3> {
     public static final String[] fields = new String[] {
            "model_id",
            "training_frame",
            "seed",
            "response_column", 
            "ignored_columns",
            "algorithm",
            "min_rule_length", 
            "max_rule_length",
            "max_num_rules",
            "model_type",
            "weights_column", 
            "distribution",
            "rule_generation_ntrees"
    };

    @API(help = "Seed for pseudo random number generator (if applicable).", gridable = true)
    public long seed;

    // Input fields
    @API(help = "The algorithm to use to generate rules.",
            values = {"AUTO", "DRF", "GBM"})
    public RuleFitModel.Algorithm algorithm;
    
    @API(help = "Minimum length of rules. Defaults to 1.")
    public int min_rule_length;

    @API(help = "Maximum length of rules. Defaults to 10.")
    public int max_rule_length;

    @API(help = "The maximum number of rules to return. defaults to -1 which means the number of rules is selected \n" +
            "by diminishing returns in model deviance.")
    public int max_num_rules;
    
    @API(help = "Specifies type of base learners in the ensemble.", values = {"RULES_AND_LINEAR", "RULES", "LINEAR"})
    public RuleFitModel.ModelType model_type;

    @API(help = "specifies the number of trees to build in the tree model. Defaults to 50.")
    public int rule_generation_ntrees;
  }
}

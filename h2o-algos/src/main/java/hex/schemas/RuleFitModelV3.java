package hex.schemas;

import hex.rulefit.RuleFitModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;

public class RuleFitModelV3 extends ModelSchemaV3<RuleFitModel, RuleFitModelV3, RuleFitModel.RuleFitParameters, RuleFitV3.RuleFitParametersV3,
        RuleFitModel.RuleFitOutput, RuleFitModelV3.RuleFitModelOutputV3> {
  public static final class RuleFitModelOutputV3 extends ModelOutputSchemaV3<RuleFitModel.RuleFitOutput, RuleFitModelOutputV3> {

    // Output
    @API(help = "The estimated coefficients and language representations (in case it is a rule) for each of the significant baselearners.")
    public TwoDimTableV3 rule_importance;

    @API(help = "Intercept.")
    public double[] intercept;
  }

  public RuleFitV3.RuleFitParametersV3 createParametersSchema() { return new RuleFitV3.RuleFitParametersV3();}
  public RuleFitModelOutputV3 createOutputSchema() { return new RuleFitModelOutputV3();}

  @Override
  public RuleFitModel createImpl() {
    RuleFitModel.RuleFitParameters parms = parameters.createImpl();
    return new RuleFitModel(model_id.key(), parms, null, null, null);
  }
}

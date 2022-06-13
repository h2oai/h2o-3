package water.api.schemas3;

import water.Iced;
import water.api.API;

public class SignificantRulesV3 extends RequestSchemaV3<Iced, SignificantRulesV3> {

  @API(help="Model id of interest", json = false)
  public KeyV3.ModelKeyV3 model_id;
  
  @API(help="The estimated coefficients and language representations (in case it is a rule) for each of the significant baselearners.", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 significant_rules_table;
}

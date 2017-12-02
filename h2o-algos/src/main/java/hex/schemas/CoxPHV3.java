package hex.schemas;

import hex.coxph.CoxPH;
import hex.coxph.CoxPHModel.CoxPHParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;

public class CoxPHV3 extends ModelBuilderSchema<CoxPH,CoxPHV3,CoxPHV3.CoxPHParametersV3> {
  public static final class CoxPHParametersV3 extends ModelParametersSchemaV3<CoxPHParameters, CoxPHParametersV3> {
    public static String[] fields = new String[] {
              "model_id",
              "training_frame",
              "start_column",
              "stop_column",
              "response_column",
              "ignored_columns",
              "weights_column",
              "offset_column",
              "ties",
              "init",
              "lre_min",
              "iter_max"
      };

    @API(help="stop_column", direction = API.Direction.INOUT)
    public String stop_column;

    @API(help="start_column", direction = API.Direction.INOUT)
    public String start_column;

    @API(help="ties", values = {"efron", "breslow"}, direction = API.Direction.INOUT)
    public CoxPHParameters.CoxPHTies ties;

    @API(help="init", direction = API.Direction.INOUT)
    public double init;

    @API(help="lre_min", direction = API.Direction.INOUT)
    public double lre_min;

    @API(help="iter_max", direction = API.Direction.INOUT)
    public int iter_max;

  }
}
package hex.schemas;

import hex.coxph.CoxPH;
import hex.coxph.CoxPHModel.CoxPHParameters;
import water.api.API;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.StringPairV3;

public class CoxPHV3 extends ModelBuilderSchema<CoxPH,CoxPHV3,CoxPHV3.CoxPHParametersV3> {
  public static final class CoxPHParametersV3 extends ModelParametersSchemaV3<CoxPHParameters, CoxPHParametersV3> {
    public static String[] fields = new String[] {
              "model_id",
              "training_frame",
              "rcall",
              "start_column",
              "stop_column",
              "response_column",
              "ignored_columns",
              "weights_column",
              "offset_column",
              "stratify_by",
              "ties",
              "init",
              "lre_min",
              "iter_max",
              "interactions_only",
              "interactions",
              "interaction_pairs",
              "use_all_factor_levels"
    };

    @API(help="How was the model invoked.", direction = API.Direction.INOUT)
    public String rcall;

    @API(help="Start Time Column.", direction = API.Direction.INOUT)
    public String start_column;

    @API(help="Stop Time Column.", direction = API.Direction.INOUT)
    public String stop_column;

    @API(help="List of columns to use for stratification.", direction = API.Direction.INOUT)
    public String[] stratify_by;

    @API(help="Method for Handling Ties.", values = {"efron", "breslow"}, direction = API.Direction.INOUT)
    public CoxPHParameters.CoxPHTies ties;

    @API(help="Coefficient starting value.", direction = API.Direction.INOUT)
    public double init;

    @API(help="Minimum log-relative error.", direction = API.Direction.INOUT)
    public double lre_min;

    @API(help="Maximum number of iterations.", direction = API.Direction.INOUT)
    public int iter_max;

    @API(help="A list of columns that should only be used to create interactions but should not itself participate in model training.", direction=API.Direction.INPUT, level=API.Level.expert)
    public String[] interactions_only;

    @API(help="A list of predictor column indices to interact. All pairwise combinations will be computed for the list.", direction= API.Direction.INPUT, level= API.Level.expert)
    public String[] interactions;

    @API(help="A list of pairwise (first order) column interactions.", direction= API.Direction.INPUT, level= API.Level.expert)
    public StringPairV3[] interaction_pairs;

    @API(help="(Internal. For development only!) Indicates whether to use all factor levels.", direction = API.Direction.INPUT)
    public boolean use_all_factor_levels;

  }
}
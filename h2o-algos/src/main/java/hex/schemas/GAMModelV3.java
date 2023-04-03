package hex.schemas;

import hex.gam.GAMModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;

public class GAMModelV3 extends ModelSchemaV3<GAMModel, GAMModelV3, GAMModel.GAMParameters, GAMV3.GAMParametersV3,
        GAMModel.GAMModelOutput, GAMModelV3.GAMModelOutputV3> {
  public static final class GAMModelOutputV3 extends ModelOutputSchemaV3<GAMModel.GAMModelOutput, GAMModelOutputV3> {
    @API(help="Table of Coefficients")
    TwoDimTableV3 coefficients_table;
    
    @API(help="Table of Coefficients without centering")
    TwoDimTableV3 coefficients_table_no_centering;

    @API(help="GLM scoring history")
    TwoDimTableV3 glm_scoring_history;
    
    @API(help = "GLM model summary")
    TwoDimTableV3 glm_model_summary;

    @API(help="Table of Standardized Coefficients Magnitudes")
    TwoDimTableV3 standardized_coefficient_magnitudes;

    @API(help="Variable Importances", direction=API.Direction.OUTPUT, level = API.Level.secondary)
    TwoDimTableV3 variable_importances;

    @API(help="key storing gam columns and predictor columns.  For debugging purposes only")
    String gam_transformed_center_key;
    
    @API(help="GLM Z values.  For debugging purposes only")
    double[] glm_zvalues;

    @API(help="GLM p values.  For debugging purposes only")
    double[] glm_pvalues;

    @API(help="GLM standard error values.  For debugging purposes only")
    double[] glm_std_err;
    
    @API(help = "knot locations for all gam columns.")
    double[][] knot_locations;

    @API(help = "Gam column names for knots stored in knot_locations")
    String[] gam_knot_column_names;
  }

  public GAMV3.GAMParametersV3 createParametersSchema() { return new GAMV3.GAMParametersV3();}
  public GAMModelOutputV3 createOutputSchema() { return new GAMModelOutputV3();}

  @Override
  public GAMModel createImpl() {
    GAMModel.GAMParameters parms = parameters.createImpl();
    return new GAMModel(model_id.key(), parms, null);
  }
}

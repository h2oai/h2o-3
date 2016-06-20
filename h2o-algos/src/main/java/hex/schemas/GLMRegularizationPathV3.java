package hex.schemas;

import hex.glm.GLMModel;
import water.api.API;
import water.api.KeyV3;
import water.api.SchemaV3;

/**
 */
public class GLMRegularizationPathV3  extends SchemaV3<GLMModel.RegularizationPath,GLMRegularizationPathV3>{
  @API(help="source model", required = true, direction = API.Direction.INPUT)
  public KeyV3.ModelKeyV3 model;
  @API(help="Computed lambda values")
  public double [] lambdas;
  @API(help="explained deviance on the training set")
  public double [] explained_deviance_train;
  @API(help="explained deviance on the validation set")
  public double [] explained_deviance_valid;
  @API(help="coefficients for all lambdas")
  public double [][] coefficients;
  @API(help="standardized coefficients for all lambdas")
  public double [][] coefficients_std;
  @API(help="coefficient names")
  public String [] coefficient_names;
}

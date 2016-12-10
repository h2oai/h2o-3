package water.api.schemas4;

import hex.ModelBuilder;
import water.api.API;

/**
 * Lightweight information profile about each model.
 */
public class ModelInfoV4 extends OutputSchemaV4<ModelBuilder, ModelInfoV4> {

  @API(help="Algorithm name, such as 'gbm', 'deeplearning', etc.")
  public String algo;

  @API(help="Development status of the algorithm: alpha, beta, or stable.")
  public String maturity;

  @API(help="Does the model support generation of POJOs?")
  public boolean have_pojo;

  @API(help="Does the model support generation of MOJOs?")
  public boolean have_mojo;

  @API(help="Mojo version number for this algorithm.")
  public String mojo_version;
}

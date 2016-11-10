package water.api.schemas4;

import water.Iced;
import water.api.API;

/**
 * List of models, returned by GET /4/modelsinfo
 */
public class ModelsInfoV4 extends OutputSchemaV4<Iced, ModelsInfoV4> {

  @API(help="Generic information about each model supported in H2O.")
  public ModelInfoV4[] models;

}

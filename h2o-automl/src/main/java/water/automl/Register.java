package water.automl;

import water.H2O;
import water.api.AbstractRegister;

public class Register extends AbstractRegister{
  @Override public void register(String relativeResourcePath) throws ClassNotFoundException {
    H2O.registerPOST("/3/AutoMLBuilder", AutoMLBuilderHandler.class, "automl", "automatically build models");
    H2O.registerGET("/3/AutoML/(?<automl_id>.*)", AutoMLHandler.class,"refresh", "refresh the model key");
    H2O.registerGET("/3/AutoMLJSONSchemaHandler", AutoMLJSONSchemaHandler.class,"getJSONSchema", "Get the json schema for the AutoML input fields.");
  }
}

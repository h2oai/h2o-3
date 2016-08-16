package water.automl;

import water.H2O;
import water.api.AbstractRegister;

public class Register extends AbstractRegister{
  @Override public void register(String relativeResourcePath) throws ClassNotFoundException {
    H2O.register("/3/AutoMLBuilder", AutoMLBuilderHandler.class, "POST", "automl", "automatically build models");
    H2O.register("/3/AutoML/{automl_id}", AutoMLHandler.class,"refresh", "GET", "refresh the model key");
    H2O.register("/3/AutoMLJSONSchemaHandler", AutoMLJSONSchemaHandler.class, "GET", "getJSONSchema", "Get the json schema for the AutoML input fields.");
  }
}

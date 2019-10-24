package hex;

import java.io.Serializable;
import java.util.Optional;

public class ModelBuildingResult implements Serializable {

  private Optional<Model> model;
  private Optional<Throwable> throwable;
  private Model.Parameters modelBuildingParameters;

  public ModelBuildingResult(Optional<Model> model, Optional<Throwable> throwable,
                             Model.Parameters modelBuildingParameters) {
    this.model = model;
    this.throwable = throwable;
    this.modelBuildingParameters = modelBuildingParameters;
  }

  public Optional<Model> getModel() {
    return model;
  }

  public boolean hasModel() {
    return model.isPresent();
  }
  
  public Optional<Throwable> getThrowable() {
    return throwable;
  }

  public Model.Parameters getModelBuildingParameters() {
    return modelBuildingParameters;
  }

}

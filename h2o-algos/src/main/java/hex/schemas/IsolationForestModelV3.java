package hex.schemas;

import hex.tree.isofor.IsolationForestModel;

public class IsolationForestModelV3 extends SharedTreeModelV3<IsolationForestModel,
        IsolationForestModelV3,
        IsolationForestModel.IsolationForestParameters,
        IsolationForestV3.IsolationForestParametersV3,
        IsolationForestModel.IsolationForestOutput,
        IsolationForestModelV3.IsolationForestModelOutputV3> {

  public static final class IsolationForestModelOutputV3 extends SharedTreeModelV3.SharedTreeModelOutputV3<IsolationForestModel.IsolationForestOutput, IsolationForestModelOutputV3> {}

  public IsolationForestV3.IsolationForestParametersV3 createParametersSchema() { return new IsolationForestV3.IsolationForestParametersV3(); }
  public IsolationForestModelOutputV3 createOutputSchema() { return new IsolationForestModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public IsolationForestModel createImpl() {
    IsolationForestV3.IsolationForestParametersV3 p = this.parameters;
    IsolationForestModel.IsolationForestParameters parms = p.createImpl();
    return new IsolationForestModel( model_id.key(), parms, new IsolationForestModel.IsolationForestOutput(null) );
  }
}

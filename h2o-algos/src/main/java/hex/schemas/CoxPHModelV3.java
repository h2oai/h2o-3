package hex.schemas;

import hex.coxph.CoxPHModel;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class CoxPHModelV3 extends ModelSchemaV3<CoxPHModel,
                                              CoxPHModelV3,
                                              CoxPHModel.CoxPHParameters,
                                              CoxPHV3.CoxPHParametersV3,
                                              CoxPHModel.CoxPHOutput,
                                              CoxPHModelV3.CoxPHModelOutputV3> {

  public static final class CoxPHModelOutputV3 extends ModelOutputSchemaV3<CoxPHModel.CoxPHOutput, CoxPHModelOutputV3> {
    //FIXME
    //add output fields

  } // CoxPHModelOutputV3

  public CoxPHV3.CoxPHParametersV3 createParametersSchema() { return new CoxPHV3.CoxPHParametersV3(); }
  public CoxPHModelOutputV3 createOutputSchema() { return new CoxPHModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public CoxPHModel createImpl() {
    CoxPHModel.CoxPHParameters parms = parameters.createImpl();
    return new CoxPHModel(model_id.key(), parms, new CoxPHModel.CoxPHOutput(null));
  }
}

package hex.schemas;

import hex.coxph.CoxPHModel;
import water.Key;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

public class CoxPHModelV2 extends ModelSchema<CoxPHModel, CoxPHModelV2, CoxPHModel.CoxPHParameters, CoxPHModel.CoxPHOutput> {

  public static final class CoxPHModelOutputV2 extends ModelOutputSchema<CoxPHModel.CoxPHOutput, CoxPHModelOutputV2> {
    //FIXME
    //add output fields

  } // CoxPHModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public CoxPHV2.CoxPHParametersV2 createParametersSchema() { return new CoxPHV2.CoxPHParametersV2(); }
  public CoxPHModelOutputV2 createOutputSchema() { return new CoxPHModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public CoxPHModel createImpl() {
    CoxPHV2.CoxPHParametersV2 p = ((CoxPHV2.CoxPHParametersV2)this.parameters);
    CoxPHModel.CoxPHParameters parms = p.createImpl();
    return new CoxPHModel(Key.make() /*dest*/, parms, new CoxPHModel.CoxPHOutput(null));
  }
}

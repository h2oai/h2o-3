package hex.schemas;

import hex.klime.KLimeModel;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class KLimeModelV3 extends ModelSchemaV3<KLimeModel, KLimeModelV3, KLimeModel.KLimeParameters, KLimeV3.KLimeParametersV3, KLimeModel.KLimeOutput, KLimeModelV3.KLimeModelOutputV3> {

  public static final class KLimeModelOutputV3 extends ModelOutputSchemaV3<KLimeModel.KLimeOutput, KLimeModelOutputV3> {
  } // KLimeModelOutputV3


  //==========================
  // Custom adapters go here
  public KLimeV3.KLimeParametersV3 createParametersSchema() { return new KLimeV3.KLimeParametersV3(); }
  public KLimeModelOutputV3 createOutputSchema() { return new KLimeModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public KLimeModel createImpl() {
    KLimeModel.KLimeParameters parms = parameters.createImpl();
    return new KLimeModel( model_id.key(), parms, null);
  }
}

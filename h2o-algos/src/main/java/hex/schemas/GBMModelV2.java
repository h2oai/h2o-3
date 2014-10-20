package hex.schemas;

import hex.gbm.GBMModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.util.PojoUtils;
//import water.util.DocGen.HTML;

public class GBMModelV2 extends ModelSchema<GBMModel, GBMModel.GBMParameters, GBMModel.GBMOutput, GBMModelV2> {

  public static final class GBMModelOutputV2 extends ModelOutputSchema<GBMModel.GBMOutput, GBMModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Error")
    public double mse;           // Total MSE, variance

    @Override public GBMModel.GBMOutput createImpl() {
      GBMModel.GBMOutput impl = new GBMModel.GBMOutput();
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    // Version&Schema-specific filling from the handler
    @Override public GBMModelOutputV2 fillFromImpl( GBMModel.GBMOutput impl) {
      PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }


  } // GBMModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public GBMV2.GBMParametersV2 createParametersSchema() { return new GBMV2.GBMParametersV2(); }
  public GBMModelOutputV2 createOutputSchema() { return new GBMModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public GBMModel createImpl() {
    GBMV2.GBMParametersV2 p = ((GBMV2.GBMParametersV2)this.parameters);
    GBMModel.GBMParameters parms = p.createImpl();
    return new GBMModel( key, p.training_frame, parms, new GBMModel.GBMOutput(), 0 );
  }

  // Version&Schema-specific filling from the impl
  @Override public GBMModelV2 fillFromImpl( GBMModel kmm ) {
    return super.fillFromImpl(kmm);
  }
}

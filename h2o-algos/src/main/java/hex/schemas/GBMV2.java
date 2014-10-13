package hex.schemas;

import hex.gbm.GBM;
import hex.gbm.GBMModel.GBMParameters;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;
import water.util.PojoUtils;

public class GBMV2 extends ModelBuilderSchema<GBM,GBMV2,GBMV2.GBMParametersV2> {

  public static final class GBMParametersV2 extends ModelParametersSchema<GBMParameters, GBMParametersV2> {
    public String[] fields() { return new String[] {
            "destination_key",
            "training_frame",
            "loss" }; }

    // Input fields
    @API(help = "Loss function", values = { "AUTO", "Bernoulli" })
    public GBM.Family loss;

    @Override public GBMParametersV2 fillFromImpl(GBMParameters parms) {
      super.fillFromImpl(parms);
      loss = GBM.Family.AUTO;
      return this;
    }

    public GBMParameters createImpl() {
      GBMParameters impl = new GBMParameters();
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      impl.loss = GBM.Family.AUTO;
      return impl;
    }
  }

  //==========================
  // Custom adapters go here

  @Override public GBMParametersV2 createParametersSchema() { return new GBMParametersV2(); }

  // TODO: refactor ModelBuilder creation
  // TODO: defaults should only be in the impl, not duplicated in the API layer
  @Override public GBM createImpl() {
    if( parameters.seed == 0 ) parameters.seed = System.nanoTime();

    GBMParameters parms = parameters.createImpl();
    return new GBM(parms);
  }

  // TODO: UGH
  // Return a URL to invoke GBM on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/GBM?training_frame="+fr._key; }
}

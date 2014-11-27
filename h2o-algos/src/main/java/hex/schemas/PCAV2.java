package hex.schemas;

import hex.pca.PCA;
import hex.pca.PCAModel.PCAParameters;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;
import water.util.PojoUtils;

public class PCAV2 extends ModelBuilderSchema<PCA,PCAV2,PCAV2.PCAParametersV2> {

  public static final class PCAParametersV2 extends ModelParametersSchema<PCAParameters, PCAParametersV2> {
    static public String[] own_fields = new String[] { "max_pc", "tolerance", "standardize"};

    // Input fields
    @API(help = "maximum number of principal components")
    public int max_pc;

    @API(help = "tolerance")
    public double tolerance;

    @API(help = "standardize")
    public boolean standardize;

    @Override public PCAParametersV2 fillFromImpl(PCAParameters parms) {
      super.fillFromImpl(parms);
      return this;
    }

    public PCAParameters fillImpl(PCAParameters impl) {
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);

      // Sigh:
      impl._train = (this.training_frame == null ? null : this.training_frame._key);
      impl._valid = (this.validation_frame == null ? null : this.validation_frame._key);
      return impl;
    }
  }

  //==========================
  // Custom adapters go here

  // TODO: UGH
  // Return a URL to invoke PCA on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/PCA?training_frame="+fr._key; }
}

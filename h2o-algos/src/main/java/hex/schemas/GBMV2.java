package hex.schemas;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.api.API;
import water.api.SupervisedModelParametersSchema;
import water.fvec.Frame;

public class GBMV2 extends SupervisedModelBuilderSchema<GBM,GBMV2,GBMV2.GBMParametersV2> {

  public static final class GBMParametersV2 extends SupervisedModelParametersSchema<GBMParameters, GBMParametersV2> {
    static public String[] own_fields = new String[] {
        "ntrees",
        "max_depth",
        "min_rows",
        "nbins",
        "learn_rate",
        "loss",
        "variable_importance",
        "seed"
    };

    // Input fields
    @API(help="Number of trees.  Grid Search, comma sep values:50,100,150,200")
    public int ntrees;

    @API(help="Maximum tree depth.  Grid Search, comma sep values:5,7")
    public int max_depth;

    @API(help="Fewest allowed observations in a leaf (in R called 'nodesize'). Grid Search, comma sep values")
    public int min_rows;

    @API(help="Build a histogram of this many bins, then split at the best point")
    public int nbins;

    @API(help="Learning rate from 0.0 to 1.0")
    public float learn_rate;

    @API(help = "Loss function", values = { "AUTO", "bernoulli" })
    public GBMParameters.Family loss;

    @API(help = "RNG Seed for balancing classes", level = API.Level.expert)
    public long seed;

    @API(help = "Compute variable importance", level = API.Level.critical)
    public boolean variable_importance;

    @Override public GBMParametersV2 fillFromImpl(GBMParameters parms) {
      super.fillFromImpl(parms);

      loss = GBMParameters.Family.AUTO; // TODO: Why? Defaults should NOT go into the schemas!
      variable_importance = parms._variable_importance;
      return this;
    }

    public GBMParameters fillImpl(GBMParameters impl) {
      super.fillImpl(impl);

      impl._variable_importance = this.variable_importance;
      return impl;
    }
  }

  //==========================
  // Custom adapters go here

  // TODO: UGH
  // Return a URL to invoke GBM on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/GBM?training_frame="+fr._key; }
}

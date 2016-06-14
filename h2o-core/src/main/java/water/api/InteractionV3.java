package water.api;

import hex.Interaction;

class InteractionV3 extends SchemaV3<Interaction, InteractionV3> {
  static public String[] own_fields = new String[] { "source_frame", "factor_columns", "pairwise", "max_factors", "min_occurrence" };

  @API(help="destination key", direction=API.Direction.INOUT)
  public KeyV3.FrameKeyV3 dest;

  @API(help = "Input data frame", direction = API.Direction.INOUT)
  public KeyV3.FrameKeyV3 source_frame;

  @API(help = "Factor columns", is_member_of_frames = {"source_frame"}, direction = API.Direction.INOUT)
  public String[] factor_columns;

  @API(help = "Whether to create pairwise quadratic interactions between factors (otherwise create one higher-order interaction). Only applicable if there are 3 or more factors.", required = false, direction = API.Direction.INOUT)
  public boolean pairwise;

  @API(help = "Max. number of factor levels in pair-wise interaction terms (if enforced, one extra catch-all factor will be made)", required = true, direction = API.Direction.INOUT)
  public int max_factors;

  @API(help = "Min. occurrence threshold for factor levels in pair-wise interaction terms", direction = API.Direction.INOUT)
  public int min_occurrence;

  @Override public Interaction createImpl( ) { return new Interaction(); }
}


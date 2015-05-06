package water.api;

import hex.Interaction;

class InteractionV3 extends JobV3<Interaction, InteractionV3> {
  @API(help = "Input data frame", required = true, json=true)
  public KeyV3.FrameKeyV3 source_frame;

  @API(help = "Column indices (0-based) of factors for which to compute interactions", is_member_of_frames = {"source_frame"}, direction = API.Direction.INOUT)
  public FrameV3.ColSpecifierV2 response_column;
  public int[] factors;

  @API(help = "Whether to create pairwise quadratic interactions between factors (otherwise create one higher-order interaction). Only applicable if there are 3 or more factors.", required = false, json=true)
  public boolean pairwise;

  @API(help = "Max. number of factor levels in pair-wise interaction terms (if enforced, one extra catch-all factor will be made)", required = true, json=true)
  public int max_factors;

  @API(help = "Min. occurrence threshold for factor levels in pair-wise interaction terms", required = true, json=true)
  public int min_occurrence;

  @Override public Interaction createImpl( ) { return new Interaction(); }
}


package water.api.schemas3;

import hex.PDP;
import water.Key;
import water.api.API;

/**
 * Partial dependence plot
 */
public class PDPV3 extends SchemaV3<PDP, PDPV3> {
  @API(help="Name of Model of interest", direction = API.Direction.INOUT)
  public KeyV3.ModelKeyV3 model_id;

  @API(help="Frame", direction=API.Direction.INOUT)
  public KeyV3.FrameKeyV3 frame_id;

  @API(help="Column(s)", direction=API.Direction.INOUT)
  public String[] cols;

  @API(help="Number of bins", direction=API.Direction.INOUT)
  public int nbins;

  @SuppressWarnings("unused")
  @API(help="Partial Dependence Data", direction=API.Direction.OUTPUT)
  public TwoDimTableV3[] partial_dependence_data;

  @API(help="Key to store the destination", direction=API.Direction.INOUT)
  public KeyV3.PDPKeyV3 destination_key;

  @Override public PDP createImpl( ) { return new PDP(Key.<PDP>make()); }
}

package water.api.schemas3;

import hex.PartialDependence;
import water.Key;
import water.api.API;

/**
 * Partial dependence plot
 */
public class PartialDependenceV3 extends SchemaV3<PartialDependence, PartialDependenceV3> {
  @SuppressWarnings("unused")
  @API(help="Model", direction = API.Direction.INOUT)
  public KeyV3.ModelKeyV3 model_id;

  @SuppressWarnings("unused")
  @API(help="Frame", direction=API.Direction.INOUT)
  public KeyV3.FrameKeyV3 frame_id;

  @SuppressWarnings("unused")
  @API(help="Column(s)", direction=API.Direction.INOUT)
  public String[] cols;

  @SuppressWarnings("unused")
  @API(help="weight_column_index", direction=API.Direction.INOUT)
  public int weight_column_index; // choose which column containing the weight

  @SuppressWarnings("unused")
  @API(help="add_missing_na", direction=API.Direction.INOUT)
  public boolean add_missing_na; // add missing values if data column contains NAs

  @SuppressWarnings("unused")
  @API(help="Number of bins", direction=API.Direction.INOUT)
  public int nbins;

  @SuppressWarnings("unused")
  @API(help="Partial Dependence Data", direction=API.Direction.OUTPUT)
  public TwoDimTableV3[] partial_dependence_data;

  @API(help="Key to store the destination", direction=API.Direction.INPUT)
  public KeyV3.PartialDependenceKeyV3 destination_key;

  @Override public PartialDependence createImpl( ) { return new PartialDependence(Key.<PartialDependence>make()); }
}

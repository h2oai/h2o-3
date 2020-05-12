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
  @API(help="Row Index", direction=API.Direction.INOUT)
  public long row_index;
  
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
  @API(help="User define split points", direction=API.Direction.INOUT)
  public double[] user_splits; // all user split columns by value

  @SuppressWarnings("unused")
  @API(help="Column(s) of user defined splits", direction=API.Direction.INOUT)
  public String[] user_cols; // list column indices to use user defined split values

  @SuppressWarnings("unused")
  @API(help="Number of user defined splits per column", direction=API.Direction.INOUT)
  public int[] num_user_splits; // list of number of user defined split values per column

  @SuppressWarnings("unused")
  @API(help="Partial Dependence Data", direction=API.Direction.OUTPUT)
  public TwoDimTableV3[] partial_dependence_data;
  
  @API(help="lists of column name pairs to plot 2D pdp for", direction=API.Direction.INOUT)
  public String[][] col_pairs_2dpdp;

  @API(help="Key to store the destination", direction=API.Direction.INPUT)
  public KeyV3.PartialDependenceKeyV3 destination_key;

  @API(help="Target class for multinomial classification", direction=API.Direction.INPUT)
  public String target;

  @Override public PartialDependence createImpl( ) { return new PartialDependence(Key.<PartialDependence>make()); }
}

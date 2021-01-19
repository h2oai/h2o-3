package water.api.schemas3;

import water.Iced;
import water.api.API;

public class FeatureInteractionV3 extends RequestSchemaV3<Iced, FeatureInteractionV3> {
  
  @API(help="Model id of interest", json = false)
  public KeyV3.ModelKeyV3 model_id;

  @API(help = "Maximum interaction depth", required = true)
  public int max_interaction_depth;

  @API(help = "Maximum tree depth", required = true)
  public int max_tree_depth;

  @API(help = "Maximum deepening", required = true)
  public int max_deepening;

  @API(help="Feature importance table", direction = API.Direction.OUTPUT)
  public TwoDimTableV3[] feature_interaction;
}

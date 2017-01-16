package hex.schemas;

import water.Iced;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.FrameV3;
import water.api.schemas3.KeyV3;

/**
 * Created by tomas on 10/26/16.
 */
public class GramV3 extends Schema<Iced,GramV3>{
  @API(help="source data", required = true, direction = API.Direction.INPUT)
  public KeyV3.FrameKeyV3 X;
  @API(help="weight vector", required = false, direction = API.Direction.INPUT)
  public FrameV3.ColSpecifierV3 W;

  @API(help="use all factor levels when doing 1-hot encoding", required=false,direction=API.Direction.INPUT)
  public boolean use_all_factor_levels;

  @API(help="standardize data",required=false,direction = API.Direction.INPUT)
  public boolean standardize;

  @API(help="skip missing values",required=false,direction = API.Direction.INPUT)
  public boolean skip_missing;

  @API(help="Destination key for the resulting matrix.", direction = API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 destination_frame;
}

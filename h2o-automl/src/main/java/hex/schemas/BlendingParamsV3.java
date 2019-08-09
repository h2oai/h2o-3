package hex.schemas;

import ai.h2o.automl.targetencoding.BlendingParams;
import water.api.API;
import water.api.schemas3.SchemaV3;

public class BlendingParamsV3 extends SchemaV3<BlendingParams, BlendingParamsV3> {
  
  @API(help="K blending parameter", direction=API.Direction.OUTPUT)
  public double k;
  @API(help="", direction=API.Direction.OUTPUT)
  public double f;
}

package hex.schemas;

import water.Iced;
import water.Key;
import water.api.API;
import water.api.API.Direction;
import water.api.KeyV3;
import water.api.SchemaV3;

/**
 * End point to update a model. Creates a modified copy of the original model. Can only change coefficient values.
 */
public class MakeGLMModelV3 extends SchemaV3<Iced,MakeGLMModelV3> {

  @API(help="source model", required = true, direction = Direction.INPUT)
  public KeyV3.ModelKeyV3 model;

  @API(help="destination key", required = false, direction = Direction.INPUT)
  public KeyV3.ModelKeyV3 dest;//new KeyV3.ModelKeyV3(Key.make());

  @API(help="coefficient names", required = true, direction = Direction.INPUT)
  public String [] names;

  @API(help = "new glm coefficients", required = true, direction = Direction.INPUT)
  public double [] beta;

  @API(help="decision threshold for label-generation", required = false, direction = Direction.INPUT)
  public float threshold = .5f;

}

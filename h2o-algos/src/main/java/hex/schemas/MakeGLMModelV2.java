package hex.schemas;

import water.Iced;
import water.Key;
import water.api.API;
import water.api.API.Direction;
import water.api.KeyV1;
import water.api.Schema;

/**
 * Created by tomasnykodym on 8/29/14.
 *
 * End point to update a model. Creates a modified copy of the original model. Can only change coefficient values.
 */
public class MakeGLMModelV2 extends Schema<Iced,MakeGLMModelV2> {

  @API(help="source model", required = true, direction = Direction.INPUT)
  public KeyV1.ModelKeyV1 model;

  @API(help="destination key", required = false, direction = Direction.INPUT)
  public KeyV1.ModelKeyV1 dest = new KeyV1.ModelKeyV1(Key.make());

  @API(help="coefficient names", required = true, direction = Direction.INPUT)
  public String [] names;

  @API(help = "new glm coefficients", required = true, direction = Direction.INPUT)
  public double [] beta;

  @API(help="decision threshold for label-generation", required = false, direction = Direction.INPUT)
  public float threshold = .5f;

}

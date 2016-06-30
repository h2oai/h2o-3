package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsNumberV3 extends RapidsSchemaV3<Iced,RapidsNumberV3> {

  @API(help="Number result", direction=API.Direction.OUTPUT)
  public double scalar;

  public RapidsNumberV3() {}

  public RapidsNumberV3(double d) { scalar = d; }
}

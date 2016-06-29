package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsNumbersV3 extends RapidsSchemaV3<Iced,RapidsNumbersV3> {

  @API(help="Number array result", direction=API.Direction.OUTPUT)
  public double[] scalar;

  public RapidsNumbersV3() {}

  public RapidsNumbersV3(double[] ds) {
    scalar = ds;
  }
}

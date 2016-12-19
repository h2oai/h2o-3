package water.api.schemas4.output;

import water.Iced;
import water.api.API;

/**
 * Output schema for the actual result of the evaluation.
 */
public class CascadeNumOV4 extends CascadeOV4<Iced, CascadeNumOV4> {

  @API(help="The value of the numeric result")
  public double value;


  public CascadeNumOV4() {}  // for dynamic schema registration

  public CascadeNumOV4(double d) {
    value = d;
  }
}

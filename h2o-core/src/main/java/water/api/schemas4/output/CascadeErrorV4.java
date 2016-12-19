package water.api.schemas4.output;

import water.Iced;
import water.api.API;

/**
 * Schema to be returned to the user if evaluation of a Cascade expression
 * resulted in an exception.
 */
public class CascadeErrorV4 extends CascadeOV4<Iced, CascadeErrorV4> {

  @API(help="Cascade expression which caused an error")
  public String expr;

  @API(help="Position of the error (character offset within the `expr`).")
  public int errorPos;

  @API(help="Length of the token that caused the error; may be zero if not applicable.")
  public int errorLen;

  @API(help="Error message.")
  public String message;

}

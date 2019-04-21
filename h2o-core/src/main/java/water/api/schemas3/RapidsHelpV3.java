package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 * Help for the rapids language
 */
public class RapidsHelpV3 extends SchemaV3<Iced, RapidsHelpV3> {

  @API(help="Description of the rapids language.",
      direction=API.Direction.OUTPUT)
  public RapidsExpressionV3[] expressions;

  public static class RapidsExpressionV3 extends SchemaV3<Iced, RapidsExpressionV3> {
    @API(help="(Class) name of the language construct")
    public String name;

    @API(help="Code fragment pattern.")
    public String pattern;

    @API(help="Description of the functionality provided by this language construct.")
    public String description;
  }
}

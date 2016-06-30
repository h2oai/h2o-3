package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsHelpV3 extends SchemaV3<Iced, RapidsHelpV3> {

  @API(help="Rapids language, organized in a form of a tree (so that similar constructs are grouped together.",
      direction=API.Direction.OUTPUT)
  public RapidsExpressionV3 syntax;

  public static class RapidsExpressionV3 extends SchemaV3<Iced, RapidsExpressionV3> {
    @API(help="(Class) name of the language construct")
    public String name;

    @API(help="If true, then this is not a standalone construct but purely a grouping level.")
    public boolean is_abstract;

    @API(help="Code fragment pattern.")
    public String pattern;

    @API(help="Description of the functionality provided by this language construct.")
    public String description;

    @API(help="List of language constructs that grouped under this one.")
    public RapidsExpressionV3[] sub;
  }
}

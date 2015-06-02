package water.api;

import water.Iced;
import water.util.DocGen;

public class RemoveV3 extends RequestSchema<Iced, RemoveV3> {
  //Input
  @API(help="Object to be removed.")
  KeyV3 key;
  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.p("Object "+key+" has been removed.");
    return ab;
  }
}

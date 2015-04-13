package water.api;

import water.Iced;
import water.util.DocGen;

public class RemoveV3 extends Schema<Iced, RemoveV3> {
  //Input
  @API(help="Key to be removed.")
  KeyV3 key;
  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.p("Key "+key+" has been removed.");
    return ab;
  }
}
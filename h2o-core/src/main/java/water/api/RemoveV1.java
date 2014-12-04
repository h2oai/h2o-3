package water.api;

import water.Iced;
import water.Key;
import water.util.DocGen;

public class RemoveV1 extends Schema<Iced, RemoveV1> {
  //Input
  @API(help="Key to be removed.")
  Key key;
  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.p("Key "+key+" has been removed.");
    return ab;
  }
}
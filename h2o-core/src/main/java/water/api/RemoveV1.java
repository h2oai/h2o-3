package water.api;

import water.Key;
import water.util.DocGen;
import water.api.RemoveHandler.Remove;

public class RemoveV1 extends Schema<Remove,RemoveV1> {
  //Input
  @API(help="Key to be removed.")
  Key key;
  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.p("Key "+key+" has been removed.");
    return ab;
  }
}
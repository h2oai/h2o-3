package water.api;

import water.Iced;
import water.util.DocGen;

public class RemoveV3 extends RequestSchema<Iced, RemoveV3> {
  //Input
  @API(help="Object to be removed.")
  KeyV3 key;
}

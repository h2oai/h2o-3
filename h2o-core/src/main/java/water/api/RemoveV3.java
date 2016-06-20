package water.api;

import water.Iced;

public class RemoveV3 extends SchemaV3<Iced, RemoveV3> {
  //Input
  @API(help="Object to be removed.")
  KeyV3 key;
}

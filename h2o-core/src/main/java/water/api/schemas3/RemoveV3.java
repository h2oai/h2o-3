package water.api.schemas3;

import water.Iced;
import water.api.API;

public class RemoveV3 extends SchemaV3<Iced, RemoveV3> {

  @API(help="Object to be removed.")
  public KeyV3 key;

}

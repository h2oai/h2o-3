package water.api.schemas3;

import water.Iced;
import water.api.API;

public class RemoveAllV3 extends RequestSchemaV3<Iced, RemoveAllV3> {
  @API(direction = API.Direction.INPUT, help = "Keys of the models to retain", level = API.Level.critical)
  public KeyV3[] retained_keys;
}

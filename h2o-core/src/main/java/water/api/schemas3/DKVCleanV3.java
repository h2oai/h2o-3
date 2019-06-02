package water.api.schemas3;

import water.Iced;
import water.api.API;

public class DKVCleanV3 extends SchemaV3<Iced, DKVCleanV3> {
  @API(required = true, direction = API.Direction.INPUT, help = "Keys of the models to retain", level = API.Level.critical)
  public KeyV3[] retained_keys;
}

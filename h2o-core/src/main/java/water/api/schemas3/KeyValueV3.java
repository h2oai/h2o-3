package water.api.schemas3;

import hex.KeyValue;
import water.api.API;
import water.api.Schema;

public class KeyValueV3 extends SchemaV3<KeyValue, KeyValueV3> implements Schema.AutoParseable {
  @API(help = "Key")
  String key;
  @API(help = "Value")
  double value;
}

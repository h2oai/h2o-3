package water.api.schemas3;

import hex.StringPair;
import water.api.API;
import water.api.Schema;

public class StringPairV3 extends SchemaV3<StringPair, StringPairV3> implements Schema.AutoParseable {
  @API(help = "Value A")
  String a;
  @API(help = "Value B")
  String b;
}

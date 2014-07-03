package water.api;

import water.H2O;
import water.util.IcedHashMap;

/**
 * Schema for the response blob used in /2/*.
 */
class ResponseInfoV2 extends Schema<IcedHashMap, ResponseInfoV2> {
  @API(help="h2o")
  String h2o;

  @API(help="node")
  String node;

  @API(help="status")
  String status;

  @API(help="time")
  int time;

  @Override public IcedHashMap createImpl() { throw H2O.fail("fillInto should never be called on ResponseInfoV2"); }
  @Override public ResponseInfoV2 fillFromImpl(IcedHashMap i) { throw H2O.fail("fillFromImpl should never be called on ResponseInfoV2"); }
}


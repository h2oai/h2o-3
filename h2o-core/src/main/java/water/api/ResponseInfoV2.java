package water.api;

import water.H2O;

/**
 * Schema for the response blob used in /2/*.
 */
class ResponseInfoV2 extends Schema {
  @API(help="h2o")
  String h2o;

  @API(help="node")
  String node;

  @API(help="status")
  String status;

  @API(help="time")
  int time;

  @Override protected ResponseInfoV2 fillInto( Handler h ) { throw H2O.fail("fillInto should never be called on ResponseInfoV2"); }
  @Override protected ResponseInfoV2 fillFrom( Handler h ) { throw H2O.fail("fillFrom should never be called on ResponseInfoV2"); }
}


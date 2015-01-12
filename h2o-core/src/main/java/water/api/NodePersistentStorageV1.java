package water.api;

import water.Iced;

/**
* Created by rpeck on 11/30/14.
*/
public class NodePersistentStorageV1 extends Schema<Iced, NodePersistentStorageV1> {
  public static class NodePersistentStorageEntryV1 extends Schema<Iced, NodePersistentStorageEntryV1> {
    @API(help="Category name", required=true, direction=API.Direction.OUTPUT)
    String category;

    @API(help="Key name", required=true, direction=API.Direction.OUTPUT)
    String name;

    @API(help="Size in bytes of value", required=true, direction=API.Direction.OUTPUT)
    long size;

    @API(help="Epoch time in milliseconds of when the value was written", required=true, direction=API.Direction.OUTPUT)
    long timestamp_millis;
  }

  @API(help="Category name", required=false, direction=API.Direction.INOUT)
  String category;

  @API(help="Key name", required=false, direction=API.Direction.INOUT)
  String name;

  @API(help="Value", required=false, direction=API.Direction.INOUT)
  String value;

  @API(help="List of entries", required=false, direction=API.Direction.OUTPUT)
  NodePersistentStorageEntryV1[] entries;
}

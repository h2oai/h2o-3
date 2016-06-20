package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
*/
public class NodePersistentStorageV3 extends SchemaV3<Iced, NodePersistentStorageV3> {

  public static class NodePersistentStorageEntryV3 extends SchemaV3<Iced, NodePersistentStorageEntryV3> {
    @API(help = "Category name", required = true, direction = API.Direction.OUTPUT)
    public String category;

    @API(help = "Key name", required = true, direction = API.Direction.OUTPUT)
    public String name;

    @API(help = "Size in bytes of value", required = true, direction = API.Direction.OUTPUT)
    public long size;

    @API(help = "Epoch time in milliseconds of when the value was written", required = true, direction = API.Direction.OUTPUT)
    public long timestamp_millis;
  }

  @API(help="Category name", required=false, direction=API.Direction.INOUT)
  public String category;

  @API(help="Key name", required=false, direction=API.Direction.INOUT)
  public String name;

  @API(help="Value", required=false, direction=API.Direction.INOUT)
  public String value;

  @API(help="Configured", required=false, direction=API.Direction.OUTPUT)
  public boolean configured;

  @API(help = "Exists", required = false, direction = API.Direction.OUTPUT)
  public boolean exists;

  @API(help = "List of entries", required = false, direction = API.Direction.OUTPUT)
  public NodePersistentStorageEntryV3[] entries;
}
